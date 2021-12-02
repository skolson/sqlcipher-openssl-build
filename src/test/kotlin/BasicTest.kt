package com.oldguy.gradle

import io.kotest.core.spec.style.StringSpec
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner

class PluginBuildTest : StringSpec({
    basicTest()
}) {

    companion object {
        fun basicTest() {
            val testProjectDir = TemporaryFolder()
            testProjectDir.create()
            println("Text project path: ${testProjectDir.root.absolutePath}")
            val gradleRunner = GradleRunner.create()
                    .withPluginClasspath()
                    .withProjectDir(testProjectDir.root)
                    .withTestKitDir(testProjectDir.newFolder())

            // creates temp directory for a gradle project
            val buildFile = testProjectDir.newFile("build.gradle.kts")

            val ktsText = """
        import com.oldguy.gradle.OpensslExtension
        import com.oldguy.gradle.SqlcipherExtension
        
        plugins {
            id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.2.0"
        }
       

        sqlcipher {
            useGit = false
            version = "4.4.3"
            compilerOptions = SqlcipherExtension.androidCompilerOptions

            //builds("vStudio64", "x86_64", "arm64-v8a", "mingw64", "linuxX64")
            builds("x86_64", "arm64-v8a")
            
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
                    //sdkLocation = "/home/steve/Android/Sdk"
                    ndkVersion = "23.0.7599858"
                    minimumSdk = 23
                }
            }
            openssl {
                tagName = "openssl-3.0.0-beta2"
                useGit = false
                configureOptions = OpensslExtension.smallConfigureOptions
                buildSpecificOptions = mapOf(
                    "arm64-v8a" to OpensslExtension.nonWindowsOptions,
                    "x86_64" to OpensslExtension.nonWindowsOptions)
            }
        }
    """.trimIndent()
            buildFile.appendText(ktsText)

            val result = gradleRunner
                    //.withArguments("opensslBuildmingw64", "--stacktrace")
                    //.withArguments("sqlcipherBuildvStudio64", "--stacktrace", "-Dorg.gradle.debug=true")
                    //.withArguments("sqlcipherBuildmingw64", "--stacktrace")
                    //.withArguments("sqlcipherBuildarm64-v8a", "--stacktrace")
                    //.withArguments("sqlcipherBuildx86_64", "--stacktrace")
                    .withArguments("sqlcipherBuildAll", "--stacktrace", "--warning-mode", "all")
                    .build()
            println(result.output)
        }
    }

    /*
    Example build script test for SqlCipher

    SET PATH=D:\Android\sdk\ndk\23.0.7599858\toolchains\llvm\prebuilt\windows-x86_64\bin;%PATH%
    D:\Android\sdk\ndk\23.0.7599858\ndk-build.cmd V=1 NDK_DEBUG=0 NDK_APPLICATION_MK=D:\Temp\junit2902566868193315402\build\src\sqlcipher-4.4.3\Plugin-Application.mk NDK_PROJECT_PATH=D:\Temp\junit2902566868193315402\build\src\sqlcipher-4.4.3 all
     */
}