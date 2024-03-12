package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import javax.inject.Inject

abstract class SqlcipherBuildTask @Inject constructor(buildType: BuildType): BuilderTask(buildType) {
    @Internal
    override val buildName = "sqlcipher"

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val opensslIncludeDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val opensslLibDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val targetDirectory: DirectoryProperty

    @get:Input
    abstract val windowsSdkInstall: Property<String>

    @get:Input
    abstract val windowsSdkLibVersion: Property<String>

    @get:Input
    abstract val compilerOptions: ListProperty<String>

    @get:Input
    abstract val buildCompilerOptions: MapProperty<BuildType, List<String>>

    private val allCompilerOptions get() = compilerOptions.get() +
            (buildCompilerOptions.get()[buildType] ?: emptyList())

    private val compilerOptionsString get() = listToString(allCompilerOptions)
    private lateinit var iosConfig: AppleToolExtension
    private val moduleName = SqlcipherExtension.moduleName
    private val moduleNameH = SqlcipherExtension.moduleNameH
    private var targetsCopyTo: ((buildType: BuildType) -> File?)? = null
    private var copyCinteropIncludes:Boolean = false

    override fun setToolsProperties(tools: ToolsExtension) {
        super.setToolsProperties(tools)
        iosConfig = tools.apple
    }

    init {
        description = "SqlCipher build task for buildType: $buildType"
    }

    fun setup(srcDir: File,
              opensslIncludeDir: File,
              opensslLibDir: File,
              targetDir: File,
              windowsSdkInstall: String,
              windowsSdkLibVersion: String,
              compilerOptions: List<String>,
              buildCompilerOptions: Map<BuildType, List<String>>,
              targetsCopyTo: ((buildType: BuildType) -> File?)?,
              copyCinteropIncludes: Boolean
    ) {
        this.sourceDirectory.set(srcDir)
        inputs.dir(sourceDirectory)
        opensslIncludeDirectory.set(opensslIncludeDir)
        inputs.dir(opensslIncludeDirectory)
        opensslLibDirectory.set(opensslLibDir)
        inputs.dir(opensslLibDirectory)
        targetDirectory.set(targetDir)
        outputs.dir(targetDirectory)
        this.windowsSdkInstall.set(windowsSdkInstall)
        this.windowsSdkLibVersion.set(windowsSdkLibVersion)
        this.compilerOptions.set(compilerOptions)
        this.buildCompilerOptions.set(buildCompilerOptions)
        this.targetsCopyTo = targetsCopyTo
        this.copyCinteropIncludes = copyCinteropIncludes
    }

    @TaskAction
    fun buildAction() {
        val s = sourceDirectory.get().asFile
        val t = targetDirectory.get().asFile
        val i = opensslIncludeDirectory.get().asFile
        val lib = opensslLibDirectory.get().asFile
        logger.info("Start $name action")
        when (buildType) {
            BuildType.vStudio64 -> vstudioBuild(s, t, i, lib)
            BuildType.mingwX64 -> linuxScript(s, t,
                    Runner.getMsysPath(i),
                    Runner.getMsysPath(lib),
                    "--build=mingw64")
            BuildType.linuxX64 -> linuxScript(s, t, i.absolutePath, lib.absolutePath)
            BuildType.androidX64,
            BuildType.androidArm64 -> androidBuild(buildType, s, t, i, lib)
            BuildType.macosX64,
            BuildType.macosArm64,
            BuildType.iosArm64,
            BuildType.iosX64 -> appleBuild(buildType, s, t, i)
        }
        targetsCopyTo?.invoke(buildType)?.let { targetDir ->
            runner.copyResults(t, targetDir)
            if (copyCinteropIncludes) {
                runner.copyResults(lib, targetDir, listOf("libcrypto.*"))
                sourceDirectory.get().asFile.resolve("src").also {
                    if (!it.exists())
                        throw GradleException("Sqlcipher src subdirectory not found at path: ${it.absolutePath} ")
                    if (buildType.isWindowsOnly) {
                        runner.copyResults(
                            it, targetDir, listOf(
                                "sqlcipher.h",
                                "sqliteInt.h",
                                "vdbeInt.h"
                            )
                        )
                    }
                }
            }
        }
        logger.info("End $name action.")
    }

