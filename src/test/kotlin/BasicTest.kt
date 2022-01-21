package com.oldguy.gradle

import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test

internal class PluginBuildTest {

    @Test
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
    import com.oldguy.gradle.BuildType

    plugins {
        id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.3.1"
    }
   

    sqlcipher {
        useGit = false
        version = "4.5.0"
        compilerOptions = SqlcipherExtension.defaultCompilerOptions
        buildCompilerOptions = mapOf(
            BuildType.androidX64 to SqlcipherExtension.androidCompilerOptions, 
            BuildType.androidArm64 to SqlcipherExtension.androidCompilerOptions, 
            BuildType.iosX64 to SqlcipherExtension.iosCompilerOptions, 
            BuildType.iosArm64 to SqlcipherExtension.iosCompilerOptions, 
            BuildType.macosX64 to SqlcipherExtension.macOsCompilerOptions
        )

        //builds("vStudio64", "androidX64", "androidArm64", "mingwX64", "linuxX64")
        //builds("androidX64", "androidArm64")
        builds(BuildType.appleBuildTypes) 
        
        targetsCopyTo = { buildType ->
            if (buildType.isAndroid) 
                project.projectDir.resolve("TestFiles/androidCinterop")
            else 
                project.projectDir.resolve("TestFiles/cinterop")
        }
        copyCinteropIncludes = true
        
        tools {
            windows {
                msys2InstallDirectory = "D:\\msys64"
                sdkInstall = "D:\\Program Files (x86)\\Windows Kits\\10"
                sdkLibVersion = "10.0.18362.0"
                visualStudioInstall = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\"
                perlInstallDirectory = "D:\\SqlCipher\\Strawberry\\perl"
            }
            android {
                windowsSdkLocation = "D:\\Android\\sdk"
                linuxSdkLocation = "/home/steve/Android/Sdk"
                macosSdkLocation = "/Users/steve/Library/Android/sdk"
                ndkVersion = "24.0.7956693"
                minimumSdk = 23
            }
            apple {
                sdkVersion = "15"
                sdkVersionMinimum = "14"
            }
        }
        openssl {
            tagName = "openssl-3.0.1"
            useGit = false
            configureOptions = OpensslExtension.smallConfigureOptions
            buildSpecificOptions = OpensslExtension.buildOptionsMap
        }
    }
""".trimIndent()
        buildFile.appendText(ktsText)

        val result = gradleRunner
            .withArguments("sqlcipherBuildAll", "--stacktrace", "--warning-mode", "all")
            //.withArguments("opensslBuildiosX64", "--stacktrace", "--info", "-Dorg.gradle.debug=true", "--warning-mode", "all")
                //.withArguments("opensslBuildmingw64", "--stacktrace")
                //.withArguments("sqlcipherBuildvStudio64", "--stacktrace", "-Dorg.gradle.debug=true")
                //.withArguments("sqlcipherBuildmingwX64", "--stacktrace")
                //.withArguments("sqlcipherBuildandroidArm64", "--stacktrace")
                //.withArguments("sqlcipherBuildandroidX64", "--stacktrace")
                //.withArguments("sqlcipherBuildAll", "--stacktrace", "--warning-mode", "all")
                .build()
        println(result.output)
    }
}