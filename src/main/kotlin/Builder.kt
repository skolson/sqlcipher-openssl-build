package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.StringBuilder
import java.net.URL

/**
 * Helpers for running commands on the current host using Gradle Project exec(). Has these features:
 * 1. Prints command line to be executed after all args and other setup processing, before execution.
 * 2. captures stderr and stdout during execution.
 * 3. prints stderr(if any) followed by stdout, independent of whether the command failed
 * 4. If result is non-zero (command failed), throws GradleException of some flavor
 */
class Runner(private val target: Project, private val host:HostOs, private val ext: WindowsToolExtension) {
    val lineSeparator = if (host == HostOs.WINDOWS) "\r\n" else "\n"
    private lateinit var result: ExecResult
    val logger: org.gradle.api.logging.Logger = target.logger

    /**
     * Raw setup of an ExecSpec. Captures stderr and stdout returned as a String.
     * Working Directory, args and other ExecSpec configuration must be done in lambda. No attempt at host-specific
     * processing is made, user must provide.
     * @param executable string value of executable only. can be a full absolute path or relative to working directory.
     * @param setup lambda for configuring ExecSpec used to execute task
     * @returns stderr (if any) followed by stdout, as a String.
     */
    fun executable(executable: String, setup: ((execSpec: ExecSpec) -> Unit)): String {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        result = target.exec {
            it.executable(executable)
            it.standardOutput = output
            it.errorOutput = err
            it.isIgnoreExitValue = true
            setup(it)
            printExec(it)
        }
        return responseParse(err, output)
    }

    /**
     * Run a unix-like command, such as a shell script. Captures stderr and stdout returned as a String. Not intended
     * for windows native commands, @see [executable]. If host is windows,
     * MSYS2 is used to run the command using bash, otherwise native linux or macOS shell command is performed.
     *
     * Note that if using MSYS2, ExecSpec path is set to include the MSYS2 and MINGW bin directories.  To add more to
     * this path, use code like this in the [setup] lambda to avoid losing path info:
     *      it.environment("PATH", "$newPath;${it.environment[\"PATH\"}")
     * @param workingDir already existing working directory to execute in.
     * @param command used as ExecSpec executable
     * @param setup lambda for configuring ExecSpec, typically for additional args or setting environment vars.
     * @returns stderr (if any) followed by stdout, as a String.
     */
    fun command(workingDir: File, command:String, setup: ((execSpec: ExecSpec) -> Unit)): String {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        if (host == HostOs.WINDOWS) {
            result = target.exec {
                it.executable(ext.msys2Exec)
                it.workingDir(workingDir)
                it.standardOutput = output
                it.errorOutput = err
                it.args("MSYSTEM=MINGW64", "CHERE_INVOKING=1", "MSYS2_PATH_TYPE=inherit", "/usr/bin/bash", "-lc")
                it.args(command)
                it.environment("PATH", ext.mingwBinPath)
                it.isIgnoreExitValue = true
                setup(it)
                printExec(it)
            }
        } else {
            result = target.exec {
                it.executable(command)
                it.workingDir(workingDir)
                it.standardOutput = output
                it.errorOutput = err
                it.isIgnoreExitValue = true
                setup(it)
                printExec(it)
            }
        }
        return responseParse(err, output)
    }

    private fun printExec(execSpec: ExecSpec) {
        logger.info("Starting command: ${execSpec.commandLine}")
    }

    /**
     * Send stderr and stdout to logger.
     * @return first line of stdout if result shows no error.
     */
    private fun responseParse(stderr: ByteArrayOutputStream, stdout: ByteArrayOutputStream): String {
        if (result.exitValue != 0) {
            Logger.errorLines(stderr)
            Logger.errorLines(stdout)
            result.rethrowFailure()
        }
        Logger.errorLines(stderr, true)
        return Logger.textLines(stdout)
    }

    fun copyResults(srcDir: File, targetDir: File, patterns: List<String> = listOf()) {
        target.copy { copySpec ->
            copySpec.from(srcDir) {
                if (patterns.isNotEmpty())
                    it.include(patterns)
                it.includeEmptyDirs = false
            }
            copySpec.into(targetDir)
        }
        logger.info("Copied $patterns to ${targetDir.absolutePath}")
    }

    companion object {
        fun getMsysPath(file: File): String {
            val path = file.absolutePath
            if (path[1] != ':')
                throw GradleException("file absolute path does not start with a drive letter: $path")
            val b = StringBuilder()
            b.append("/").append(path[0])
                    .append(forwardSlash(path.substring(2)))
            return b.toString()
        }

        fun forwardSlash(path:String): String {
            return path.replace('\\', '/')
        }
    }
}

/**
 * Reusable parts of Build subclasses
 */
abstract class Builder(val target: Project, val tools: ToolsExtension)
{
    val windows = tools.windows
    val android = tools.android
    val host = HostOs.query()

    abstract val buildName: String
    abstract val useGit: Boolean
    abstract val gitUri: String
    abstract val gitTagName: String
    abstract val downloadUrl: URL
    abstract var gitTask: TaskProvider<out GitCheckoutTask>
    abstract var downloadTask: TaskProvider<out DownloadArchiveTask>
    abstract val srcDir: File
    val gitDir get() = srcDir.resolve("git")
    val buildTaskName = "Build"
    val copyTaskName = "GitCopy"
    val extractTaskName = "Extract"
    val verifyTaskName = "Verify"

    fun createTargetsDirectory(targetsName: String, buildName: String): File {
        val targetsDir = target.buildDir.resolve(targetsName)
        if (!targetsDir.exists()) targetsDir.mkdirs()
        val buildNameDir = targetsDir.resolve(buildName)
        if (!buildNameDir.exists()) buildNameDir.mkdirs()
        return buildNameDir
    }

    fun downloadSuffix(fileName: String): String {
        return if (host == HostOs.WINDOWS)
            "$fileName$windowsArchiveSuffix"
        else
            "$fileName$linuxArchiveSuffix"
    }

    fun taskName(name: String, buildType: String): String {
        return "$buildName$name$buildType"
    }

    fun compileDirectory(buildType: String, subDirectory: String = ""): File {
        val srcBuildType = srcDir.resolve(buildType)
        if (!srcBuildType.exists()) srcBuildType.mkdir()
        return if (subDirectory.isEmpty())
            srcBuildType
        else {
            val dir = srcBuildType.resolve(subDirectory)
            if (!dir.exists()) dir.mkdir()
            dir
        }
    }

    fun makeRunner(): Runner {
        return Runner(target, tools.buildTypes.host, tools.windows)
    }

    companion object {
        const val windowsArchiveSuffix = ".zip"
        const val linuxArchiveSuffix = ".tar.gz"

        fun isWindowsArchive(archiveFile: File): Boolean {
            val fileName = archiveFile.name.lowercase()
            return fileName.endsWith(windowsArchiveSuffix)
        }
    }
}