    private fun vstudioBuild(srcDir: File, outDir: File, opensslIncludeDir: File, opensslLibDir: File) {
        val sdkLibPath = "${windowsSdkInstall.get()}\\Lib\\${windowsSdkLibVersion.get()}\\"
        val winCompilerOptions = "-guard:cf ${compilerOptionsString}-I${opensslIncludeDir.absolutePath}"
        val nmakeCmdFileName = createPluginFile(srcDir, "nmakeCmdFile.txt") {
            """
            FOR_WIN10=1
            PLATFORM=x64
            USE_NATIVE_LIBPATHS=1
            NCRTLIBPATH="${sdkLibPath}ucrt\x64"
            NSDKLIBPATH="${sdkLibPath}um\x64"
            LTLIBS="Advapi32.lib User32.lib kernel32.lib"
            CCOPTS="$winCompilerOptions"
            SHELL_CORE_LIB=lib$moduleName.lib
            LDFLAGS=${opensslLibDir}\libcrypto_static.lib
            """.trimIndent()
        }

        val batFilename = createPluginFile(srcDir, "$defaultFileName.bat") {
            """
            call "${vStudioEnvFilePath.get()}"
            nmake /f Makefile.msc @$nmakeCmdFileName
            """.trimIndent()
        }
        runner.logger.lifecycle("$defaultFileName.bat compilerOptions: $winCompilerOptions")
        runner.executable("cmd.exe") {
            it.args("/c", batFilename)
            it.workingDir(srcDir)
        }
        runner.copyResults(srcDir, outDir, windowsPatterns + moduleNameH)
    }

    /**
     * Used for Linux and Mingw build actions
     */
    private fun linuxScript(srcDir: File,
                            targetDir: File,
                            opensslInclude: String,
                            opensslLib: String,
                            buildOption: String = "") {
        // Note the -lm for the linux math library, not needed in mingw64. Without this when -DSQLITE_ENABLE_FTS5 specified, link error occurs
        val ldflags = "LDFLAGS=\"-L$opensslLib -lcrypto -lm\""
        val cmdLine = "./configure $buildOption --enable-tempstore=yes --disable-tcl --enable-static=yes --with-crypto-lib=none " +
                "$ldflags " +
                "CFLAGS=\"$compilerOptionsString -I$opensslInclude\""

        val shFilename = createPluginFile(srcDir,"${buildName}-$buildType.sh") {
            """
            #!/bin/sh
            $cmdLine
            make
            """.trimIndent()
        }
        runner.logger.lifecycle("$shFilename compilerOptions: $compilerOptionsString")
        runner.command(srcDir, "./$shFilename") { }
        runner.copyResults(srcDir.resolve(".libs"), targetDir)
        runner.copyResults(srcDir, targetDir, listOf(moduleNameH, moduleName))
    }

    private fun androidBuild(buildType: BuildType, srcDir: File, targetDir: File, opensslIncludeDir: File, opensslLibDir: File) {
        val builder = SqlCipherAndroid(runner, srcDir, buildType, androidMinimumSdk.get())
        builder.build(opensslIncludeDir,
            opensslLibDir,
            compilerOptionsString,
            androidNdkRoot.get(),
            androidNdkPath,
            r22OrLater)
        runner.copyResults(builder.outputDir, targetDir, listOf("*.so"))
        runner.copyResults(srcDir, targetDir, listOf(moduleNameH))
    }

    private fun appleBuild(buildType: BuildType, srcDir: File, targetDir: File, opensslIncludeDir: File) {
        val builder = SqlCipherApple(runner, srcDir, buildType, iosConfig)
        val result = builder.build(opensslIncludeDir,
            allCompilerOptions)
        if (!srcDir.resolve(SqlCipherApple.libraryName).exists())
            throw GradleException("Build failed. \n$result")
        runner.copyResults(srcDir, targetDir, listOf("*.a"))
        runner.copyResults(srcDir, targetDir, listOf(moduleNameH))
    }
}

