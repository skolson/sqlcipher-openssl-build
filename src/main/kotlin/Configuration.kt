package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import java.io.File

enum class HostOs {
    LINUX, WINDOWS, MAC;

    companion object {
        fun query(): HostOs {
            val target = System.getProperty("os.name").lowercase()
            return if (target.startsWith("linux"))
                LINUX
            else if (target.startsWith("windows"))
                WINDOWS
            else if (target.startsWith("mac"))
                MAC
            else
                throw GradleException("Unsupported OS: $target")
        }
    }
}

class BuildTypes {
    val supportedBuilds = listOf(mingw64, linuxX64, arm64_v8a, x86_64, vStudio64, iosX64, iosArm64, macX64)
    private val androidBuilds = listOf(arm64_v8a, x86_64)
    private val windowsBuilds = listOf(mingw64, vStudio64) + androidBuilds
    private val linuxBuilds = linuxX64 + androidBuilds
    private val macBuilds = listOf(iosX64, iosArm64, macX64)

    val host = HostOs.query()

    fun validate(buildName: String): Boolean {
        return when (host) {
            HostOs.LINUX -> linuxBuilds.contains(buildName)
            HostOs.WINDOWS -> windowsBuilds.contains(buildName)
            HostOs.MAC -> macBuilds.contains(buildName)
        }
    }

    fun targetDirectory(targetsDir: File, buildType: String): File {
        if (!targetsDir.exists()) targetsDir.mkdirs()
        val targetDir = targetsDir.resolve(buildType)
        if (!targetDir.exists()) targetDir.mkdirs()
        return targetDir
    }

    companion object {
        const val mingw64 = "mingw64"
        const val linuxX64 = "linuxX64"
        const val iosX64 = "iosX64"
        const val iosArm64 = "iosArm64"
        const val macX64 = "macX64"
        const val arm64_v8a = "arm64-v8a"
        const val x86_64 = "x86_64"
        const val vStudio64 = "vStudio64"
    }
}

abstract class PluginExtension {
    val buildTypes = BuildTypes()

}

open class SqlcipherExtension: PluginExtension() {

    lateinit var opensslExt: OpensslExtension
        private set
    lateinit var toolsExt: ToolsExtension
        private set
    val builds = mutableListOf<String>()

    var buildSqlCipher = true
    var useGit = false
    var githubUri = defaultGithubUri
    var version = defaultVersion
    val tagName get() = "v$version"
    var compilerOptions = defaultCompilerOptions
    var srcDirectory = srcDir
    var targetsDirectory = targetsDir

    fun builds(vararg targets: String) {
        builds.clear()
        targets.forEach {
            if (!buildTypes.supportedBuilds.contains(it))
                throw GradleException("$it is not a supported target: ${buildTypes.supportedBuilds}")
            if (buildTypes.validate(it))
                builds.add(it)
            else
                Logger.logger.lifecycle("Ignoring buildType: $it on host OS ${buildTypes.host}")
        }
    }

    fun validateOptions() {
        providedOptions.forEach {
            if (compilerOptions.contains(it))
                throw GradleException("SqlCipher requires a specific setting for $it, so do not attempt to specify it.")
        }
        sqlcipherRequiredOptions.forEach {
            if (!compilerOptions.contains(it))
                throw GradleException("SqlCipher builds cannot work without option: $it. See sqlcipherRequiredOptions value")
        }
    }

