package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.net.URL
import javax.inject.Inject

/**
 * Isolates the gradle input and output properties for OpenSSL tasks
 */
abstract class OpenSslExtractTask: @Inject ExtractArchiveTask() {
    @get:InputFile
    abstract override val archiveFile: RegularFileProperty
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class OpenSslGitTask: @Inject GitCheckoutTask() {
    @get:Input
    abstract override val uri: Property<String>
    @get:Input
    abstract override val tagName: Property<String>
    @get:OutputDirectory
    abstract override val sourceDirectory: DirectoryProperty
}

abstract class OpenSslDownloadTask: @Inject DownloadArchiveTask() {
    @get:Input
    abstract override val url: Property<URL>
    @get:OutputFile
    abstract override val downloadFile: RegularFileProperty
}

class OpenSslBuild(target: Project, sqlcipherExt: SqlcipherExtension)
    : Builder(target, sqlcipherExt.toolsExt)
{
    override val buildName = constBuildName
    override lateinit var extractTask: TaskProvider<out ExtractArchiveTask>
    override lateinit var gitTask: TaskProvider<out GitCheckoutTask>
    override lateinit var downloadTask: TaskProvider<out DownloadArchiveTask>

    private val ext = sqlcipherExt.opensslExt
    private val buildTypes = sqlcipherExt.buildTypes
    private val builds = sqlcipherExt.builds

    /**
     * These overrides are using get() to avoid setting them at afterEvaluate
     */
    override val useGit: Boolean get() = ext.useGit
    override val gitUri:String get() = ext.githubUri
    override val gitTagName:String get() = ext.tagName
    override val downloadUrl: URL get() = URL(ext.downloadSource)

    init {
        val opensslVerify = target.tasks.register("${buildName}Verify") { task ->
            task.doLast {
                verifyPerl()
                verifyNasm()
            }
        }
        val srcDir = target.buildDir.resolve(ext.srcDirectory)
        if (!srcDir.exists()) srcDir.mkdir()
        registerSourceTasks(
                { srcDir.resolve(ext.downloadSourceFileName) },
                { ext.compileDirectory(target) },
                opensslVerify)
        buildTypes.supportedBuilds.forEach {
            registerTasks(it)
        }
    }

    override fun registerExtractTask() {
        extractTask = target.tasks.register("${buildName}Extract", OpenSslExtractTask::class.java)
    }

    override fun registerGitTask() {
        gitTask = target.tasks.register("${buildName}Git", OpenSslGitTask::class.java)
    }

    override fun registerDownloadTask() {
        downloadTask = target.tasks.register("${buildName}Download", OpenSslDownloadTask::class.java)
    }

    private fun registerTasks(buildType: String) {
        val verify = target.tasks.register("${buildName}Verify$buildType") { task ->
            task.dependsOn(extractTask, gitTask)
            task.onlyIf {
                builds.contains(buildType)
            }
            task.doLast {
                val runner = makeRunner()
                when (buildType) {
                    BuildTypes.vStudio64 -> {
                        windows.verifyVisualStudio()
                        windows.verifyWindowsPerl()
                        val response = runner.executable(windows.windowsPerl) {
                            it.args("--version")
                            it.environment("PATH", "{tools.windowsPerlDir};")
                        }
                        parsePerlResponse(response)
                    }
                    BuildTypes.mingw64 -> {
                        windows.verifyMingw64()
                        windows.verifyMsys2Perl()
                        val response = runner.executable(windows.msys2Perl) {
                            it.args("--version")
                            it.environment("PATH", "{tools.msys2PerlDir};")
                        }
                        parsePerlResponse(response)
                    }
                    BuildTypes.arm64_v8a,
                    BuildTypes.x86_64 -> {
                        android.determineNdkVersion()
                    }
                    BuildTypes.linuxX64 -> {
                    }
                    BuildTypes.ios64 -> {
                    }
                    else -> throw GradleException("Verify: unsupported buildType: $buildType")
                }
            }
        }

        target.tasks.register("${buildName}Build$buildType", OpensslBuildTask::class.java, buildType)
            .configure { task ->
                task.dependsOn(verify)
                task.onlyIf {
                    builds.contains(buildType)
                }
                task.setToolsProperties(tools)
                val targetsDirectory = createTargetsDirectory(ext.targetsDirectory, buildName)
                val srcDir = ext.compileDirectory(target)
                val options = ext.configureOptions.toMutableList()
                if (ext.buildSpecificOptions.containsKey(buildType)) {
                    ext.buildSpecificOptions[buildType]?.let {
                        options.addAll(0, it)
                    }
                }
                task.setup(srcDir,
                        buildTypes.targetDirectory(targetsDirectory, buildType),
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
