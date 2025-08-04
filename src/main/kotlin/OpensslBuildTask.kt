package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class OpensslBuildTask @Inject constructor(
    buildType: BuildType,
    execOperations: ExecOperations
    )
    : BuilderTask(buildType, execOperations) {
    @get:Input
    override val buildName = "openssl"

    init {
        description = "OpenSSL build task for buildType: $buildType"
    }

    private val configureOptions: ListProperty<String> = project.objects.listProperty(String::class.java)

    private val configureOptionsString: String get() = listToString(configureOptions.get())

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val targetDirectory: DirectoryProperty

    @get:Internal
    abstract val includeDirectory: DirectoryProperty

    private lateinit var config: AppleToolExtension

    override fun setToolsProperties(
        tools: ToolsExtension
    ) {
        super.setToolsProperties(tools)
        config = tools.apple
    }

    fun setup(srcDir: File, targetDir: File,
              configureOptionList: List<String>) {
        configureOptions.set(configureOptionList)

        inputs.files(srcDir)
        sourceDirectory.set(srcDir)
        includeDirectory.set(srcDir.resolve("include"))
        if (includeDirectory.get().asFile.absolutePath.contains(' '))
            throw GradleException("OpenSSL include dir contains embedded blank(s) that are problematic for Make")
        targetDirectory.set(targetDir)
        outputs.dirs(targetDirectory, sourceDirectory, includeDirectory)
    }

    @TaskAction
    fun buildAction() {
        val buildSrcDir = sourceDirectory.get().asFile
        val tDir = targetDirectory.get().asFile
        logger.info("Start $name action")
        val patterns = when (buildType) {
            BuildType.vStudio64 -> visualStudioBuild(buildSrcDir)
            BuildType.mingwX64 -> {
                linuxScript(buildSrcDir, "mingw64", windows.mingwBinPath)
                mingwPatterns
            }
            BuildType.linuxArm64 -> {
                linuxScript(buildSrcDir, "linux-aarch64")
                linuxPatterns
            }
            BuildType.linuxX64 -> {
                linuxScript(buildSrcDir, "linux-x86_64")
                linuxPatterns
            }
            BuildType.androidArm64,
            BuildType.androidX64 -> androidBuild(buildSrcDir, buildType)
            BuildType.macosX64 -> {
                linuxScript(buildSrcDir, "darwin64-x86_64-cc")
                applePatterns + "*.dylib"
            }
            BuildType.macosArm64 -> {
                linuxScript(buildSrcDir, "darwin64-arm64-cc")
                applePatterns + "*.dylib"
            }
            BuildType.iosX64,
            BuildType.iosArm64,
            BuildType.iosSimulatorArm64 -> {
                iosScript(buildSrcDir, buildType)
                applePatterns
            }
        }
        logger.info("End $name action")
        logger.info("Intermediate and final build products are in build directory: ${buildSrcDir.absolutePath}")
        runner.copyResults(buildSrcDir, tDir, patterns)

        val testSubdirectory = "test"
        val testDir = tDir.resolve(testSubdirectory)
        if (!testDir.exists()) testDir.mkdir()
        runner.copyResults(buildSrcDir.resolve(testSubdirectory), testDir, patterns)
    }

    private fun visualStudioBuild(srcDir: File): List<String> {
        val batFilename = createPluginFile(srcDir, "$defaultFileName.bat") {
            """
            call "${vStudioEnvFilePath.get()}"
            perl.exe Configure VC-WIN64A $configureOptionsString
            nmake
            """.trimIndent()        }

        runner.executable(WindowsToolExtension.cmdExe) {
            it.args("/c", batFilename)
            it.workingDir(srcDir)
        }
        return windowsPatterns
    }

    /**
     * Use this for both linux and mingw builds
     */
    private fun linuxScript(srcDir: File, configureHost: String, path: String = "") {
        val shFilename = createPluginFile(srcDir, "$defaultFileName.sh") {
            """
            ./Configure $configureHost $configureOptionsString
            $make all
            """.trimIndent()
        }

        runner.command(srcDir, "./$shFilename", execOperations) {
            it.workingDir(srcDir)
            if (path.isNotEmpty())
                it.environment("PATH", path)
        }
    }

    private fun androidBuild(srcDir: File, buildType: BuildType): List<String> {
        val arch = when (buildType) {
            BuildType.androidArm64 -> androidTargets[0]
            BuildType.androidX64 -> androidTargets[1]
            else -> throw GradleException("Unsupported android build type: $buildType")
        }
        val configureCommand = StringBuilder()
                .append("./Configure ")
                .append(arch)
                .append(" -D_ANDROID_API=${androidMinimumSdk.get()}")
                .append(" -D_FILE_OFFSET_BITS=64 $configureOptionsString")

        val exportPath = "export PATH=$androidNdkPath:\$PATH"
        val ndkRoot = if (host == HostOs.WINDOWS)
            Runner.getMsysPath(androidNdkRoot.get())
        else
            androidNdkRoot.get()
        val shFilename = createPluginFile(srcDir, "$defaultFileName.sh") {
            """
            #!/bin/sh
            export ANDROID_NDK_ROOT=$ndkRoot
            $exportPath
            $configureCommand
            make build_libs
            """.trimIndent()
        }

        runner.command(srcDir, "./$shFilename", execOperations) {}
        return linuxPatterns
    }

    /**
     * Use this for IOS builds. See Configurations/15-ios.conf
     */
    private fun iosScript(srcDir: File, buildType: BuildType, path: String = "") {
        val arch = iosTargets[buildType]
        val shFilename = createPluginFile(srcDir, "$defaultFileName.sh") {
            """
            #!/bin/zsh    
            export CC=clang;
            export CROSS_COMPILE="${config.toolChainPath}"
            export CROSS_SDK=${config.platform(buildType)}.sdk
            export CROSS_TOP=${config.crossPath(buildType)}
            ./Configure $arch $configureOptionsString ${config.optionsString(buildType)}
            $make all
            """.trimIndent()
        }

        runner.command(srcDir, "./$shFilename", execOperations) {
            it.workingDir(srcDir)
            if (path.isNotEmpty())
                it.environment("PATH", path)
        }
    }


    companion object {
        val androidTargets = listOf("android-arm64", "android64-x86_64")
        val iosTargets = mapOf(
            BuildType.iosX64 to "iossimulator-xcrun",
            BuildType.iosSimulatorArm64 to "iossimulator-arm64-xcrun",
            BuildType.iosArm64 to "ios64-cross"
        )
        // use ./Configure LIST to find these
    }
}