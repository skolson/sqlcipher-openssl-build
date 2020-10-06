package com.oldguy.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.net.URL

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

    init {
        val taskPair = registerSourceTasks(
                { target.buildDir.resolve(ext.srcDirectory).resolve(downloadFileName) },
                {
                    if (!sourceDir.exists())
                        sourceDir.mkdir()
                    sourceDir
                })
        ext.buildTypes.supportedBuilds.forEach {
            registerTasks(it, taskPair.first, taskPair.second)
        }
    }

    private fun registerTasks(buildType: String,
                              gitTask:TaskProvider<GitCheckoutTask>,
                              extractTask:TaskProvider<ExtractArchiveTask>) {
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
