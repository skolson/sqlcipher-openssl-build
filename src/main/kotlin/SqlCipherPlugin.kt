package com.oldguy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * This is a singleton convenience wrapper on the Project logger.  It also provides helpers for logging stdout and stderr
 * from command processes run in the build tasks.
 */
object Logger {
    private lateinit var target: Project
    val logger: org.gradle.api.logging.Logger get() = target.logger

    fun initialize(target: Project) {
        this.target = target
    }

    fun errorLines(stderr: ByteArrayOutputStream, warning: Boolean = false) {
        ByteArrayInputStream( stderr.toByteArray()).bufferedReader().useLines { lines ->
            lines.forEach {
                if (warning)
                    logger.warn(it)
                else
                    logger.error(it)
            }
        }
    }

    /**
     * Log all the lines to info.  Returns the first non-blank line for parsing purposes if desired.
     */
    fun textLines(stdout: ByteArrayOutputStream): String {
        var firstLine = ""
        ByteArrayInputStream( stdout.toByteArray()).bufferedReader().useLines { lines ->
            lines.forEach {
                if (it.trim().isNotEmpty() && firstLine.isEmpty())
                    firstLine = it.trim()
                logger.info(it)
            }
        }
        return firstLine
    }
}

class SqlCipherPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        Logger.initialize(target)
        val ext = SqlcipherExtension.createExtensions(target)
        val opensslTask = OpenSslBuild(target, ext)
        val sqlcipherTask = SqlCipherBuild(target, ext)
        registerAll(target, opensslTask.buildName, ext.builds, false)
        registerAll(target, sqlcipherTask.buildName, ext.builds, true)

        target.tasks.register("${opensslTask.buildName}Clean", CleanAllTask::class.java)
            .configure{
                it.sourceDirectory.set(target.buildDir.resolve(ext.opensslExt.srcDirectory))
                it.builds.clear()
                it.builds.addAll(ext.builds)
                it.sourceArchive.set(ext.opensslExt.downloadSourceFileName)
                it.targetsDirectory.set(File(ext.opensslExt.targetsDirectory))
            }
        target.tasks.register("${sqlcipherTask.buildName}Clean", CleanAllTask::class.java)
            .configure{
                it.sourceDirectory.set(target.buildDir.resolve(ext.srcDirectory))
                it.builds.clear()
                it.builds.addAll(ext.builds)
                it.sourceArchive.set(ext.tagName)
                it.targetsDirectory.set(File(ext.targetsDirectory))
            }
    }

    private fun registerAll(target: Project, buildName: String, builds: List<BuildType>, updateMap: Boolean) {
        val buildAllTask = target.tasks.register("${buildName}BuildAll", BuildAllTask::class.java)
        buildAllTask.configure { task ->
            task.builds.set(builds)
            task.targetDirectoriesMap
            val buildTasks = mutableListOf<Task>()
            builds.forEach {
                val buildTask = target.tasks.getByName("${buildName}Build$it")
                buildTasks.add(buildTask)
                if (updateMap) {
                    task.targetDirectoriesMap.put(it, (buildTask.property("targetDirectory") as DirectoryProperty).asFile)
                }
            }
            task.dependsOn(buildTasks)
        }
    }
}

/**
 * This task is useful for running all builds in the builds list. It also supplies an output property containing
 * a Map of build target directories, keyed by the values in the builds() configuration function.
 */
open class BuildAllTask @Inject constructor(): DefaultTask() {
    init {
        description = "Aggregator for all tasks listed in the builds(...) function"
        group = "build"
    }

    @get:Input
    val builds: ListProperty<BuildType> = project.objects.listProperty(BuildType::class.java)

    @get:OutputDirectories
    val targetDirectoriesMap: MapProperty<BuildType, File> = project.objects.mapProperty(BuildType::class.java, File::class.java)

    @TaskAction
    fun dummy() {
        // no processing required
    }
}

open class CleanAllTask @Inject constructor(): DefaultTask() {
    init {
        description = "Aggregator for all tasks listed in the builds(...) function"
        group = "build"
    }

    @get:Input
    val sourceArchive: Property<String> = project.objects.property(String::class.java)

    @get:InputDirectory
    val targetsDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:InputDirectory
    val sourceDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    val builds = emptyList<BuildType>().toMutableList()

    @TaskAction
    fun dummy() {
        val targetDir = targetsDirectory.asFile.get()
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        val sourceDir = sourceDirectory.asFile.get()
        if (sourceDir.exists()) {
            builds.forEach {
                val srcBuild = sourceDir.resolve(it.name)
                if (srcBuild.exists()) srcBuild.deleteRecursively()
            }
        }
        sourceDir.parentFile?.listFiles()?.forEach {
            if (!it.isDirectory && it.nameWithoutExtension.startsWith(sourceArchive.get()))
                it.delete()
        }
    }
}