package com.oldguy.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.net.URL

/**
 * Isolates the gradle input and output properties for SqlCipher tasks
 */
abstract class SqlCipherExtractTask: ExtractArchiveTask() {
    @get:InputFile
    abstract override val archiveFile: RegularFileProperty
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class SqlCipherGitTask: GitCheckoutTask() {
    @get:Input
    abstract override val uri: Property<String>
    @get:Input
    abstract override val tagName: Property<String>
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class SqlCipherDownloadTask: DownloadArchiveTask() {
    @get:Input
    abstract override val url: Property<URL>
    @get:OutputFile
    abstract override val downloadFile: RegularFileProperty
}

/**
 * Registers the tasks for building SqlCipher, using OpenSSL from the opensslBuild task. Provides the configuration
 * for each task, setting task properties from the various extension configuration values.
 */
class SqlCipherBuild(target: Project,
                     private val ext: SqlcipherExtension)
    : Builder(target, ext.toolsExt)
{
    override val buildName = "sqlcipher"
    private val builds get() = ext.builds

    private val downloadFileName: String get() = downloadSuffix(ext.tagName)

    override val useGit: Boolean get() = ext.useGit
    override val gitUri:String get() = ext.githubUri
    override val gitTagName:String get() = ext.tagName
    override val downloadUrl: URL get() = URL("${ext.githubUri}/archive/$downloadFileName")
    override val srcDir get() = target.buildDir.resolve(ext.srcDirectory)
    override lateinit var gitTask: TaskProvider<out GitCheckoutTask>
    override lateinit var downloadTask: TaskProvider<out DownloadArchiveTask>
    private val sqlcipherDir get() = "sqlcipher-${ext.version}"

    init {
        downloadTask = target.tasks.register("${buildName}Download", SqlCipherDownloadTask::class.java) { task ->
            task.onlyIf {
                !useGit
            }
            task.setup(downloadUrl, srcDir.resolve(downloadFileName))
        }

        gitTask = target.tasks.register("${buildName}Git", SqlCipherGitTask::class.java) { task ->
            task.onlyIf {
                useGit
            }
            task.setup(gitUri, gitTagName, gitDir)
        }

        ext.builds.forEach { buildType ->
            val fullCopyTaskName = taskName(copyTaskName, buildType)
            target.tasks.register(fullCopyTaskName, Copy::class.java) { task ->
                task.dependsOn(gitTask)
                task.onlyIf {
                    useGit && builds.contains(buildType)
                }
                task.from(gitDir)
                task.into(compileDirectory(buildType, sqlcipherDir))
            }
            registerTasks(buildType, fullCopyTaskName)
        }
    }

    private fun registerTasks(buildType: BuildType, fullCopyTaskName: String) {
        val opensslTaskName = "${OpenSslBuild.constBuildName}$buildTaskName$buildType"

        val extTaskName = taskName(extractTaskName, buildType)
        target.tasks.register(extTaskName, SqlCipherExtractTask::class.java) { task ->
            task.dependsOn(downloadTask)
            task.onlyIf {
                !useGit
            }
            task.setup(downloadTask.get().downloadFile.get().asFile, compileDirectory(buildType, sqlcipherDir))
        }

        target.tasks.register("${buildName}Build$buildType", SqlcipherBuildTask::class.java, buildType)
            .configure {
                ext.validateOptions()
                val opensslTask = target.tasks.getByName(opensslTaskName) as OpensslBuildTask
                val prereqName = if (useGit)
                    fullCopyTaskName
                else
                    extTaskName
                it.dependsOn(target.tasks.getByName(prereqName), opensslTask)
                it.onlyIf {
                    builds.contains(buildType) && ext.buildSqlCipher
                }
                it.setToolsProperties(ext.toolsExt)
                it.setup(
                    srcDir.resolve(buildType.name).resolve(sqlcipherDir),
                    opensslTask.includeDirectory.get().asFile,
                    opensslTask.targetDirectory.get().asFile,
                    createTargetsDirectory(ext.targetsDirectory, buildName).resolve(buildType.name),
                    windows.sdkInstall,
                    windows.sdkLibVersion,
                    ext.compilerOptions,
                    ext.buildCompilerOptions
                )
            }
    }

    companion object {
        /**
         * Map build types to the matching build option used by sqlcipher configure
         */
        private val buildsMap = mapOf(
            BuildType.androidArm64 to "aarch64-linux",
            BuildType.androidX64 to "x86_64-linux"
        )
        /**
         * This builds the amalgamation sqlite3.c and sqlite3.h from the SqlCipher source, if sqlite3.c already exists in
         * the build directory, build is skipped
         */
        fun buildAmalgamation(
            srcDir: File,
            runner: Runner,
            buildType: BuildType
        ) {
            val amalgamation = SqlcipherExtension.amalgamation
            if (srcDir.resolve(amalgamation).exists()) return
            var buildOpt = buildsMap[buildType] ?: ""
            if (buildOpt.isNotEmpty()) buildOpt = "--build=$buildOpt"

            val shFilename = BuilderTask.createPluginFile(srcDir, "sqlite-amalgamation.sh") {
                """
            #!/bin/sh
            ./configure $buildOpt --enable-tempstore=yes --disable-tcl --with-crypto-lib=none
            make $amalgamation
            """.trimIndent()
            }

            runner.logger.info("create amalgamation source: $amalgamation")
            runner.command(srcDir, "./$shFilename") { }
            runner.logger.info("Amalgamation source created: $amalgamation")
        }
    }
}
