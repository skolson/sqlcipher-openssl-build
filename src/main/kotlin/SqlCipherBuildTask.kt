package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

abstract class SqlcipherBuildTask @Inject constructor(buildType: String): BuilderTask(buildType) {
    override val buildName = "sqlcipher"

    init {
        description = "SqlCipher build task for buildType: $buildType"
    }

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

    private val compilerOptionsString get() = listToString(compilerOptions.get())

    fun setup(srcDir: File, opensslIncludeDir: File, opensslLibDir: File, targetDir: File,
              windowsSdkInstall: String, windowsSdkLibVersion: String,
              compilerOptions: List<String>) {
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
    }

    @TaskAction
    fun buildAction() {
        val s = sourceDirectory.get().asFile
        val t = targetDirectory.get().asFile
        val i = opensslIncludeDirectory.get().asFile
        val lib = opensslLibDirectory.get().asFile
        logger.info("Start $name action")
        when (buildType) {
            BuildTypes.vStudio64 -> vstudioBuild(s, t, i, lib)
            BuildTypes.mingw64 -> linuxScript(s, t,
                    Runner.getMsysPath(i),
                    Runner.getMsysPath(lib),
                    "--build=mingw64")
            BuildTypes.linuxX64 -> linuxScript(s, t, i.absolutePath, lib.absolutePath)
            BuildTypes.x86_64,
            BuildTypes.arm64_v8a -> androidBuild(buildType, s, t, i, lib)
            else ->
                throw GradleException("Unsupported build type: $buildType")
        }
        logger.info("End $name action.")
    }

    private fun vstudioBuild(srcDir: File, outDir: File, opensslIncludeDir: File, opensslLibDir: File) {
        val sdkLibPath = "${windowsSdkInstall.get()}\\Lib\\${windowsSdkLibVersion.get()}\\"
        val nmakeCmdFileName = createPluginFile(srcDir, "nmakeCmdFile.txt") {
            """
            FOR_WIN10=1
            PLATFORM=x64
            USE_NATIVE_LIBPATHS=1
            NCRTLIBPATH="${sdkLibPath}ucrt\x64"
            NSDKLIBPATH="${sdkLibPath}um\x64"
            LTLIBS="Advapi32.lib User32.lib kernel32.lib"
            CCOPTS="-guard:cf ${compilerOptionsString}-I${opensslIncludeDir.absolutePath}"
            SHELL_CORE_LIB=libsqlite3.lib
            LDFLAGS=${opensslLibDir}\libcrypto_static.lib
            """.trimIndent()
        }

        val batFilename = createPluginFile(srcDir, "${defaultFileName}.bat") {
            """
            call "${vStudioEnvFilePath.get()}"
            nmake /C /f Makefile.msc clean
            nmake /f Makefile.msc @$nmakeCmdFileName
            """.trimIndent()
        }

        runner.executable("cmd.exe") {
            it.args("/c", batFilename)
            it.workingDir(srcDir)
        }
        runner.copyResults(srcDir, outDir, windowsPatterns + "sqlite3.h")
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
                "CFLAGS=\"${compilerOptionsString}-I$opensslInclude\""

        val shFilename = createPluginFile(srcDir,"${buildName}-$buildType.sh") {
            """
            #!/bin/sh
            $cmdLine
            make clean
            make
            """.trimIndent()
        }

        runner.command(srcDir, "./$shFilename") { }
        runner.copyResults(srcDir.resolve(".libs"), targetDir)
        runner.copyResults(srcDir, targetDir, listOf("sqlite3.h", "sqlite"))
    }

    private fun androidBuild(buildType: String, srcDir: File, targetDir: File, opensslIncludeDir: File, opensslLibDir: File) {
        val builder = AndroidSqlCipher(runner, srcDir, buildType, androidMinimumSdk.get())
        builder.build(opensslIncludeDir,
            opensslLibDir,
            compilerOptionsString,
            androidNdkRoot.get(),
            androidNdkPath)
        runner.copyResults(builder.outputDir, targetDir, listOf("*.so"))
        runner.copyResults(srcDir, targetDir, listOf("sqlite3.h"))
    }
}

