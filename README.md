## GradleSqlCipher

GradleSqlCipher is a Gradle plugin that builds OpenSSL and Zetetic's SqlCipher libraries and related executables, supporting a variety of build environments. View this plugin as essentially an ease-of-use wrapper on top of the configuration scripts and make files both packages already supply.

This plugin is very early in its lifecycle. There is **lots** of opportunity for more documentation, enhancements in flexbility, etc. Suggestions for documentation, bugs and enhancements are always welcome. Use this Github project's New Issue for these. 

At a high level, this is current status and near-term plans for the plugin. See below for details/
- 64 bit builds only on all platforms. No current plans for 32 bit builds
- Windows builds are working
    - Visual Studio 2019 Community Edition
    - Mingw64 using MSYS2 and a variety of installed MSYS2 packages
    - Android 64 bit (arm64_v8a, x86_64) builds using NDK 21.3.6528147 (r20b) 
    - Android 64 bit (arm64_v8a, x86_64) builds using NDK 22.1.7171670 (r21b)
    - Openssl 1.1.k and earlier has an issue with android NDK r22 and later. See [OpenSSL Github pull 13694](https://github.com/openssl/openssl/pull/13694). The current 3.0.0 beta has the required fix. So to use the newer NDK versions, use 3.x of openssl. The fix is also backported and merged, so should be available in 1.1.1l or later. 
    - Android 64 bit (arm64_v8a, x86_64) builds using NDK 23.0.7599858 (r23)
- Linux builds on Ubuntu are working
    - linuxX64 using gcc toolchain
    - Android 64 bit (arm64_v8a, x86_64) builds using NDK 21.3.6528147 (r20b)
- MacOS builds pending
    - ios64
- Published in the Gradle plugin repository under name `sqlcipher-openssl-build` 
- Android configuration - easy add of extra source files (JNI wrappers) to standard library  

#### Tested Version Combinations
The plugin successfully performs 64 bit builds of these combinations.  Others may work. Note that the Sqlite version is listed only for information, it is determined by the SqlCipher version.

| OpenSSL version   | SqlCipher version     | Sqlite Version        |
|-------------------|-----------------------|-----------------------
| 1.1.g             | 4.4.0                 | 3.31.0                |
| 1.1.h             | 4.4.0                 | 3.31.0                |
| 1.1.h             | 4.4.1                 | 3.33.0                |
| 1.1.h             | 4.4.2                 | 3.33.0                |
| 3.0.0 beta2       | 4.4.3                 | 3.34.1                |

## OpenSSL
*From the OpenSSL site:*

OpenSSL is a robust, commercial-grade, full-featured Open Source Toolkit for the Transport Layer Security (TLS) protocol formerly known as the Secure Sockets Layer (SSL) protocol. The protocol implementation is based on a full-strength general purpose cryptographic library, which can also be used stand-alone.

OpenSSL is descended from the SSLeay library developed by Eric A. Young and Tim J. Hudson.

The official Home Page of the OpenSSL Project is www.openssl.org. Github repository:
[OpenSSL Github](https://github.com/openssl/openssl) 

OpenSSL builds are set up using a Perl Configure script that generates make-related artifacts.  This plugin uses its configuration to correctly run the Perl script, then perform the make process. Any errors cause a GradleException and the plugin stops.  

## SqlCipher
*From the SqlCipher site:*

SQLCipher extends the [SQLite](https://www.sqlite.org) database library to add security enhancements that make it more suitable for encrypted local data storage like:

- on-the-fly encryption
- tamper detection
- memory sanitization
- strong key derivation

SQLCipher is based on SQLite and stable upstream release features are periodically integrated. 

SQLCipher is maintained by Zetetic, LLC, and additional information and documentation is available on the official [SQLCipher site](https://www.zetetic.net/sqlcipher/). Github repository:
                                                                                                                                                                         [SqlCipher Github](https://github.com/sqlcipher/sqlcipher) 
## Reason for Existence
There is some complexity involved with correctly building OpenSSL and SqlCipher on various platforms, especially Windows. The long-term goals of this plugin are:
- automate the build process for these platforms and tool chains:
    - Windows 
        - Visual Studio  
        - mingw64
        - Android NDK arm64_v8a, x86_64
    - Linux
        - gcc
        - Android NDK arm64_v8a, x86_64
    - MacOS
        - ios64
        - MacOS

- OpenSSL and Sqlite both offer a rich set of compile options. This plugin will offer some standard option sets for common requirements that are easily selectable and still customizable.
- Provide additional documentation on platform-specific build details, with initial focus on Windows host builds. 

## Environments 
This plugin has been tested with Gradle 7.0.2 and 6.6.1 so far. Older V6.x versions of Gradle should work, but are untested.  Host operating systems tested so far include Windows 10, Ubuntu 14.04, and in near future Mac OS Catalina. Plugin source is Kotlin 1.4.0. As of September 2020 only current versions of SqlCipher (4.4.0) and OpenSSL (1.1.1g, 1.1.1h) have been tested. 

Windows support is included for three tool chains, each with its own requirements. Windows builds have to be run on a Windows host:
- Visual Studio 2019 Community Edition
    - OpenSSL requires Windows-oriented perl support for build configuration. Either of these works fine: 
        - [Strawberry Perl 5.32](http://strawberryperl.com/) 
        - [Active Perl 5.28](https://www.activestate.com/products/perl/downloads/)
    - OpenSSL requires Assembler support
        - [NASM 2.15.05](https://www.nasm.us/)
    - Windows SDK 10.0.18362.1 or later
        - [Windows 10 SDK Installer](https://developer.microsoft.com/en-us/windows/downloads/windows-10-sdk/)   
- Mingw64 toolchain - build process requires mingw64, make, and some other tools installed that are required during the make process for SqlCipher and for OpenSSL. 
    - [MSYS2](https://www.msys2.org/). Once installed, run these from an MSYS2 prompt:
        - pacman -S mingw-w64-x86_64-toolchain
        - pacman -S make
        - pacman -S mingw-w64-x86_64-cmake
        - pacman -S diffutils
    - OpenSSL requires Assembler support
        - [NASM 2.15.05](https://www.nasm.us/)
    - **Note** - OpenSSL requires linux-oriented perl support, which is provided by the base MSYS2 install
    - **Note** - to avoid potential path issues, **do not** install MSYS2 in a directory that contains any embedded blanks anywhere in the absolute path (i.e.: "c:\Program Files\msys2" is bad).
- CLang is not currently supported but could be easily added if there is interest.
- Android NDK
    - Android Studio 4.0 or later, use SDK manager to install current versions:
        - NDK (side-by-side) version r20b tested
        - CMake

Linux support requires the following packages on Ubuntu.  These may need adjustment on other distros:
- to do
   
Mac OS support requires        
- to do

## Features
- Current support is for 64-bit builds only on all platforms.
- Simple DSL for setting flexible configuration options 
- Support for these build types:
    - vStudio64
    - mingw64
    - linuxX64
    - ios64
    - Android NDK builds
        - arm64_v8a
        - x86_64
- one or more of these can be specified. Build types declared that cannot be run on the current Host OS will be skipped.
- Source for OpenSSL and Sqlcipher can be obtained one of two ways:
    - Git clone and checkout of a specific tag (version). This is by far the slowest of the two options, but creates a usable git local checkout.
    - HTTPS download of zip or tarball for a specific tag/version
- Gradle tasks are registered for each build type, one set for OpenSSL, one set for SqlCipher:
    - task names are of the format <openssl|sqlcipher><Name><build type>
        - For example sqlcipherBuildvStudio64 will verify/download/build SqlCipher and OpenSSL as required
    - Verify checks the locations of the required tools to be sure they are present and usable.
    - Clone is an optional task, will clone and check out a specific tag name. Depends on Verify
    - Download is an optional task, will download a zip/tarball for the specified tag, usually from a Github archive URL. Depends on Verify
    - Extract is an optional task that depends on Download
    - Build depends on Clone and Extract.  Performs the build process. Once build is complete, copies the produced targets to a specified location for targets, in a subdirectory specific to build type.
        - For example, sqlcipher build for buildType mingw64 defaults to `build/targets/sqlcipher/mingw64` as the subdirectory containing the built artifacts  
        - the directory with the build artifacts is the single output in that task's gradle task.outputs  
    - see the **DSL Reference** section for details on tasks that can be registered.
    
## Getting Started

The samples below use the kotlin gradle DSL syntax, but configurations is currently just simple string and boolean assignments, so should be easily groovy-ized as well.  The kotlin default options use kotlin specific listOf() so the using the list-of-strings constants may be problematic in groovy.

Use the plugins DSL in build.gradle.kts to declare enable the plugin:
```
    plugins {
            ...
            id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.1.1"
    }
```

The DSL to configure the plugin for a windows hosted build producing Visual Studio, MingW, Android ARM, and Android x86 64 bit targets might look like this as an example:
```
    import com.oldguy.gradle.SqlcipherExtension // only needed if using any of the constants available with the extension
    
    ...

    sqlcipher {
        useGit = false
        version = "4.4.3"
        compilerOptions = SqlcipherExtension.defaultCompilerOptions +
            "-DSQLITE_LIKE_DOESNT_MATCH_BLOBS"

        builds("vStudio64", "mingw64", "arm64-v8a", "x86_64", "linuxX64")
        
        tools {
            windows {
                msys2InstallDirectory = "D:\\msys64"
                sdkInstall = "D:\\Program Files (x86)\\Windows Kits\\10"
                sdkLibVersion = "10.0.18362.0"
                visualStudioInstall = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\"
                perlInstallDirectory = "D:\\SqlCipher\\Strawberry\\perl"
            }
            android {
                sdkLocation = "D:\\Android\\sdk"
                ndkVersion = "22.1.7171670"
                minimumSdk = 23
            }
        }
        openssl {
            tagName = "OpenSSL_1_1_1k"
            useGit = false
        }
    }

    ...
```

Using this configuration, running Gradle task `sqlcipherBuildAll` will run all the tasks required to perform the designated builds.

In the above DSL, SqlCipher 4.4.0 would be built using a source archive (useGit = false) from Github (.zip if running Gradle on windows, .tar.gz if not). The **builds(...)** function indicates Visual Studio 64 bit, MingW64, and two android builds would be performed. These would happen only if gradle is being run on a Windows host. If gradle is being run on a linux host, then only the two android and one linuxX64 builds run. All targets produced would reside in the project buildDir/targets directory, each buildType having its own subdirectory. the project buildDir directory will also contain "srcOpenssl" and one "srcSqlcipher" subdirectory each for OpenSSL and SqlCipher. Each of there will contain one subdirectory for each configured build type, containing source and all respective build artifacts.  

The sqlcipher compilerOptions are derived from a default list with one additional custom option in this example.  Any kotlin expression that evaluates to a List<String> is valid.

compilerOptions assignment above designates a set of default options, plus one addition. See the Reference section below for details.  

**Note** - The build process is kinda long for OpenSSL and has to happen for each target. On a decent but not high end build machine OpenSSL for mingw64 takes about 5 minutes to build. Linux OpenSSL builds are significantly faster. SqlCipher builds much quicker, but patience is required when running lots of target builds in one Gradle run. 

##Reference

**Vocabulary**
- Punt - indicates throwing of a GradleException, and the task terminates
- Slow - indicates build task can take a relatively long time (multiple minutes)

**Notes**

Most all of the build activities create small text files in support of the build process.  They can be a shell script, a bat file, or some flavor of make file.  The builds use existing make files and other artifacts with no changes, these additional files are used "on top" so to speak of the existing stuff.  These files are created in the respective source directories, used there and left there with the intent that anyone curious about the build details can examine/change/run these files manually.  Thay are all located at the root of the respective source directory, and all names start with the literal `"Plugin-"`

All of the builds rely on the configurations in the DSL, and create a set of Gradle tasks that perform the various steps required.  Below is a list of all of these tasks, followed by the reference information for DSL configuration.

The current version of the plugin only supports 64 bit tools and host OSs. Build tasks are provided only for building 64 bit targets using these tools. 

  
**Registered Tasks**

Available Build Types are listed here. Gradle tasks registered are based on these as selected in the DSL. As is typical with Gradle, ANY of the build processes invoked by the plugin cause the plugin to punt. The plugin copies console output from the build processes to the gradle console. Currently this is done ate process completion.   

|**Build Type** | Supporting Hosts  | Description |
|---------------|-------------------|--------------------------------|
| vstudio64     | Windows           | Visual Studio Community Edition 2019 is used as the compiler/linker and produces ,lib files and .dll files (and other file types) usable with any visual studio project. Older versions of Visual Studio may work but are untested. |
| mingw64       | Windows           | MSYS2 and MINGW64 toolchains are used to produce mingw static and shared libs on a Windows host |
| arm64-v8a     | Windows, Linux    | Uses the Android NDK configured in **tools** DSL to build 64 bit ARM static and shared libraries |
| x86_64        | Windows, Linux    | Uses the Android NDK configured in **tools** DSL to build 64 bit Intel/AMD static and shared libraries |
| linuxX64       | Linux             | Standard GCC toolchain is used to produce static and shared libraries and the command line utility for sqlcipher. Initially tested on Ubuntu |
| ios64         | Mac OS            | To be added later |


Configuration DSL specifies the build types to be performed. Task names specific to a build type contain the build type in the name.  In the names below for these tasks, the build type is abbreviated to *BT*. Note that in the case of SqlCipher, no build-specific Verify tasks are needed, as it uses the same set of tools used by OpenSSL builds, which handles the verifications.   

| Task Name         | Description |
|-------------------|--------------------------------------------|
| opensslBuildAll   | Top level task, easy way to build only openssl configured builds |
| sqlcipherBuildAll | Top level task, easy way to build sqlcipher configured builds. Note since sqlcipher build tasks depend on their openssl counterparts, this will also build openssl. |
| opensslVerify     | Uses the configuration information in the **tools** DSL to verify the install of a usable PERL and NASM. |
| opensslVerify*BT* | Each build type verifies the existence of the build tools configured in the **tools** DSL that are required for that build type |
| opensslGit        | This optional task is one of two options for retrieving source for OpenSSL.  The source produced by this task is a single tag only clone and checkout from the Github repo configured in the DSL, which defaults to [OpenSSL Github](https://github.com/openssl/openssl) |
| opensslDownload   | This optional task is one of two options for retrieving source for OpenSSL. It will do an HTTPS download of the .zip (for windows hosts) or .tar.gz archives, typically from the same Github site archive for the specified tag/release |
| opensslExtract*BT*| Performs the Extract All operation of the output from opensslDownload. |
| opensslBuild*BT*  | Performs the build specific to the buildType. One of these is registered for each of the buildTypes configured in the **builds** function in the DSL that are supported by the host machine on which Gradle is running. These builds are not fast, on a decent but not great workstation they take close to 5 minutes each. Patience is a virtue :-).|
| sqlcipherGit      | This optional task is one of two options for retrieving source for SqlCipher.  The source produced by this task is a single tag only clone and checkout from the Github repo configured in the DSL, which defaults to [SqlCipher Github](https://github.com/sqlcipher/sqlcipher) |
| sqlcipherDownload | This optional task is one of two options for retrieving source for OpenSSL. It will do an HTTPS download of the .zip (for windows hosts) or .tar.gz archives, typically from the same Github site archive for the specified tag/release |
| sqlcipherExtract*BT*| Performs the Extract All operation of the output from opensslDownload. |
| sqlcipherBuild*BT*| Performs the build specific to the buildType. One of these is registered for each of the buildTypes configured in the **builds** function in the DSL that are supported by the host machine on which Gradle is running. |
| opensslClean      | Deletes source directory and contents, as well as targetsDirectory and contents, for the OpenSSL version specified in the DSL. Also deletes source archive (zip or tar) if there is one.
| sqlcipherClean    | Deletes source directory and contents, as well as targetsDirectory and contents, for SqlCipher and the taName specified in the DSL. Also deletes source archive (zip or tar) if there is one.
   
###DSL Reference ###

The DSL block `sqlcipher { ... }` contains options specific to SqlCipher builds, and contains subsection `tool { ... }` for configuring tools used by the build tasks, and subsection `openssl { ... }` for OpenSSL specific build options. So a typical configuration block in build.gradle.kts is structured like this:
```
sqlcipher {
    builds( "mingw64", "linuxX64", "ios64", "arm64-v8a", "x86_64", vStudio64" )  // or whatever subset desired
    ... sqlcipher options ...

    openssl {
        ... openssl options ...
    }

    tools {
        ... installation locations required for the tools used ...
        windows {
            ... optional, windows-specific configuration
        }
        android {
            ... optional, android-specific configuration
        }
    }
}
``` 

Since both openssl and sqlcipher sections rely on tools, the tools section is described first, then openssl, finally sqlcipher.

Each of these configuration sections is a Gradle Extension class. the Extension classes are listed below.  Some have constant values that are available for use in the DSL expressions if desired.

| Extension Class Name  | Description |
|-----------------------|----------------------------------------------------|
| SqlcipherExtension    | Configuration options for SqlCipher. The other extensions are properties of this extension |
| OpensslExtension      | Configuration options for OpenSSL builds. |
| ToolsExtension        | Mostly options indicating where tools are located. Owns platform-specific subsections. |
| WindowsToolExtension  | Locations of windows-specific tools. Property of ToolsExtension 
| AndroidToolExtension  | Android-specific configuration for the SDK and NDK to be used. Property of ToolsExtension | 

#### tools DSL subsection #####

Each option in the `tools` block is below.  The type indicates whether the option is a **value** that can be assigned, a **constant** that cannot be assigned but is available for use in assignments to values, or a **function** that can be called. Note that the options here are all paths specific to the host in use.  Windows likes backslashes, which in kotlin KTS files require escaping. The `tools` block has platform specific subsections; `windows`, `android`.  These subsections are required only if using build types for those platforms. 

| Name                      | Type      | Description |
|---------------------------|-----------|-----------------------------------------|
| **windows** settings      |
| visualStudioInstall       | value     | Specify the absolute path to the directory where Visual Studio is installed. See 'defaultVisualStudioInstall' for the default value used if this is not specified, it is an example of a typical value. |
| defaultVisualStudioInstall| constant  | `"C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\"` |  
| sdkInstall                | value     | Specify the absolute path to the directory where Windows SDK is installed. See 'defaultWindowsSdkInstall' for the default value used if this is not specified, it is an example of a typical value. |
| defaultSdkInstall         | constant  | `"C:\\Program Files (x86)\\Windows Kits\\10"` |
| sdkLibVersion             | value     | String value matching a subdirectory of `windowsSdkInstall`. See `defaultWindowsSdkLibVersion` for a typical value, and the one used if not specified. |        
| defaultSdkLibVersion      | constant | `"10.0.18362.0"` for a version used with Visual Studio 2019 Community Edition. |
| perlInstallDirectory      | value    | Specify the absolute path to the directory where a Windows version of PERL is installed.  Either Strawberry Perl or ActiveState Perl should work fine. Example: `"D:\\Strawberry\\perl"` |
| msys2InstallDirectory     | value     | Specify the absolute path to the directory MSYS2 is installed in.  Don't install MSYS2 in an absolute path that has any embedded blanks, headaches ensue. Example: `msys2InstallDirectory = "D:\\msys64"`. Also note that as described elsewhere, a number of MSYS2 packages must be installed using `pacman` at the MSYS2 64 bit command prompt before verify and build tasks using build type `mingw64` can work.|
| **android** settings      |
| androidSdkLocation        | value     | Specify the absolute path to the directory where the Android SDK is installed. Example: `"D:\\Android\\sdk"` |
| androidNdkVersion         | value     | String value matching a subdirectory of `androidSdkLocation`. Example: `"21.3.6528147"` for version r20b of the NDK . Note that the plugin assumes a standard NDK install for locating the underlying llvm toolchain used by ndk-build.cmd.  |
| androidMinimumSdk         | int value | Specify the minimum sdk version android build will run on. Used by both Android NDK builds and OpenSSL configuration for android. Example: `23` |

#### openssl DSL subsection #####

Each option in the `openssl` block is below. OpenSSL build process uses a PERL configure script to verify the build environment and generate the correct makefile artifacts used by a subsequent make run. The options below allow options to be supplied to that process.  
   
| Name                      | Type      | Description |
|---------------------------|-----------|-----------------------------------------|
| srcDirectory              | value     | The subdirectory in the Gradle Project BuildDir that wll contain all source retrieved or generated by the plugin. See `openSslSrcDir` for an example value and the default if this is not speficied. |
| targetsDirectory          | value     | The subdirectory in the Gradle Project BuildDir that wll contain a subdirectory for each build type, which will contain build artifacts produced for that build type. See `opensslTargetsDir` for an example and the default value used if this is not specified. | 
| useGit                    | true/false| Specify true to use Git to retrieve source. Default is false, which uses HTTPS download.  Only specify this if a local git checkout of a specific tag is useful after doing build, it is noticeably slower than the download and extract option.   |
| githubUri                 | value     | Required if `useGit = true`. See `defaultGithubUri` for the value used by default if not specified. |
| tagName                   | value     | Required by both source options. Specify the Git tag name for the release to be retrieved. See `defaultTagName` for en example value, and the one used if this is not specified. Both "OpenSSL_1_1_1g" and "OpenSSL_1_1_1h" have been tested. | 
| defaultTagName            | constant  | `"OpenSSL_1_1_1g"`
| sourceURI                 | value     | URI used by the download task to retrieve a source archive file. Both `.zip` and `.tar.gz` are supported. See `defaultGithubUriArchive` for an example, and the value used if not specified. |
| defaultGithubUri          | constant  | `"https://github.com/openssl/openssl"` |
| defaultGithubUriArchive   | constant  | `"${defaultGithubUri}/archive/"` |
| configureOptions          | value     | List<String> of options to supply to the openssl Configure perl script. Any of the options supported by OpenSSL are valid, see [OpenSSL Compilation Wiki](https://wiki.openssl.org/index.php/Compilation_and_Installation) for details. See `defaultConfigureOptions` for an example specification using the kotlin `listof()` function for an example, and for the values used if this is not specified. Any expression that evaluates to a List<String> is usable. |   
| configureOptions          | value     | List<String> of options to supply to the openssl Configure perl script. Any of the options supported by OpenSSL are valid, see [OpenSSL Compilation Wiki](https://wiki.openssl.org/index.php/Compilation_and_Installation) for details. See `defaultConfigureOptions` for an example specification using the kotlin `listof()` function for an example, and for the values used if this is not specified. Any expression that evaluates to a List<String> is usable. |   
| buildSpecificOptions      | map       | If specified, contains a Map<String, List<String>>, where each entry is keyed by the build type that applies. Example: `buildSpecificOptionss = mapOf("arm64-v8a" to listOf("-fPIC", "-fstack-protector-all"), ...)`. If the current build type matches a key, the specified options for that key are added to those in configureOptions. |
| smallConfigureOptions     | constant  | `listOf("-fPIC", "-fstack-protector-all", "no-asm","no-idea", "no-camellia", "no-seed", "no-bf", "no-cast", "no-rc2", "no-rc4", "no-rc5", "no-md2", "no-md4", "no-ecdh", "no-sock", "no-ssl3", "no-dsa", "no-dh", "no-ec", "no-ecdsa", "no-tls1", "no-rfc3779", "no-whirlpool", "no-srp", "no-mdc2", "no-ecdh", "no-engine", "no-srtp")`

The value for `smallConfigureOptions` comes from the SqlCipher for android java library build process on Github [SqlCipher for Android](https://github.com/sqlcipher/android-database-sqlcipher).

If in the future other common option sets develop during use, they will be added as named constants for use here. The `configureOptions` value is by far the most variable value in this block, so make use of kotlin (or groovy) list operations however you like to set this value to the List<String> desired.  

#### sqlcipher DSL subsection #####

Each option in the `sqlcipher` block is below. OpenSSL build process uses a PERL configure script to verify the build environment and generate the correct makefile artifacts used by a subsequent make run. The options below allow options to be supplied to that process.  
   
| Name                      | Type      | Description |
|---------------------------|-----------|-----------------------------------------|
| buildSqlCipher            | true/false| Default true, controls whether build tasks for sqlcipher are registered. Specify false if only openssl is desired. | 
| srcDirectory              | value     | The subdirectory in the Gradle Project BuildDir that wll contain all source retrieved or generated by the plugin. See `sqlcipherSrcDir` for an example value and the default if this is not speficied. |
| targetsDirectory          | value     | The subdirectory in the Gradle Project BuildDir that wll contain a subdirectory for each build type, which will contain build artifacts produced for that build type. See `sqlcipherTargetsDir` for an example and the default value used if this is not specified. | 
| builds                    | function  | Accepts a list of strings.  Must be one or more of the supported Build Types (see above). Specifying build types not supported by the host OS running gradle causes those build types to be skipped. Specifying anything other than a valid buildType in the list of arguments causes the plugin to punt. |
| useGit                    | true/false| Specify true to use Git to retrieve source. Default is false, which uses HTTPS download.  Only specify this if a local git checkout of a specific tag is useful after doing build for non-plugin work, it is noticeably slower than the download and extract option.   |
| githubUri                 | value     | Required if `useGit = true`. See `defaultGithubUri` for the value used by default if not specified. |
| version                   | value     | Required by both source options. Specify the Version for the release to be retrieved. See `defaultVersion` for en example value, and the one used if this is not specified. SqlCipher uses the convention `tagName="v$version"`, so the plugin derives the GIT tag name and the download archive name from the version value. | 
| defaultVersion            | constant  | `"4.4.0"`
| sourceURI                 | value     | URI used by the download task to retrieve a source archive file. Both `.zip` and `.tar.gz` are supported. See `defaultGithubUriArchive` for an example, and the value used if not specified. |
| defaultGithubUri          | constant  | `"https://github.com/sqlcipher/sqlcipher"` |
| defaultGithubUriArchive   | constant  | `"${defaultGithubUri}/archive/"` |
| configureOptions          | value     | List<String> of options to supply to the openssl Configure perl script. Any of the options supported by OpenSSL are valid, see [OpenSSL Compilation Wiki](https://wiki.openssl.org/index.php/Compilation_and_Installation) for details. See `defaultConfigureOptions` for an example specification using the kotlin `listof()` function for an example, and for the values used if this is not specified. Any expression that evaluates to a List<String> is usable. |   
| defaultConfigureOptions   | constant  | `listOf("no-asm", "no-weak-ssl-ciphers")` |
| sqlcipherSrcDir           | constant  | `"src"` |
| sqlcipherTargetsDir       | constant  | `"targets"` |

### Tasks - Inputs and Outputs ###

The table below lists the various tasks that can be registered, and shows their explicitly defined **input** and **output** Gradle Properties available for use in any outside-the-plugin DSL scripts. 

| Task              | Property      | Type          | Description   |
|-------------------|---------------|---------------|-----------------------|
| sqlcipherBuildAll | builds        | List of Strings| list of the buildTypes for which targets are produced |
|                   | targetDirectoriesMap| Map<String, Directory> | Map keyed by buildType of each target subdirectory containing artifacts | 
| opensslBuildAll   | builds        | List of Strings| list of the buildTypes for which targets are produced |
|                   | targetDirectoriesMap| Map<String, Directory> | Map keyed by buildType of each target subdirectory containing artifacts | 

*Future - add rest of tasks and properties details*

### Logging ###

The Gradle Project's standard logger is used to produce additional information from the plugin. The intent is to produce minimal additional logging by default, but have additional info available if desired. Logging levels used are:

- Error - if a build step fails, stderr then stdout are sent to this logger before throwing the failing exception.
- Warning - if a build step succeeds but produces stderr output, typically for warnings, stderr is sent to this logger.
- Lifecycle - a small number of status messages use this
- Info - all stdout from build tools (compilers, linkers, perl scripts, etc) used is logged here. 

Use gradle's logging options (`--info` for example) to display Info level messages to the gradle console when desired.  By default Gradle console shows Lifecycle level and higher messages.

## Windows Details

### Visual Studio 2019 Community Edition
The plugin does not require any entries in the Windows PATH environment variables, so the locations of the Windows SDK and of Visual Studio must be configured in the DSL for this to work. Also since Windows does not provide Perl by default, a windows-oriented Perl install is required and the plugin will verify the install.  Note the term "windows-oriented". The OpenSSL configure script is aware of various types of Perl support,  If an attempt is made to use a Perl it does not expect, for example the Perl that comes with MSYS2, on a Visual Studio build, the configure will fail.  A windows-oriented Perl that uses the window file path conventions, like Active Perl or Strawberry Perl, will work fine.   

The Visual Studio build for OpenSSL is straightforward. The OpenSSL build process for windows works without problem using Visual Studio 2019 Community Edition. The default Configure script command and subsequent make steps used by this plugin looks like this:
```
Configure VC-WIN64A no-asm no-weak-ssl-ciphers
nmake clean
nmake
```
The **configureOptions** can be changed in the openssl DSL to any combination of the options offered by OpenSSL: [OpenSSL Configure Options](https://wiki.openssl.org/index.php/Compilation_and_Installation#Configure_Options) as well as any compiler options. See the Reference section above for details.

The SqlCipher windows build using Visual Studio is more complex.  See the **Reference** section above for details on the compile options and other configuration requirements. The install locations for the local Visual Studio install and the Windows SDK install must be set for this build to work. Sqlite/SqlCipher use a Visual Studio-specific make file **Makefile.msc** to drive the make process.  No Perl configuration script is used. The plugin then uses these steps. Note that a command file was used with nmake as there can be a LOT of text making the command line really long otherwise:
- Visual Studio 64 bit native build environment setup **vcvars64.bat**
- nmake /f Makefile.msc clean
- nmake /f Makefile.msc @nmakeCmdFile.txt

The plugin generates the contents of nmakeCmdFile.txt in the root of the SqlCipher directory alongside Makefile.msc. This is a sample of what that file looks like:
```
    FOR_WIN10=1
    PLATFORM=x64
    USE_NATIVE_LIBPATHS=1
    NCRTLIBPATH="${sdkLibPath}ucrt\x64"
    NSDKLIBPATH="${sdkLibPath}um\x64"
    LTLIBS="Advapi32.lib User32.lib kernel32.lib"
    CCOPTS="-guard:cf ${ext.compilerOptions} -I${opensslIncludeDir.absolutePath}"
    LDFLAGS=${opensslLibDir}\libcrypto_static.lib
    SHELL_CORE_LIB=libsqlite3.lib
``` 
The value for `${sdkLibPath}` comes from configuration.  

`${ext.compilerOptions}` specifies the desired compiler options from the DSL configuration.  See the **Reference** section above for details on these options. Note the **-guard:cf** option in CCOPTS, this may be a new requirement with Visual Studio 2019. The Makefile.msc includes this option at link time, but does not use it at compile time, which caused compile errors under Visual Studio 2019. 

LTLIBS was specified with the above three standard windows libraries to resolve some Unresolved External errors that occur without them. 

LDFLAGS points back to the static library built by the opensslBuildvStudio64 task for use by the linker, indicating that OpenSSL is statically linked. 

The SHELL_CORE_LIB value is a hack :-). Without it the link of the SqlCipher shell EXE that provides a command line tool fails with an unresolved external on C function `sqlcipher_version`. The default included library in Makefile.msc doesn't resolve this function.  The SHELL_CORE_LIB setting used here resolves this, but also causes a bunch of warnings about duplicate entries at link time because the specified library resolves many of the same symbols as the default library,  The resulting shell program runs just fine though.  One of the goals of the plugin is to make NO changes to the source make files, so this work-around was used.  I have not researched the root cause of the issue requiring the workaround, or found a better workaround yet.

After the Windows build process runs, buildDir\openssl\vStudio64 subdirectory contains these files:

buildDir\sqlcipher\vStudio64 subdirectory contains these files:

### MSYS2 and MingW64

Once MSYS2 is installed and configured, the build process for OpenSSL is straightforward. MSYS2 must have these packages installed before using the plugin to do a mingw64 build, or the build will fail. These installs are done from the MSYS2 command prompt.
- pacman -S mingw-w64-x86_64-toolchain
- pacman -S make
- pacman -S mingw-w64-x86_64-cmake
- pacman -S diffutils

The MSYS2 install directory must be properly configured in the DSL, see the **Reference** section above for details.  The plugin does not rely on the Windows PATH environment variable, so the install directory specification in the DSL is a requirement. 

The OpenSSL build process for windows works without problem using Visual Studio 2019 Community Edition. The default Configure script command and subsequent make steps used by this plugin looks like this:
```
Configure VC-WIN64A no-asm no-weak-ssl-ciphers
make clean
make
```

After the Windows build process runs, buildDir\openssl\mingw64 subdirectory contains these files:
```libcrypto-1_1-x64.dll
libcrypto.a
libcrypto.dll.a
libssl-1_1-x64.dll
libssl.a
libssl.dll.a
```
buildDir\sqlcipher\mingw64 subdirectory contains these files (from the .libs subdirectory after build):
```libsqlcipher-0.dll
libsqlcipher.a
libsqlcipher.dll.a
libsqlcipher.la
libsqlcipher.lai
lt-sqlcipher.c
sqlcipher.exe
sqlcipher_ltshwrapper
sqlite3.o
```

The SqlCipher shell links openssl dynamically, so to run it requires `libcrypto-1_1-x64.dll` to be either on the PATH or copied into the current working directory.

### Android Builds on Windows

Android builds require specifying four lines in the `tools` section of the DSL, identifying the location of the Android SDK, the version in use, and the minimum API level of android the build will run on.  The `msys2InstallDirectory` (also used by mingw64 builds) is required also. For various reasons it was simpler to use linux-oriented Perl and shell scripts when running OpenSSL Configure perl script and the SqlCipher configure script. Here is a sample of these required lines:  
```
    androidSdkLocation = "D:\\Android\\sdk"
    androidNdkVersion = "21.3.6528147"
    androidMinimumSdk = 23
    msys2InstallDirectory = "D:\\msys64"
```
After the Android opensslBuildarm64-v8a task completes, buildDir\openssl\arm64-v8a subdirectory contains these files
```
libcrypto.a
libcrypto.map
libcrypto.pc
libcrypto.so
libcrypto.so.1.1
libssl.a
libssl.map
libssl.pc
libssl.so
libssl.so.1.1
openssl.pc
``` 

Same for the Android opensslBuildx86_64 task, buildDir\openssl\x86_64 subdirectory contains these files
```
libcrypto.a
libcrypto.map
libcrypto.pc
libcrypto.so
libcrypto.so.1.1
libssl.a
libssl.map
libssl.pc
libssl.so
libssl.so.1.1
openssl.pc
``` 