    companion object {
        const val defaultGithubUri = "https://github.com/sqlcipher/sqlcipher"
        const val defaultVersion = "4.5.0"
        private const val srcDir = "srcSqlCipher"
        const val targetsDir = "targets"

        /**
         * Configure script requires/forces these options, so specifying them causes warnings
         */
        val providedOptions = listOf("SQLITE_THREADSAFE")

        val sqlcipherRequiredOptions = listOf("-DSQLITE_HAS_CODEC","-DSQLCIPHER_CRYPTO_OPENSSL")
        val defaultCompilerOptions = sqlcipherRequiredOptions + listOf(
                "-DSQLITE_OMIT_DEPRECATED",
                "-DSQLITE_OMIT_TRACE",
                "-DSQLITE_OMIT_TCL_VARIABLE",
                "-DSQLITE_OMIT_PROGRESS_CALLBACK",
                "-DSQLITE_DEFAULT_MEMSTATUS=0",
                "-DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1",
                "-DSQLITE_OMIT_SHARED_CACHE",
                "-DSQLITE_MAX_EXPR_DEPTH=0",
                "-DSQLITE_DQS=0",
                "-DSQLITE_DEFAULT_FOREIGN_KEYS=1",
                "-DSQLITE_INTROSPECTION_PRAGMAS",
                "-DSQLITE_USE_ALLOCA")

        val androidCompilerOptions = sqlcipherRequiredOptions + listOf(
                "-DSQLITE_SOUNDEX",
                "-DHAVE_USLEEP=1",
                "-DSQLITE_MAX_VARIABLE_NUMBER=99999",
                "-DSQLITE_TEMP_STORE=3",
                "-DSQLITE_THREADSAFE=1",
                "-DSQLITE_DEFAULT_JOURNAL_SIZE_LIMIT=1048576",
                "-DNDEBUG=1",
                "-DSQLITE_ENABLE_MEMORY_MANAGEMENT=1",
                "-DSQLITE_ENABLE_LOAD_EXTENSION",
                "-DSQLITE_ENABLE_COLUMN_METADATA",
                "-DSQLITE_ENABLE_UNLOCK_NOTIFY",
                "-DSQLITE_ENABLE_RTREE",
                "-DSQLITE_ENABLE_STAT3",
                "-DSQLITE_ENABLE_STAT4",
                "-DSQLITE_ENABLE_JSON1",
                "-DSQLITE_ENABLE_FTS3_PARENTHESIS",
                "-DSQLITE_ENABLE_FTS4",
                "-DSQLITE_ENABLE_FTS5",
                "-DSQLITE_ENABLE_DBSTAT_VTAB")

        fun createExtensions(target: Project): SqlcipherExtension {
            val ext: SqlcipherExtension = target.extensions.create("sqlcipher", SqlcipherExtension::class.java)
            ext.opensslExt = (ext as ExtensionAware).extensions.create("openssl", OpensslExtension::class.java)
            ext.toolsExt = ToolsExtension.createExtensions(ext)
            return ext
        }
    }
}

/**
 * Tools metadata to be set at configuration time using the extensions DSL. Handles multiple platforms.
 *
 * Note - Windows builds can use Visual Studio, mingw, or both in builds. Targets are kept copied into separate
 * target subdirectories.  With windows the OpenSSL build uses perl and a Configure perl script.  Visual Studio
 * builds must use a Windows-oriented perl install for the OpenSSL script to work.  mingw builds on Windows must use
 * a linux-oriented perl for the OpenSSL configure to work.    For Windows-oriented perl, Strawberry has been tested
 * and works fine, ActivePerl should also be fine for use with Visual Studio builds. For mingw builds, MSYS2 perl works.
 */
open class ToolsExtension: PluginExtension() {
    lateinit var android: AndroidToolExtension
        private set
    lateinit var windows: WindowsToolExtension
        private set
    lateinit var ios: IosToolExtension
        private set

    val bashPerl = "perl"

    companion object {
        fun createExtensions(sqlcipher: SqlcipherExtension): ToolsExtension {
            val ext: ToolsExtension = (sqlcipher as ExtensionAware).extensions.create("tools", ToolsExtension::class.java)
            ext.android = (ext as ExtensionAware).extensions.create("android", AndroidToolExtension::class.java)
            ext.ios = (ext as ExtensionAware).extensions.create("ios", IosToolExtension::class.java)
            ext.windows = (ext as ExtensionAware).extensions.create("windows", WindowsToolExtension::class.java)
            return ext
        }
    }
}

open class WindowsToolExtension {
    var msys2InstallDirectory = ""
    val mingwInstallDirectory get() = "$msys2InstallDirectory\\mingw64"
    val msys2UsrBin get() = "${msys2InstallDirectory}\\usr\\bin"
    val msys2Perl get() = "${msys2UsrBin}\\$perlExe"
    val msys2Exec get() = "${msys2UsrBin}\\env.exe"
    val mingwBinPath get() = "${msys2UsrBin};${mingwInstallDirectory}\\bin"

