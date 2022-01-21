package com.oldguy.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import java.io.File

/**
 * Enum of the supported operating systems running Gradle
 */
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

/**
 * Enum of the supported build types, with attributes useful for determining which hosts support which build types,
 * which platform a build type supports.
 */
enum class BuildType {
    /**
     * Windows only, using the mingw 64-bit toolchain
     */
    mingwX64,

    /**
     * Tested on Ubuntu Linux, should work on many others.
     */
    linuxX64,

    /**
     * IOS Simulator, MacOS hosts only
     */
    iosX64,

    /**
     * IOS for ARM 64-bit, MacOS hosts only
     */
    iosArm64,

    /**
     * Mac OS Big Sur or later, 64 bit Intel/AMD architecture
     */
    macosX64,

    /**
     * Android ARM 64-bit using the Android NDK, supported on all hosts
     */
    androidArm64,

    /**
     * Android Intel/AMD 64-bit using the Android NDK, supported on all hosts. Typically for Android Emulators
     */
    androidX64,

    /**
     * Windows 10 or later Visual Studio Community Edition 2019 toolchain for Intel/AMD 64 bit windows hosts only.
     */
    vStudio64;

    val host = HostOs.query()
    val isAndroid = ordinal == 5 || ordinal == 6
    val isIos = ordinal == 2 || ordinal == 3
    val isMacOs = isIos || isAndroid || ordinal == 4
    val isWindowsOnly = when (ordinal) { 0, 7 -> true else -> false }
    val isWindows = isWindowsOnly || isAndroid
    val isLinux = when (ordinal) { 1, 5, 6 -> true else -> false }
    val isThisHost = when (host) {
        HostOs.LINUX -> isLinux
        HostOs.WINDOWS -> isWindows
        HostOs.MAC -> isMacOs
    }

    /**
     * Verifies that the subdirectory (matching the enum value name) of targetsDir for this BuildType exists, makes it
     * if not, then returns it.
     * If targetsDir does not exist, it is created
     */
    fun targetDirectory(targetsDir: File): File {
        if (!targetsDir.exists()) targetsDir.mkdirs()
        val targetDir = targetsDir.resolve(name)
        if (!targetDir.exists()) targetDir.mkdirs()
        return targetDir
    }

    companion object {
        /**
         * Convenience value for build scripts that want to limit builds by host
         */
        val appleBuildTypes = values().filter { it.isMacOs || it.isIos || it.isAndroid }
        /**
         * Convenience value for build scripts that want to limit builds by host
         */
        val windowsBuildTypes = values().filter { it.isWindows }
        /**
         * Convenience value for build scripts that want to limit builds by host
         */
        val linuxBuildTypes = values().filter { it.isLinux }
    }
}

/**
 * Gradle extension for DSL configuration of the plugin. Contains an extension specific to OpenSSSL, one containing
 * tools configuration for the supported tool chains, and the configurable options that can be set by the DSL.
 */
open class SqlcipherExtension {

    lateinit var opensslExt: OpensslExtension
        private set
    lateinit var toolsExt: ToolsExtension
        private set
    val builds = mutableListOf<BuildType>()

    var buildSqlCipher = true
    var useGit = false
    var githubUri = defaultGithubUri
    var version = defaultVersion
    val tagName get() = "v$version"
    var compilerOptions = defaultCompilerOptions

    /**
     * Map of additional options to be passed to the C compiler, keyed by build type. Any found for a specific
     * build type will be added after the options specified in [compilerOptions]. Entries for an unsupported build type
     * will cause an exception. Entries for build types not specified in [builds] are ignored.
     */
    var buildCompilerOptions = emptyMap<BuildType, List<String>>()
    var srcDirectory = srcDir
    var targetsDirectory = targetsDir

    /**
     * Implementations should examine the build type, and return a directory to receive a copy of the targets from the
     * build folder after build is complete. If no implementation is provided, no copies are done and the targets will
     * remain only in the build folder.
     * argument buildType - specifies the buildType whose targets are available to copy
     * @returns File which can be null for no copy desired, or a directory into which target contents in build folder for
     * this buildType should be copied.
     */
    var targetsCopyTo: ((buildType: BuildType) -> File?)? = null
    var copyCinteropIncludes = false

