plugins {
    kotlin("jvm") version "2.1.21"
    `java-gradle-plugin`
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.10"
    id("com.gradle.plugin-publish") version "1.3.1"
    `maven-publish`
}

val groupName = "com.oldguy.gradle"
val artifactName = "sqlcipher-openssl-build"
val versionString = "0.5.2"
val jgitVersion = "7.3.0.202506031305-r"

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
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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