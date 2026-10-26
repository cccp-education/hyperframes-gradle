@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
    id("com.gradleup.nmcp.settings").version("1.5.0")
}

val globalProps = java.util.Properties().also {
    val globalFile = file(System.getProperty("user.home") + "/.gradle/gradle.properties")
    if (globalFile.exists()) it.load(globalFile.inputStream())
}

nmcpSettings {
    centralPortal {
        // Tolerate a credential-less runner (CI): publishing later fails loudly,
        // but configuration + tests run. Mirrors the capsule settings (S-016 fix).
        username = globalProps.getProperty("ossrhUsername") ?: ""
        password = globalProps.getProperty("ossrhPassword") ?: ""
        publishingType = "AUTOMATIC"
    }
}

dependencyResolutionManagement {
    repositories {
        // mavenLocal() lets a just-published N1 (video-kernel) be consumed while
        // Central propagation is still in flight; CI falls through to Central.
        mavenLocal()
        mavenCentral()
    }
}

// ── MEM-CAT-ROLLOUT-4 — Catalog workspace published (MEMPHIS): single pin per borough (D4) ──
// education.cccp:workspace-catalog:0.0.106 — cross-borough source of truth for plugin versions.
dependencyResolutionManagement {
    versionCatalogs {
        create("ws") {
            from("education.cccp:workspace-catalog:0.0.106")
        }
    }
}

rootProject.name = "hyperframes-plugin"
