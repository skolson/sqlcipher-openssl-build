package com.oldguy.gradle

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.io.File

abstract class BuilderTask(val buildType: String): DefaultTask() {
    abstract val buildName: String
    private val groupName = "build"
    val host = HostOs.query()
    val windowsPatterns = listOf("*.lib", "*.dll", "*.exe")
    val mingwPatterns = listOf("*.a", "*.dll", "*.pc", "*.rc", "*.def", "*.o")
    val linuxPatterns = listOf("sqlcipher", "*.a", "*.so", "*.pc", "*.map", "*.so.*")
    val make = "make"
    val defaultFileName get() = "${buildName}-${buildType}"

    @get:Input
    abstract val msys2UsrBin: Property<String>

    @get:Input
    abstract val mingwInstallDirectory: Property<String>

    @get:Input
    abstract val vStudioEnvFilePath: Property<String>

    @get:Input
    abstract val androidMinimumSdk: Property<Int>

    @get:Input
    abstract val androidNdkRoot: Property<File>
    val androidNdkPath get() = androidNdkPath(host, buildType)

    lateinit var runner: Runner
    lateinit var windows: WindowsToolExtension

    init {
        group = groupName
    }

    private val ndkToolsDir get() = when (host) {
        HostOs.WINDOWS -> "windows-x86_64"
        HostOs.LINUX -> "linux-x86_64"
        HostOs.MAC -> "darwin-x86_64"
    }

    private val androidPrebuilt get() = androidNdkRoot.get()
            .resolve("toolchains")
            .resolve("llvm")
            .resolve("prebuilt")
            .resolve(ndkToolsDir)

    private fun androidNdkPath(host: HostOs, buildType: String, msys: Boolean = true): String {
        val t = when (buildType) {
            BuildTypes.arm64_v8a -> "aarch64-linux-android"
            BuildTypes.x86_64 -> "x86_64-linux-android"
            else -> throw GradleException("Unsupported Android buildType: $buildType")
        }
        return when (host) {
            HostOs.WINDOWS -> {
                if (msys) {
                    Runner.getMsysPath(androidPrebuilt.resolve("bin")) + ":" +
                            Runner.getMsysPath(androidPrebuilt.resolve(t).resolve("bin"))
                } else {
                    androidPrebuilt.resolve("bin").absolutePath + ";" +
                            androidPrebuilt.resolve(t).resolve("bin").absolutePath
                }
            }
            HostOs.MAC -> {
                androidPrebuilt.resolve("bin").absolutePath + ":" +
                        androidPrebuilt.resolve(t).resolve("bin").absolutePath
            }
            HostOs.LINUX -> {
                androidPrebuilt.resolve("bin").absolutePath + ":" +
                        androidPrebuilt.resolve(t).resolve("bin").absolutePath
            }
        }
    }

    fun setToolsProperties(tools: ToolsExtension) {
        this.windows = tools.windows
        msys2UsrBin.set(tools.windows.msys2UsrBin)
        mingwInstallDirectory.set(tools.windows.mingwInstallDirectory)
        vStudioEnvFilePath.set(tools.windows.vStudioEnvFile.absolutePath)
        androidMinimumSdk.set(tools.android.minimumSdk)
        androidNdkRoot.set(tools.android.ndkRoot)
        runner = Runner(project, host, windows)
    }

    fun listToString(list: List<String>): String {
        val s = StringBuilder()
        list.forEach { s.append("$it ") }
        return s.toString()
    }

    companion object {
        private const val shellPrefix = "Plugin-"

        fun pluginFileName(fileName: String): String {
            return "$shellPrefix$fileName"
        }

        fun createPluginFile(dir: File, fileName: String, addPrefix:Boolean = true, appendText:() -> String): String {
            val fullFileName = if (addPrefix) pluginFileName((fileName)) else fileName
            val shFile = File(dir, fullFileName)
            if (shFile.exists()) shFile.delete()
            shFile.appendText(appendText())
            shFile.setExecutable(true)
            return fullFileName
        }
    }
}