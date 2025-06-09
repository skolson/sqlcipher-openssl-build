##GradleSqlCipher Change log

###Version history

### 0.5.0 2025-06
- Gradle 8.14.1
- Kotlin 2.1.21
- com.gradle.plugin-publish 1.3.1
- org.eclipse.jgit:org.eclipse.jgit 7.3.0.202506031305-r
- Fixed a deprecated usage of gradle Project exec function that would break in Gradle 9.0
- Removed obsolete Idea classpath workaround from build.gradle.kts
- Start using the SqlCipher configure option --with-tempstore=yes instead of --enable-tempstore=yes on versions 4.7.0 and later
- Stop using deleted configure option --enable-static=yes on versions 4.7.0 and later
- Stop using deleted configure option --with-crypto-lib=none on versions 4.7.0 and later
- add new required compile options -DSQLITE_EXTRA_INIT=sqlcipher_extra_init and -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown. Shouldn't impact builds of older versions before these were added
- NOTE: SqlCipher changed the name of the libraries built by the make process from libsqlcipher to libsqlite3. So any .def files looking for libsqlcipher.a need to be changed to libsqlite3.a

### 0.4.0 2024-03

- Gradle 8.6
- Kotlin 1.9.22
- com.gradle.plugin-publish 1.2.1
- org.eclipse.jgit:org.eclipse.jgit 6.9.0.202403050737-r
- Add support for Macos arm64 architecture to both openssl and sqlcipher builds
- Plugin test script now updated to OpenSSL 3.2.1 and SqlCipher 4.5.6
- NOTE: macosArm64 target builds with no errors, but build has not been tested on Apple Mac M1 hardware.

### 0.3.5 2023-08

- For Gradle 9 compatibility, change all input properties of type "url" to String. String Property value must be valid for java.net.URL constructor
- Kotlin 1.8.21
- jgit 6.6.0.202305301015-r
- Java 17
- gradle plugin-publish plugin version 1.2.0 (note: this was not included in 0.3.5 Git tag in error)

### 0.3.4 2022-03

- Sqlcipher 4.5.1 and Android NDK 25.0.8151533 fails build due to missing `LOCAL_LDLIBS := -llog` line in make file
- Gradle 7.4.1

### 0.3.3 2022-01-20

- Bugfix: some host-specific tasks could attempt to run on wrong host OS.
- Bugfix: BuildType refactor regressed android builds by specifying wrong ABI literal
- Ios Sqlcipher compile now has bitcode enabled

### 0.3.2 2022-01-18

- Refactored BuildTypes strings to BuildType enum class
- Sqlcipher build task ahd configuration now supports optional copy operation of compiled targets from the targets subdirectory of the build folder to anywhere else. A configurable function is added in SqlcipherExtension to support DSL script controlling copy destination by BuildType.
- Option added to copy includes required by cinterop

### 0.3.1 2022-01-15

- Support for Android NDK builds on Mac host
- Minor changes in OpenSSL extension default handling of Configure options
- Readme changes

### 0.3.0 2022-01-11

- Support MacOS, IOS and IOS Simulator 64 bit builds on Mac hosts
- Readme changes

### 0.2.5 2021-08-15
*Added*

- Kotlin 1.5.31 and Gradle 7.3.x.

### 0.2.0 2021-08-15
*Added*

- Support for Gradle 7.x by improving labeling of properties in tasks to conform to the new requirements.
- Tested/built with Gradle 7.1.1
- Support for OpenSSL 3.x
- Support for NDK versions r22 and r23 which have slightly changed directory structures
- Make separate source directories for each build type, for both OpenSSL and SqlCipher.  Both build processes use the source directory and subdirectories within it as working/output diurectories. Keeping each build type in separate source directories retains all the intermediate output files for potential use, and avoids gradle 7.x warnings about overlapping output directories across build tasks. 

*Changed*

- Replaced ANDROID_SDK_HOME usage with ANDROID_SDK_ROOT

### 0.1.1 2020-11-12

Minor fix

### 0.1.0 2020-10-06

Original release, using Gradle 6.6.1