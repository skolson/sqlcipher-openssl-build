package com.oldguy.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class PluginBuildTest {

    @Test
    fun basicTest(@TempDir(cleanup = CleanupMode.NEVER) testProjectDir: Path) {
        println("Test project path: ${testProjectDir.toAbsolutePath()}")
        val gradleRunner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.toFile())
                .withTestKitDir(testProjectDir.resolve("testkit").toFile())

        val buildFile = testProjectDir.resolve("build.gradle.kts")

        val ktsText = """
    import com.oldguy.gradle.OpensslExtension
    import com.oldguy.gradle.SqlcipherExtension
    import com.oldguy.gradle.BuildType

    plugins {
        id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.6.0"
    }
   

    sqlcipher {
        useGit = false
        version = "4.16.0"
        compilerOptions = SqlcipherExtension.defaultCompilerOptions
        buildCompilerOptions = mapOf(
            BuildType.androidX64 to SqlcipherExtension.androidCompilerOptions, 
            BuildType.androidArm64 to SqlcipherExtension.androidCompilerOptions, 
            BuildType.iosX64 to SqlcipherExtension.iosCompilerOptions, 
            BuildType.iosArm64 to SqlcipherExtension.iosCompilerOptions, 
            BuildType.iosSimulatorArm64 to SqlcipherExtension.iosCompilerOptions, 
            BuildType.macosX64 to SqlcipherExtension.macOsCompilerOptions,
            BuildType.macosArm64 to SqlcipherExtension.macOsCompilerOptions
        )

        //builds("vStudio64", "androidX64", "androidArm64", "mingwX64", "linuxX64", "linuxArm64")
        //builds("androidX64", "androidArm64")
        builds("macosArm64")
        //builds(BuildType.appleBuildTypes) 
        //builds("iosSimulatorArm64")
        
        val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
        val nativeInterop = projectDir.resolve("src/nativeInterop")
        val abiMap = mapOf(
            BuildType.androidX64 to "x86_64",
            BuildType.androidArm64 to "arm64-v8a"
        )
        targetsCopyTo = { buildType ->
            if (buildType.isAndroid)
                androidMainDirectory.resolve("sqlcipher").resolve(abiMap[buildType]!!)
            else
                nativeInterop.resolve(buildType.name)
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
                macosSdkLocation = "/Volumes/mnt/Library/Android"
                ndkVersion = "29.0.14206865"
                minimumSdk = 26
            }
            apple {
                platformsLocation = "/Applications/Xcode.app/Contents/Developer"
                sdkVersion = "18"
                sdkVersionMinimum = "14"
            }
        }
        openssl {
            tagName = "openssl-4.0.1"
            useGit = false
            configureOptions = OpensslExtension.smallConfigureOptions
            buildSpecificOptions = OpensslExtension.buildOptionsMap
        }
    }
""".trimIndent()
        Files.writeString(buildFile, ktsText)

        val result = gradleRunner
            //.withArguments("sqlcipherBuildAll", "--stacktrace", "--warning-mode", "all")
            .withArguments("sqlcipherBuildmacosArm64", "--stacktrace", "--warning-mode", "all")
            //.withArguments("opensslBuildiosX64", "--stacktrace", "--info", "-Dorg.gradle.debug=true", "--warning-mode", "all")
            //.withArguments("opensslBuildmingw64", "--stacktrace")
            //.withArguments("sqlcipherBuildvStudio64", "--stacktrace", "-Dorg.gradle.debug=true")
            //.withArguments("sqlcipherBuildmingwX64", "--stacktrace")
            //.withArguments("sqlcipherBuildandroidArm64", "--stacktrace", "--warning-mode", "all")
            //.withArguments("sqlcipherBuildandroidX64", "--stacktrace")
            //.withArguments("sqlcipherBuildAll", "--stacktrace", "--warning-mode", "all")
            .build()
        println(result.output)
    }
}