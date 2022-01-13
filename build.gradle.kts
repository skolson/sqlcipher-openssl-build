import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.gradle.ext.ProjectSettings

plugins {
    kotlin("jvm") version "1.5.31"
    `java-gradle-plugin`
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.1"
    id("com.gradle.plugin-publish") version "0.19.0"
    `maven-publish`
}

val groupName = "com.oldguy.gradle"
val artifactName = "sqlcipher-openssl-build"
val versionString = "0.3.1"
group = groupName
version = versionString

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
    testImplementation(kotlin("test"))
    //testImplementation("io.kotest:kotest-runner-junit5-jvm:5.0.3")
    //testImplementation("io.kotest:kotest-assertions-core-jvm:5.0.3")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "15"
}

pluginBundle {
    website = "https://github.com/skolson/sqlcipher-openssl-build"
    vcsUrl = "https://github.com/skolson/sqlcipher-openssl-build.git"
    tags = listOf("openssl", "sqlcipher")
}

gradlePlugin {
    plugins {
        create(artifactName) {
            // This ID is used by the dependent projects in the plugins block
            id = "$groupName.$artifactName"
            displayName = "OpenSSL and SqlCipher builds"
            description = "Automated builds of OpenSSL and SqlCipher, 64 bit only, for Windows, Linux and Mac build hosts. Multiple target hosts including Android, VStudio, mingw, Linux. Mac, IOS"
            implementationClass = "com.oldguy.gradle.SqlCipherPlugin"
        }
    }
}
afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "localRepo"
                url = uri("https://localhost/MavenRepo")
            }
        }
        publications {
            create<MavenPublication>("sqlcipherPlugin") {
                from(components["java"])
                groupId    = groupName
                artifactId = artifactName
                version    = versionString
            }
        }
    }
}

/**
 * The code below remedies a classpath issue with running unit tests using Gradle Runner using Idea. Unknown if
 * this is required with Android Studio - it is untried there yet. But only applies to running unit tests.
 */
val fixIdeaPluginClasspath = tasks.create("fixIdeaPluginClasspath") {
    doFirst {
        tasks.pluginUnderTestMetadata.configure {
            val ideaClassesPath = project.buildDir.toPath()
                    .resolveSibling("out")
                    .resolve("production").toFile()
            val newClasspath = pluginClasspath.toMutableList()
            newClasspath.add(0, ideaClassesPath)
            pluginClasspath.setFrom(newClasspath)
        }
    }
}
tasks.getByName("pluginUnderTestMetadata").mustRunAfter(fixIdeaPluginClasspath)

fun org.gradle.plugins.ide.idea.model.IdeaProject.settings(block: ProjectSettings.() -> Unit) =
        (this@settings as ExtensionAware).extensions.configure(block)

fun ProjectSettings.taskTriggers(block: org.jetbrains.gradle.ext.TaskTriggersConfig.() -> Unit) =
        (this@taskTriggers as ExtensionAware).extensions.configure("taskTriggers", block)

idea {
    project {
        settings {
            taskTriggers {
                beforeBuild(fixIdeaPluginClasspath, tasks.pluginUnderTestMetadata)
            }
        }
    }
}