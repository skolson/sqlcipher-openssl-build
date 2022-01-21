package com.oldguy.gradle

import org.gradle.api.GradleException
import java.io.File

/**
 * Builds amalgamation script and runs it. Creates build shell script to compile amalgamation into static library.
 * Supports all apple build types.
 */
class SqlCipherApple(
    private val runner:Runner,
    private val srcDir: File,
    private val buildType: BuildType,
    private val config: AppleToolExtension
) {
    private val amalgamation = SqlcipherExtension.amalgamation
    fun build(opensslIncludeDir: File,
              compilerOptions: List<String>): String {
        SqlCipherBuild.buildAmalgamation(
            srcDir,
            runner,
            buildType
        )
        if (!srcDir.resolve(amalgamation).exists())
            throw GradleException("Amalgamation $amalgamation not found")
        val scriptName = BuilderTask.pluginFileName("build.sh")
        /*
         * compile amalgamation file using SDK for the build type, make library with libtool
         */
        val options = buildString {
            if (buildType == BuildType.iosArm64) append("-arch arm64 ")
            compilerOptions.forEach { append("$it ") }
            config.options(buildType).forEach { append("$it ") }
            append("-I. -I${opensslIncludeDir.absolutePath} -fPIC -O3")
        }
        val objectName = "${SqlcipherExtension.moduleName}.o"
        runner.logger.lifecycle("$scriptName, compilerOptions: $options")
        BuilderTask.createPluginFile(
            srcDir,
            scriptName,
            addPrefix = false) {
            """
            #!/bin/zsh
            export PATH="${config.toolChainPath}:${'$'}PATH"    
            clang $options -o $objectName -c $amalgamation
            libtool -static -o $libraryName $objectName
            """.trimIndent()
        }
        return runner.command(srcDir, "./$scriptName") { }
    }

    companion object {
        const val libraryName = "libsqlcipher.a"
    }
}