    var visualStudioInstall = defaultVisualStudioInstall
    var sdkInstall = defaultSdkInstall
    var sdkLibVersion = defaultSdkLibVersion
    var perlInstallDirectory = ""

    val vStudioEnvFile get() = File("${visualStudioInstall}${visualStudioConfig}")
    private val windowsPerlDir get() = "${perlInstallDirectory}\\bin"
    val windowsPerl get() = "${windowsPerlDir}\\$perlExe"

    fun verifyMingw64() {
        if (msys2InstallDirectory.isEmpty())
            throw GradleException("msys2InstallDirectory must be specified for build type mingw64")
        val dir = File(mingwInstallDirectory)
        if (!dir.exists())
            throw GradleException("Mingw64 install directory does not exist: $mingwInstallDirectory")
        val bin = dir.resolve("bin")
        if (!bin.exists())
            throw GradleException("MSYS2 Mingw64 bin directory does not exist. Install mingw-w64-x86_64-toolchain in MSYS2")
        val gcc = File(bin, "gcc.exe")
        if (!gcc.exists())
            throw GradleException("MSYS2 Mingw64 bin does not contain gcc.exe. Install mingw-w64-x86_64-toolchain in MSYS2")
        Logger.logger.info("mingw64 install verified, location: $mingwInstallDirectory")
    }

    fun verifyVisualStudio() {
        val vsDir = File(visualStudioInstall)
        if (!vsDir.exists())
            throw GradleException("Visual Studio install directory does not exist: $visualStudioInstall")
        if (!vStudioEnvFile.exists())
            throw GradleException("Visual Studio environment file does not exist: $visualStudioInstall")
        Logger.logger.info("Visual Studio install verified, location: $visualStudioInstall")
        val sdkDir = File(sdkInstall)
        if (!sdkDir.exists())
            throw GradleException("Windows SDK install directory does not exist: $sdkInstall")
        Logger.logger.info("Windows SDK install verified, location: $sdkInstall")
    }

    fun verifyMsys2Perl() {
        if (msys2InstallDirectory.isEmpty())
            throw GradleException("$msys2PerlErrorPrefix Specify msys2InstallDirectory")
        val msys2Dir = File(msys2InstallDirectory)
        if (!msys2Dir.exists())
            throw GradleException("$msys2PerlErrorPrefix msys2InstallDirectory not found: $msys2InstallDirectory")
        val msys2Bin = File(msys2Dir, "usr\\bin")
        if (!msys2Bin.exists())
            throw GradleException("$msys2PerlErrorPrefix msys2InstallDirectory does not contain usr\\bin subdirectory: $msys2InstallDirectory")
        val msys2Perl = File(msys2Bin, perlExe)
        if (!msys2Perl.exists())
            throw GradleException("$msys2PerlErrorPrefix $msys2InstallDirectory\\bin does not contain $perlExe")
    }

    fun verifyWindowsPerl() {
        if (perlInstallDirectory.isEmpty())
            throw GradleException("$perlErrorPrefix Specify windowsPerlInstallDirectory")
        val dir = File(perlInstallDirectory)
        if (!dir.exists())
            throw GradleException("$perlErrorPrefix windowsPerlInstallDirectory not found: $perlInstallDirectory")
        val binDir = dir.resolve("bin")
        if (!binDir.exists())
            throw GradleException("$perlErrorPrefix windowsPerlInstallDirectory does not contain bin subdirectory: $perlInstallDirectory")
        val perlFile = File(binDir, perlExe)
        if (!perlFile.exists())
            throw GradleException("$perlErrorPrefix $perlInstallDirectory\\bin does not contain $perlExe")
    }

    companion object {
        const val defaultVisualStudioInstall = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\"
        const val visualStudioConfig = "Auxiliary\\Build\\vcvars64.bat"
        const val defaultSdkInstall = "C:\\Program Files (x86)\\Windows Kits\\10"
        const val defaultSdkLibVersion = "10.0.18362.0"
        const val perlErrorPrefix = "Use of Visual Studio requires Windows-oriented Perl. "
        const val msys2PerlErrorPrefix = "Use of mingw requires msys2 linux-oriented Perl. "
        const val perlExe = "perl.exe"
        const val cmdExe = "cmd.exe"
    }
}

