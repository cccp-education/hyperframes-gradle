plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.publish)
    id("education.cccp.build.gradle-plugin") version "0.0.2"
    id("education.cccp.build.publishing") version "0.0.2"
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

    // Cucumber BDD — HF-4c N3 contract acceptance tests
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.picocontainer)
    testImplementation(libs.junit.platform.suite)
}

tasks.withType<Test> {
    outputs.cacheIf { true }
}

// Cucumber BDD — features in src/test/resources/features, steps in src/test/kotlin/hyperframes/scenarios
sourceSets.test {
    resources.srcDir("src/test/resources/features")
}

val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
            sourceSets.test.get().output +
            sourceSets.main.get().output +
            files(tasks.named("jar").get().outputs.files)

    dependsOn(tasks.classes)
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    val cucumberTags = project.findProperty("cucumber.tags")?.toString()
        ?: "not @wip and not @integration"
    systemProperty("cucumber.filter.tags", cucumberTags)
}

tasks.named("check") { dependsOn(cucumberTest) }

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