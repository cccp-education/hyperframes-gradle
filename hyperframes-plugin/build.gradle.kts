plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.publish)
    id("education.cccp.build.gradle-plugin") version "0.0.7"
    id("education.cccp.build.publishing") version "0.0.7"
    id("education.cccp.build.functional-test") version "0.0.7"
}

group = "education.cccp"
// MEM-CAT-ROLLOUT-4 (D3) — self version derived from the published workspace catalog.
version = ws.versions.hyperframes.plugin.get()

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(platform("education.cccp:workspace-bom:0.0.108"))

    implementation(kotlin("stdlib-jdk8"))
    // N1 shared engine-neutral video kernel (spike — 2nd engine adapter).
    implementation(libs.video.kernel)
    implementation(libs.asciidoctorj)
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

    // D5R-2 (cross-borough pattern, graphify S-029) — inject the *published*
    // workspace catalog versions resolved by Gradle from the catalog pin.
    // The publication hygiene guard must never read a neighbour repository's
    // working tree: that is racy between parallel sessions (and absent on CI).
    systemProperty("hyperframes.publishedCatalog.pluginVersion", ws.versions.hyperframes.plugin.get())
    systemProperty("hyperframes.publishedCatalog.bomVersion", ws.versions.workspace.bom.get())
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
    // HF-CR3 — restrict the hermetic run to the aggregate runner so the
    // dedicated integration runner (Cr3RealRenderCucumberRunner) is never
    // discovered here (a 0-scenario suite would fail with
    // NoTestsDiscoveredException — same pitfall as capsule S-082).
    filter {
        includeTestsMatching("hyperframes.scenarios.CucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    val cucumberTags = project.findProperty("cucumber.tags")?.toString()
        ?: "not @wip and not @integration"
    systemProperty("cucumber.filter.tags", cucumberTags)
}

// HF-CR3-3 — dedicated runner for the real-render acceptance feature.
// Skip-guarded (`assumeTrue`) and *not* part of `check`: it needs Node.js, the
// `hyperframes` npm package and `ffprobe`. The non-blocking
// `.github/workflows/real-render-hyperframes.yml` drives it (HF-CR3-2).
val cucumberTestCr3RealRender = tasks.register<Test>("cucumberTestCr3RealRender") {
    description = "Runs the real HyperFrames CLI render acceptance (skip-guarded; needs Node + the hyperframes npm package)"
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
    filter {
        includeTestsMatching("hyperframes.scenarios.Cr3RealRenderCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.filter.tags", "@real-render and not @wip")
}

tasks.named("check") { dependsOn(cucumberTest) }

// S-022 (dogfooding) — expose the plugin artefact version in the jar manifest so the
// N3 contract reports the plugin version, not the consumer project's `version`.
tasks.named<Jar>("jar") {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
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
    website = "https://github.com/cccp-education/hyperframes-gradle"
    vcsUrl = "https://github.com/cccp-education/hyperframes-gradle.git"
}

publishingConventions {
    publicationType = "PLUGIN"
}

// RELOCATION : garde comme echappatoire future.
// Activable avec -Prem relocationGroup="<futur-namespace>"

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