    @Suppress("MemberVisibilityCanBePrivate")
    fun builds(targets: List<BuildType>) {
        builds.clear()
        targets.forEach {
            if (it.isThisHost)
                builds.add(it)
            else
                Logger.logger.lifecycle("Ignoring buildType: ${it.name} on host OS ${it.host}")
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun builds(vararg targets: BuildType) {
        builds.clear()
        targets.forEach {
            if (it.isThisHost)
                builds.add(it)
            else
                Logger.logger.lifecycle("Ignoring buildType: ${it.name} on host OS ${it.host}")
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun builds(vararg targets: String) {
        builds.clear()
        targets.forEach {
            try {
                BuildType.valueOf(it).also {
                    if (it.isThisHost)
                        builds.add(it)
                    else
                        Logger.logger.lifecycle("Ignoring buildType: ${it.name} on host OS ${it.host}")
                }
            } catch (exc: IllegalArgumentException) {
                throw GradleException("Invalid BuildType string: $it")
            }
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
        const val targetsDir = "sqlCipherTargets"
        const val moduleName = "sqlite3"
        const val moduleNameH = "$moduleName.h"
        const val amalgamation = "$moduleName.c"

        /**
         * Configure script requires/forces these options, so specifying them causes warnings
         */
        val providedOptions = listOf("SQLITE_THREADSAFE")

        val sqlcipherRequiredOptions = listOf("-DSQLITE_HAS_CODEC","-DSQLCIPHER_CRYPTO_OPENSSL")
        val defaultCompilerOptions = sqlcipherRequiredOptions + listOf(
            "-DNDEBUG=1",
            "-DSQLITE_OMIT_DEPRECATED",
            "-DSQLITE_OMIT_TRACE",
            "-DSQLITE_OMIT_TCL_VARIABLE",
            "-DSQLITE_OMIT_PROGRESS_CALLBACK",
            "-DSQLITE_DEFAULT_MEMSTATUS=0",
            "-DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1",
            "-DSQLITE_OMIT_SHARED_CACHE",
            "-DSQLITE_ENABLE_COLUMN_METADATA",
            "-DSQLITE_MAX_EXPR_DEPTH=0",
            "-DSQLITE_DQS=0",
            "-DSQLITE_DEFAULT_FOREIGN_KEYS=1",
            "-DSQLITE_ENABLE_RTREE",
            "-DSQLITE_ENABLE_STAT3",
            "-DSQLITE_ENABLE_STAT4",
            "-DSQLITE_ENABLE_FTS3_PARENTHESIS",
            "-DSQLITE_ENABLE_FTS4",
            "-DSQLITE_ENABLE_FTS5",
            "-DSQLITE_INTROSPECTION_PRAGMAS"
        )

        val androidCompilerOptions = listOf(
            "-DSQLITE_SOUNDEX",
            "-DHAVE_USLEEP=1",
            "-DSQLITE_MAX_VARIABLE_NUMBER=99999",
            "-DSQLITE_TEMP_STORE=3",
            "-DSQLITE_DEFAULT_JOURNAL_SIZE_LIMIT=1048576",
            "-DSQLITE_ENABLE_MEMORY_MANAGEMENT=1",
            "-DSQLITE_ENABLE_UNLOCK_NOTIFY",
            "-DSQLITE_ENABLE_DBSTAT_VTAB",
            "-DSQLITE_OMIT_AUTORESET",
            "-DSQLITE_OMIT_BUILTIN_TEST",
            "-DSQLITE_OMIT_LOAD_EXTENSION"
        )

        val appleCompilerOptions = listOf(
            "-fno-common",
            "-DSQLITE_ENABLE_API_ARMOR",
            "-DSQLITE_ENABLE_UPDATE_DELETE_LIMIT",
            "-DSQLITE_OMIT_AUTORESET",
            "-DSQLITE_OMIT_BUILTIN_TEST",
            "-DSQLITE_OMIT_LOAD_EXTENSION",
            "-DSQLITE_SYSTEM_MALLOC",
            "-DSQLITE_THREADSAFE=2",
            "-DSQLITE_OS_UNIX=1"
        )

        /**
         * The following is to avoid a warning as a Sqlite work-around specific to MACs and people using NFS home drives.
         */
        val macOsCompilerOptions = appleCompilerOptions + listOf(
            "-DSQLITE_ENABLE_LOCKING_STYLE=1"
        )

        val iosCompilerOptions = appleCompilerOptions + listOf(
            "-DSQLITE_MAX_MMAP_SIZE=0",
            "-DSQLITE_ENABLE_LOCKING_STYLE=0",
            "-DSQLITE_TEMP_STORE=3",
            "-fembed-bitcode",
            "-Wno-#warnings"
        )

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
open class ToolsExtension {
    lateinit var android: AndroidToolExtension
        private set
    lateinit var windows: WindowsToolExtension
        private set
    lateinit var apple: AppleToolExtension
        private set

    val bashPerl = "perl"

    companion object {
        fun createExtensions(sqlcipher: SqlcipherExtension): ToolsExtension {
            val ext: ToolsExtension = (sqlcipher as ExtensionAware).extensions.create("tools", ToolsExtension::class.java)
            ext.android = (ext as ExtensionAware).extensions.create("android", AndroidToolExtension::class.java)
            ext.apple = (ext as ExtensionAware).extensions.create("apple", AppleToolExtension::class.java)
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
        @Suppress("MemberVisibilityCanBePrivate")
        if (msys2InstallDirectory.isEmpty())
            throw GradleException("msys2InstallDirectory must be specified for build type ${BuildType.mingwX64}")
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
    var windowsSdkLocation = ""
    var linuxSdkLocation = ""
    var macosSdkLocation = ""
    val sdkLocation get() = when (HostOs.query()) {
        HostOs.LINUX -> linuxSdkLocation
        HostOs.WINDOWS -> windowsSdkLocation
        HostOs.MAC -> macosSdkLocation
    }
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

open class AppleToolExtension {
    var platformsLocation = "/Applications/Xcode.app/Contents/Developer"
    var sdkVersion = "15"
    var sdkVersionMinimum = "14"
    var platforms = mapOf(
        BuildType.iosX64 to "iPhoneSimulator",
        BuildType.iosArm64 to "iPhoneOS",
        BuildType.macosX64 to "MacOSX"
    )

    private fun platform(buildType: BuildType): String {
        if (!buildType.isMacOs)
            throw GradleException("Bug: only invoke for MacOS build types. buildType: $buildType")
        return platforms[buildType]
            ?: throw GradleException("Bug: invalid IOS buildType: $buildType")
    }

    private fun crossPath(buildType: BuildType): String {
        return "$platformsLocation/Platforms/${platform(buildType)}.platform/Developer"
    }

    val toolChainPath: String get() = "$platformsLocation/Toolchains/XcodeDefault.xctoolchain/usr/bin"

    /**
     * for the specified build type, return a list of compiler options required - not configurable. All Apple
     * builds require -isysroot pointing to the location of the SDK to use. All iphone builds require specifying the
     * minimum version of the SDK.
     */
    fun options(buildType: BuildType): List<String> {
        val all = listOf("-isysroot ${crossPath(buildType)}/SDKs/${platform(buildType)}.sdk")
        return if (buildType == BuildType.macosX64)
            all
        else
            all + listOf("-miphoneos-version-min=$sdkVersionMinimum")
    }

    fun optionsString(buildType: BuildType): String {
        return buildString { options(buildType).forEach { append("$it ") } }
    }
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
    var buildSpecificOptions = buildOptionsMap

    var srcDirectory = openSslSrcDir
    var targetsDirectory = opensslTargetsDir

    val downloadSource get() = "${sourceURI}${tagName}.tar.gz"
    val downloadSourceFileName get() = downloadSource.substring(sourceURI.length)

    companion object {
        const val defaultGithubUri = "https://github.com/openssl/openssl"
        const val defaultGithubUriArchive = "${defaultGithubUri}/archive/"
        const val defaultTagName = "openssl_3.0.1"
        const val openSslSrcDir = "srcOpenssl"
        const val opensslTargetsDir = SqlcipherExtension.targetsDir
        val defaultConfigureOptions = listOf("no-asm", "no-weak-ssl-ciphers")

        /**
         * Named compile options for OpenSSL that eliminate unused portions of OpenSSL support
         */

        /**
         * This list of options originated in SqlCipher's source build process.
         */
        val androidOptions = listOf("-fPIC", "-fstack-protector-all")
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
        val buildOptionsMap = mapOf(
            BuildType.androidArm64 to androidOptions,
            BuildType.androidX64 to androidOptions,
            BuildType.linuxX64 to iosConfigureOptions,
            BuildType.iosArm64 to iosConfigureOptions,
            BuildType.iosX64 to iosConfigureOptions,
            BuildType.macosX64 to iosConfigureOptions
        )
    }
}