plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.gradle.publish)
}

val groupName = "com.oldguy.gradle"
val artifactName = "sqlcipher-openssl-build"
val versionString: String = libs.versions.pluginVersion.get()
val jgitVersion: String = libs.versions.jgit.get()

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
    implementation(libs.jgit)
    testImplementation(libs.kotlin.test)
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