package com.oldguy.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
/*
class PluginTest : StringSpec({

    "Using the Plugin ID" should {
        "Apply the Plugin" {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply("sqlcipher-openssl-build")
            project.plugins.getPlugin(SqlCipherPlugin::class.java) shouldNotBe null
        }
    }
})
*/
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
            id("sqlcipher-openssl-build")
        }

        sqlcipher {
            useGit = false
            version = "4.4.0"
            compilerOptions = SqlcipherExtension.androidCompilerOptions

            builds("vStudio64", "x86_64", "arm64-v8a", "mingw64", "linuxX64")
            
            tools {
                windows {
                    msys2InstallDirectory = "D:\\msys64"
                    sdkInstall = "D:\\Program Files (x86)\\Windows Kits\\10"
                    sdkLibVersion = "10.0.18362.0"
                    visualStudioInstall = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\"
                    perlInstallDirectory = "D:\\SqlCipher\\Strawberry\\perl"
                }
                android {
                    //sdkLocation = "D:\\Android\\sdk"
                    sdkLocation = "/home/steve/Android/Sdk"
                    ndkVersion = "21.3.6528147"
                    minimumSdk = 23
                }
            }
            openssl {
                tagName = "OpenSSL_1_1_1h"
                useGit = false
                configureOptions = OpensslExtension.smallConfigureOptions
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
                    .withArguments("sqlcipherBuildAll", "--stacktrace")
                    .build()
            println(result.output)
        }
    }
}