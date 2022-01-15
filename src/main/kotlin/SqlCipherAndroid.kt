package com.oldguy.gradle

import java.io.File

/**
 * Android NDK using ndk-build. May end up refactoring this to be an extension or part of one in the config DSL.
 * Currently it is just an android and ndk-build specific helper.
 */
class SqlCipherAndroid(
        private val runner:Runner,
        private val srcDir: File,
        private val buildType: BuildType,
        androidMinimumSdk: Int)
{
    private val host get() = HostOs.query()
    private val sqlite3 = SqlcipherExtension.moduleName
    var outputDir = srcDir
        private set

    /**
     * Application.mk variables and Android.mk variables that ndk-build uses
     */
    private val pluginAppMakeFileName = BuilderTask.pluginFileName(appMakeFileName)
    private val pluginMakeFileName = BuilderTask.pluginFileName(makeFileName)
    private val appVars = mapOf(
            "APP_PROJECT_PATH" to Runner.forwardSlash(srcDir.absolutePath),
            "APP_ABI" to buildType,
            "APP_BUILD_SCRIPT" to Runner.forwardSlash(srcDir.resolve(pluginMakeFileName).absolutePath),
            "APP_CFLAGS" to "-D_FILE_OFFSET_BITS=64",
            "APP_LDFLAGS" to "-Wl,--exclude-libs,ALL",
            "APP_PLATFORM" to "android-${androidMinimumSdk}",
            "APP_MODULES" to "libcrypto libsqlcipher"
    )
    private val makeVars = mapOf(
            "LOCAL_MODULE" to "libsqlcipher",
            "LOCAL_SRC_FILES" to "$sqlite3.c",
            "LOCAL_LDLIBS" to "-llog",
            "LOCAL_LDFLAGS" to "-fuse-ld=bfd"  // only use this for NDK r21 and earlier
    )
    private val ndkBuildOptions = listOf(
            "V=1",
            "NDK_DEBUG=0",
            "NDK_APPLICATION_MK=${Runner.forwardSlash(srcDir.resolve(pluginAppMakeFileName).absolutePath)}",
            "NDK_PROJECT_PATH=${Runner.forwardSlash(srcDir.absolutePath)}",
            "all"
    )
    private val requiredCFlags = listOf("-DLOG_NDEBUG", "-fstack-protector-all")

    fun build(opensslIncludeDir: File,
              opensslTargetDir: File,
              compilerOptionsString: String,
              androidNdkRoot: File,
              androidNdkPath: String,
              r22OrLater: Boolean): String
    {
        SqlCipherBuild.buildAmalgamation(
            srcDir,
            runner,
            buildType
        )
        val cFlags = StringBuilder()
        requiredCFlags.forEach { cFlags.append("$it ") }
        cFlags.append(compilerOptionsString)

        BuilderTask.createPluginFile(srcDir, pluginAppMakeFileName, false) {
            buildString {
                appVars.forEach {
                    append("${it.key} := ${it.value} ${runner.lineSeparator}")
                }
            }
        }

        val opensslModule = "libcrypto"
        val ldFlags = if (r22OrLater)
            "LOCAL_LDFLAGS += -L${Runner.forwardSlash(opensslTargetDir.absolutePath)}"
        else
            "LOCAL_LDFLAGS += -L${Runner.forwardSlash(opensslTargetDir.absolutePath)} ${makeVars["LOCAL_LDFLAGS"]}"

        BuilderTask.createPluginFile(srcDir, pluginMakeFileName, false) {
            """
            LOCAL_PATH := ${'$'}(call my-dir)
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := ${makeVars["LOCAL_MODULE"]}
            LOCAL_C_INCLUDES += ${'$'}(LOCAL_PATH)
            LOCAL_CFLAGS += $cFlags
            LOCAL_SRC_FILES := ${makeVars["LOCAL_SRC_FILES"]}
            $ldFlags
            LOCAL_STATIC_LIBRARIES += $opensslModule
            include ${'$'}(BUILD_SHARED_LIBRARY)
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := $opensslModule
            LOCAL_EXPORT_C_INCLUDES := ${Runner.forwardSlash(opensslIncludeDir.absolutePath)}
            LOCAL_SRC_FILES := ${Runner.forwardSlash(opensslTargetDir.resolve("libcrypto.a").absolutePath)}
            include ${'$'}(PREBUILT_STATIC_LIBRARY)
            """.trimIndent()
        }

        outputDir = srcDir.resolve("libs").resolve(buildType.name)

        val cmd = if (host == HostOs.WINDOWS)
            androidNdkRoot.resolve("ndk-build.cmd").absolutePath
        else
            androidNdkRoot.resolve("ndk-build").absolutePath
        return if (host == HostOs.WINDOWS) {
            runner.executable(WindowsToolExtension.cmdExe) {
                it.workingDir(srcDir)
                it.args("/c", cmd)
                it.args(ndkBuildOptions)
            }
        } else {
            val exportPath = "export PATH=$androidNdkPath:\$PATH"
            val ndkRoot = if (host == HostOs.WINDOWS)
                Runner.getMsysPath(androidNdkRoot)
            else
                androidNdkRoot
            val commandLine = StringBuilder(cmd)
            ndkBuildOptions.forEach { commandLine.append(" $it") }
            val shFilename = BuilderTask.createPluginFile(srcDir, "sqlcipher-${buildType}.sh") {
                """
                #!/bin/sh
                export ANDROID_NDK_ROOT=$ndkRoot
                $exportPath
                $commandLine
                """.trimIndent()
            }
            runner.command(srcDir, "./$shFilename") { }
        }
    }

    companion object {
        const val appMakeFileName = "Application.mk"
        const val makeFileName = "Android.mk"
    }
}