/**
 * Default NDK version to search for is r21b 21.3.6528147. If setting changes this to empty, then search
 * for latest installed NDK in sdkLocation.  Starting with r22, the required PATH
 * for tools usage simplified.
 */
open class AndroidToolExtension {
    var sdkLocation = ""
    var ndkVersion = "21.3.6528147"
    val r22OrLater: Boolean
        get() {
        if (ndkVersion.isEmpty()) return false
        val ndkTokens = ndkVersion.split(".")
        if (ndkTokens.size != 3 || ndkTokens[0].toIntOrNull() == null)
            throw GradleException("Android NDK version $ndkVersion unsupported format")
        return ndkTokens[0].toInt() >= 22
    }

    val ndkRoot get() = File(sdkLocation).resolve("ndk").resolve(ndkVersion)
    var minimumSdk = 23

    fun determineNdkVersion() {
        val ndkDir = File(sdkLocation, "ndk")
        if (!ndkDir.exists() || !ndkDir.isDirectory)
            throw GradleException("Android NDK installs could not be located in $sdkLocation/ndk")
        if (ndkVersion.isNotEmpty()) {
            val f = File(ndkDir, ndkVersion)
            if (!f.exists() || !f.isDirectory)
                throw GradleException("Android NDK version $ndkVersion could not be located in $sdkLocation/ndk")
        } else {
            var newest = ""
            val dirs = ndkDir.listFiles()
            if (dirs == null || dirs.isEmpty())
                throw GradleException("No Android NDK versions could be located in $sdkLocation/ndk")
            dirs.filter { it.isDirectory }.forEach { if (it.name.compareTo(newest) == 1) newest = it.name }
            ndkVersion = newest
        }
    }
}

open class IosToolExtension {
    var platformsLocation = "/Applications/Xcode.app/Contents/Developer/Platforms"
    var sdkVersion = "14"
    var sdkVersionMinimum = "14"
}

/**
 * OpenSSL options used by the build process.  Source can be downloaded using a git clone, or by
 * zip/tar download and extract.  The appropriate PERL must also be available
 */
open class OpensslExtension {
    var useGit = false
    var githubUri = defaultGithubUri
    var tagName = defaultTagName
    var sourceURI = defaultGithubUriArchive
    var configureOptions = defaultConfigureOptions
    var buildSpecificOptions = emptyMap<String, List<String>>()

    var srcDirectory = openSslSrcDir
    var targetsDirectory = opensslTargetsDir

    val downloadSource get() = "${sourceURI}${tagName}.tar.gz"
    val downloadSourceFileName get() = downloadSource.substring(sourceURI.length)

    companion object {
        const val defaultGithubUri = "https://github.com/openssl/openssl"
        const val defaultGithubUriArchive = "${defaultGithubUri}/archive/"
        const val defaultTagName = "openssl_3.0.1"
        const val openSslSrcDir = "srcOpenssl"
        const val opensslTargetsDir = "targets"
        val defaultConfigureOptions = listOf("no-asm", "no-weak-ssl-ciphers")

        /**
         * Named compile options for OpenSSL that eliminate unused portions of OpenSSL support
         */

        /**
         * This list of options originated in SqlCipher's source build process.
         */
        val nonWindowsOptions = listOf("-fPIC", "-fstack-protector-all")
        val smallConfigureOptions = listOf(
                "no-asm",
                "no-idea", "no-camellia",
                "no-seed", "no-bf", "no-cast", "no-rc2", "no-rc4", "no-rc5", "no-md2",
                "no-md4", "no-ecdh", "no-sock", "no-ssl3",
                "no-dsa", "no-dh", "no-ec", "no-ecdsa", "no-tls1",
                "no-rfc3779", "no-whirlpool", "no-srp",
                "no-mdc2", "no-ecdh", "no-engine", "no-srtp")
        val iosConfigureOptions = listOf(
            "no-dso", "no-async", "no-shared"
        )
        val smallOptionsMap = mapOf(
            "arm64-v8a" to nonWindowsOptions + smallConfigureOptions,
            "x86_64" to nonWindowsOptions + smallConfigureOptions,
            "vStudio64" to smallConfigureOptions,
            "iosArm64" to smallConfigureOptions + iosConfigureOptions
        )

    }
}