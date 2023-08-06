import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.gradle.ext.ProjectSettings

plugins {
    kotlin("jvm") version "1.8.21"
    `java-gradle-plugin`
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("com.gradle.plugin-publish") version "1.2.0"
    `maven-publish`
}

val groupName = "com.oldguy.gradle"
val artifactName = "sqlcipher-openssl-build"
val versionString = "0.3.5"
group = groupName
version = versionString

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://github.com/skolson/sqlcipher-openssl-build"
    vcsUrl = "https://github.com/skolson/sqlcipher-openssl-build.git"
    plugins {
        create(artifactName) {
            // This ID is used by the dependent projects in the plugins block
            id = "$groupName.$artifactName"
            displayName = "OpenSSL and SqlCipher builds"
            description = "Automated builds of OpenSSL and SqlCipher, 64 bit only, for Windows, Linux and Mac build hosts. Multiple target hosts including Android, VStudio, mingw, Linux. Mac, IOS"
            implementationClass = "com.oldguy.gradle.SqlCipherPlugin"
            tags = listOf("openssl", "sqlcipher")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
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
val fixIdeaPluginClasspath: Task = tasks.create("fixIdeaPluginClasspath") {
    doFirst {
        tasks.pluginUnderTestMetadata.configure {
            val ideaClassesPath = project.layout.buildDirectory.get().asFile
                    .resolveSibling("out")
                    .resolve("production")
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