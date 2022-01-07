package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import java.io.File
import javax.inject.Inject

abstract class OpensslBuildTask @Inject constructor(buildType: String)
    : BuilderTask(buildType) {
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
            BuildTypes.vStudio64 -> visualStudioBuild(buildSrcDir)
            BuildTypes.mingw64 -> {
                linuxScript(buildSrcDir, "mingw64", windows.mingwBinPath)
                mingwPatterns
            }
            BuildTypes.linuxX64 -> {
                linuxScript(buildSrcDir, "linux-x86_64")
                linuxPatterns
            }
            BuildTypes.arm64_v8a,
            BuildTypes.x86_64 -> androidBuild(buildSrcDir, buildType)
            BuildTypes.macX64 -> {
                linuxScript(buildSrcDir, "darwin64-x86_64-cc")
                applePatterns + "*.dylib"
            }
            BuildTypes.iosX64,
            BuildTypes.iosArm64 -> {
                iosScript(buildSrcDir, buildType == BuildTypes.iosX64)
                applePatterns
            }
            else ->
                throw GradleException("Unsupported OpenSSL buildType: $buildType")
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

        runner.command(srcDir, "./$shFilename") {
            it.workingDir(srcDir)
            if (path.isNotEmpty())
                it.environment("PATH", path)
        }
    }

    private fun androidBuild(srcDir: File, buildType: String): List<String> {
        val arch = when (buildType) {
            BuildTypes.arm64_v8a -> androidTargets[0]
            BuildTypes.x86_64 -> androidTargets[1]
            else -> throw IllegalArgumentException("Unsupported android build type: $buildType")
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

        runner.command(srcDir, "./$shFilename") {}
        return linuxPatterns
    }

    /**
     * Use this for IOS builds
     */
    private fun iosScript(srcDir: File, simulator: Boolean, path: String = "") {
        val platform = if (simulator) "iPhoneSimulator" else "iPhoneOS"
        val arch = if (simulator) iosTargets[0] else iosTargets[1]
        val crossPath = "${platformsLocation.get()}/$platform.platform/Developer"
        val shFilename = createPluginFile(srcDir, "$defaultFileName.sh") {
            """
            #!/bin/zsh    
            export PLATFORM=$platform
            export CC=clang;
            export PATH="/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:${'$'}PATH"
            ./Configure $arch $configureOptionsString -isysroot $crossPath/SDKs/$platform.sdk -miphoneos-version-min=${iosMinimumSdk.get()}
            $make all
            """.trimIndent()
        }

        runner.command(srcDir, "./$shFilename") {
            it.workingDir(srcDir)
            if (path.isNotEmpty())
                it.environment("PATH", path)
        }
    }


    companion object {
        val androidTargets = listOf("android-arm64", "android64-x86_64")
        val iosTargets = listOf("iossimulator-xcrun", "ios64-cross")
        // use ./Configure LIST to find these
    }
}