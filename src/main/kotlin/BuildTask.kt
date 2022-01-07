package com.oldguy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File

abstract class BuilderTask(@get:Input val buildType: String): DefaultTask() {
    private val groupName = "build"

    @get:Input
    abstract val buildName: String

    @get:Input
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
    abstract val platformsLocation: Property<String>

    @get:Input
    abstract val iosMinimumSdk: Property<String>

    @get:Input
    abstract val iosSdk: Property<String>

    @get:Input
    abstract val androidNdkRoot: Property<File>

    @get:Input
    abstract var r22OrLater: Boolean

    @get:Input
    val androidNdkPath get() = androidNdkPath(host, buildType, r22OrLater)

    @Internal
    val host = HostOs.query()
    @Internal
    val windowsPatterns = listOf("*.lib", "*.dll", "*.exe")
    @Internal
    val mingwPatterns = listOf("*.a", "*.dll", "*.pc", "*.rc", "*.def", "*.o")
    @Internal
    val linuxPatterns = listOf("sqlcipher", "*.a", "*.so", "*.pc", "*.map", "*.so.*")
    @Internal
    val applePatterns = listOf("sqlcipher", "*.a", "*.pc")
    @Internal
    val make = "make"
    @Internal
    lateinit var runner: Runner
    @Internal
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

    private fun androidNdkPath(host: HostOs, buildType: String, r22OrLater: Boolean, msys: Boolean = true): String {
        val t = when (buildType) {
            BuildTypes.arm64_v8a -> "aarch64-linux-android"
            BuildTypes.x86_64 -> "x86_64-linux-android"
            else -> return ""
        }
        val sep = if (host == HostOs.WINDOWS && !msys) ";" else ":"
        val extraPathEntry = if (r22OrLater) ""
        else
            sep + Runner.getMsysPath(androidPrebuilt.resolve(t).resolve("bin"))
        val absPath = androidPrebuilt.resolve("bin").absolutePath + extraPathEntry
        return when (host) {
            HostOs.WINDOWS -> {
                if (msys) {
                    Runner.getMsysPath(androidPrebuilt.resolve("bin")) + extraPathEntry
                } else {
                    absPath
                }
            }
            HostOs.LINUX,
            HostOs.MAC -> {
                absPath
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
        iosMinimumSdk.set(tools.ios.sdkVersionMinimum)
        platformsLocation.set(tools.ios.platformsLocation)
        iosSdk.set(tools.ios.sdkVersion)
        r22OrLater = tools.android.r22OrLater
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