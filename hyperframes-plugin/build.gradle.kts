plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.publish)
    id("education.cccp.build.gradle-plugin") version "0.0.1"
    id("education.cccp.build.publishing") version "0.0.1"
}

group = "education.cccp"
version = "0.0.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.asciidoctorj)
    implementation(libs.node.gradle)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
    testImplementation(gradleTestKit())
}

tasks.withType<Test> {
    outputs.cacheIf { true }
}

gradlePlugin {
    plugins {
        create("hyperframes") {
            id = "education.cccp.hyperframes"
            implementationClass = "hyperframes.HyperframesPlugin"
            displayName = "HyperFrames Plugin"
            description = "Plugin Gradle N2 transformant un document AsciiDoc annote en video MP4 via le moteur HyperFrames (HeyGen, Apache 2.0)."
            tags.set(listOf("asciidoc", "hyperframes", "video", "mp4", "kotlin"))
        }
    }
    website = "https://cheroliv.com"
    vcsUrl = "https://github.com/cheroliv/hyperframes-gradle.git"
}

publishingConventions {
    publicationType = "PLUGIN"
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("HyperFrames Gradle Plugin")
            description.set("Plugin Gradle N2 transformant un document AsciiDoc annote en video MP4 via le moteur HyperFrames (HeyGen, Apache 2.0).")
        }
    }
    repositories {
        mavenCentral()
    }
}