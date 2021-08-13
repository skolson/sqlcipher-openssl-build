package com.oldguy.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.net.URL
import javax.inject.Inject

/**
 * Isolates the gradle input and output properties for SqlCipher tasks
 */
abstract class SqlCipherExtractTask: @Inject ExtractArchiveTask() {
    @get:InputFile
    abstract override val archiveFile: RegularFileProperty
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class SqlCipherGitTask: @Inject GitCheckoutTask() {
    @get:Input
    abstract override val uri: Property<String>
    @get:Input
    abstract override val tagName: Property<String>
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class SqlCipherDownloadTask: @Inject DownloadArchiveTask() {
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
    private val builds = ext.builds

    override val useGit: Boolean get() = ext.useGit
    override val gitUri:String get() = ext.githubUri
    override val gitTagName:String get() = ext.tagName
    private val downloadFileName: String get() = downloadSuffix(ext.tagName)
    override val downloadUrl: URL get() = URL("${ext.githubUri}/archive/$downloadFileName")
    private val sourceDir get() = ext.compileDirectory(target).resolve("$buildName-${ext.version}")
    override lateinit var extractTask: TaskProvider<out ExtractArchiveTask>
    override lateinit var gitTask: TaskProvider<out GitCheckoutTask>
    override lateinit var downloadTask: TaskProvider<out DownloadArchiveTask>

    init {
        if (!sourceDir.exists())
            sourceDir.mkdir()
        registerSourceTasks(
                { target.buildDir.resolve(ext.srcDirectory).resolve(downloadFileName) },
                {sourceDir})
        ext.buildTypes.supportedBuilds.forEach {
            registerTasks(it)
        }
    }

    override fun registerExtractTask() {
        extractTask = target.tasks.register("${buildName}Extract", SqlCipherExtractTask::class.java)
    }

    override fun registerGitTask() {
        gitTask = target.tasks.register("${buildName}Git", SqlCipherGitTask::class.java)
    }

    override fun registerDownloadTask() {
        downloadTask = target.tasks.register("${buildName}Download", SqlCipherDownloadTask::class.java)
    }


    private fun registerTasks(buildType: String) {
        val opensslTaskName = "${OpenSslBuild.constBuildName}Build$buildType"

        target.tasks.register("${buildName}Build$buildType", SqlcipherBuildTask::class.java, buildType)
            .configure {
                ext.validateOptions()
                val opensslTask = target.tasks.getByName(opensslTaskName) as OpensslBuildTask
                it.dependsOn(gitTask, extractTask, opensslTask)
                it.onlyIf {
                    builds.contains(buildType) && ext.buildSqlCipher
                }
                it.setToolsProperties(ext.toolsExt)
                it.setup(
                        sourceDir,
                        opensslTask.includeDirectory.get().asFile,
                        opensslTask.targetDirectory.get().asFile,
                        createTargetsDirectory(ext.targetsDirectory, buildName).resolve(buildType),
                        windows.sdkInstall,
                        windows.sdkLibVersion,
                        ext.compilerOptions
                    )
            }
    }
}
