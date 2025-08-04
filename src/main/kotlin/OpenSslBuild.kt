package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Isolates the gradle input and output properties for OpenSSL tasks
 */
abstract class OpenSslExtractTask@Inject constructor(private val project: Project)
    : ExtractArchiveTask(project) {
    @get:InputFile
    abstract override val archiveFile: RegularFileProperty
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class OpenSslGitTask: GitCheckoutTask() {
    @get:Input
    abstract override val uri: Property<String>
    @get:Input
    abstract override val tagName: Property<String>
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class OpenSslDownloadTask: DownloadArchiveTask() {
    @get:Input
    abstract override val url: Property<String>
    @get:OutputFile
    abstract override val downloadFile: RegularFileProperty
}

class OpenSslBuild(
    target: Project,
    private val sqlcipherExt: SqlcipherExtension,
    execOperations: ExecOperations
    )
    : Builder(target, sqlcipherExt.toolsExt, execOperations)
{
    override val buildName = constBuildName
    override var gitTask: TaskProvider<out GitCheckoutTask>
    override var downloadTask: TaskProvider<out DownloadArchiveTask>

    private val ext = sqlcipherExt.opensslExt

    /**
     * These overrides are using get() as extensions are not populated until tasks are to be configured by Gradle
     */
    private val builds get() = sqlcipherExt.builds
    private val archiveTopDir get() = "openssl-${ext.tagName}"
    // parent directory of each of the build type subdirectories
    override val srcDir get() = target.layout.buildDirectory.get().asFile.resolve(ext.srcDirectory)
    override val useGit: Boolean get() = ext.useGit
    override val gitUri:String get() = ext.githubUri
    override val gitTagName:String get() = ext.tagName
    override val downloadUrl: String get() = ext.downloadSource

    init {
        val opensslVerify = target.tasks.register("${buildName}Verify") { task ->
            task.doLast {
                verifyPerl()
                verifyNasm()
            }
        }

        downloadTask = target.tasks.register("${buildName}Download", OpenSslDownloadTask::class.java) { task ->
            task.dependsOn(opensslVerify)
            task.onlyIf {
                !useGit
            }
            task.setup(downloadUrl, srcDir.resolve(ext.downloadSourceFileName))
        }

        gitTask = target.tasks.register("${buildName}Git", OpenSslGitTask::class.java) { task ->
            task.dependsOn(opensslVerify)
            task.onlyIf {
                useGit
            }
            task.setup(gitUri, gitTagName, gitDir)
        }

        BuildType.entries.forEach { buildType ->
            val gitCopyTaskName = taskName(copyTaskName, buildType)
            target.tasks.register(gitCopyTaskName, Copy::class.java) { task ->
                task.dependsOn(gitTask)
                task.onlyIf {
                    useGit && taskOk(builds, buildType)
                }
                task.from(gitDir)
                task.into(srcDir.resolve(buildType.name).resolve(archiveTopDir))
            }

            registerTasks(buildType, gitCopyTaskName)
        }
    }

    private fun registerTasks(buildType: BuildType, gitCopyTaskName: String) {
        val extractName = taskName(extractTaskName, buildType)
        val extractTask = target.tasks.register(extractName, OpenSslExtractTask::class.java) { task ->
            task.dependsOn(downloadTask)
            task.onlyIf {
                !useGit && taskOk(builds, buildType)
            }
            task.setup(downloadTask.get().downloadFile.get().asFile, compileDirectory(buildType, archiveTopDir))
        }

        val verify = target.tasks.register(taskName(verifyTaskName, buildType)) { task ->
            task.dependsOn(extractTask, gitTask)
            task.onlyIf {
                taskOk(builds, buildType)
            }
            task.doLast {
                val runner = makeRunner()
                when (buildType) {
                    BuildType.vStudio64 -> {
                        windows.verifyVisualStudio()
                        windows.verifyWindowsPerl()
                        val response = runner.executable(windows.windowsPerl) {
                            it.args("--version")
                            it.environment("PATH", "{tools.windowsPerlDir};")
                        }
                        parsePerlResponse(response)
                    }
                    BuildType.mingwX64 -> {
                        windows.verifyMingw64()
                        windows.verifyMsys2Perl()
                        val response = runner.executable(windows.msys2Perl) {
                            it.args("--version")
                            it.environment("PATH", "{tools.msys2PerlDir};")
                        }
                        parsePerlResponse(response)
                    }
                    BuildType.androidArm64,
                    BuildType.androidX64 -> {
                        android.determineNdkVersion()
                    }
                    BuildType.linuxX64,
                    BuildType.linuxArm64 -> {
                    }
                    BuildType.macosX64,
                    BuildType.macosArm64,
                    BuildType.iosArm64,
                    BuildType.iosX64,
                    BuildType.iosSimulatorArm64-> {
                    }
                }
            }
        }

        target.tasks.register(taskName(buildTaskName, buildType), OpensslBuildTask::class.java, buildType)
            .configure { task ->
                val prereqName = if (useGit)
                    gitCopyTaskName
                else
                    extractName
                task.dependsOn(verify, target.tasks.getByName(prereqName))
                task.onlyIf {
                    taskOk(builds, buildType)
                }
                task.setToolsProperties(tools)
                val targetsDirectory = createTargetsDirectory(ext.targetsDirectory, buildName)
                val options = ext.configureOptions.toMutableList()
                if (ext.buildSpecificOptions.containsKey(buildType)) {
                    ext.buildSpecificOptions[buildType]?.let {
                        options.addAll(0, it)
                    }
                }
                val compileDir = compileDirectory(buildType, archiveTopDir)

                task.setup(compileDir,
                        buildType.targetDirectory(targetsDirectory),
                        options)
            }
    }

    private fun verifyPerl() {
        val runner = makeRunner()
        when (host) {
            HostOs.MAC,
            HostOs.LINUX -> {
                val response = runner.executable(tools.bashPerl) {
                    it.args("--version")
                }
                parsePerlResponse(response)
            }
            HostOs.WINDOWS -> {
                // Perl comes with base MSYS2. MSYS2 install has been verified
            }
        }
    }

    private fun parsePerlResponse(response: String) {
        if (!response.startsWith("This is perl"))
            throw GradleException("Perl is required, does not seem to be on path. Response: $response")
        val version = response.substringAfter('(').substringBefore(')')
        target.logger.info("Perl found on path, version: $version")
    }

    private fun verifyNasm() {
        if (ext.configureOptions.contains("no-asm")) {
            target.logger.lifecycle("$buildName: no-asm specified in configureOptions, NASM not required")
            return
        }
        val expectedPrefix = "NASM version "
        val executable = when (host) {
            HostOs.MAC,
            HostOs.LINUX -> "./nasm"
            HostOs.WINDOWS -> "nasm.exe"
        }
        val response = makeRunner().executable(executable) {
            it.args("--version")
        }
        if (!response.startsWith(expectedPrefix))
            throw GradleException("NASM is required, does not seem to be on path. (www.nasm.us)")
        val version = response.substring(expectedPrefix.length).substringBefore(' ')
        target.logger.info("NASM found on path. Version: $version")
    }

    companion object {
        const val constBuildName = "openssl"
    }
}
