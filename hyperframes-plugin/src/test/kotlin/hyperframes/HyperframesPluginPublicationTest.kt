package hyperframes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * MEM-CAT-ROLLOUT-4 (S-029, cross-borough MEMPHIS) — publication hygiene guard.
 *
 * D3: the plugin self version is derived from the published workspace catalog
 * (`ws.versions.hyperframes.plugin.get()`) — never a duplicated literal.
 * D4: the borough pins the catalog once in settings.gradle.kts.
 * D5 hygiene: the local toml self version and the ws catalog version must agree,
 * and the platform pin must match the ws catalog BOM version.
 *
 * D5R-2 (S-017) — the *published* ws catalog versions are injected by Gradle as
 * `hyperframes.publishedCatalog.{pluginVersion,bomVersion}` system properties.
 * The guard never reads a neighbour repository's working tree — that was racy
 * between parallel sessions (and absent on the CI runner).
 */
class HyperframesPluginPublicationTest {
    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    private val publishedPluginVersion: String
        get() =
            System.getProperty("hyperframes.publishedCatalog.pluginVersion")
                ?: error(
                    "hyperframes.publishedCatalog.pluginVersion is not set — run through Gradle " +
                        "(build.gradle.kts injects the published ws catalog version)"
                )

    private val publishedBomVersion: String
        get() =
            System.getProperty("hyperframes.publishedCatalog.bomVersion")
                ?: error(
                    "hyperframes.publishedCatalog.bomVersion is not set — run through Gradle " +
                        "(build.gradle.kts injects the published ws catalog version)"
                )

    @Test
    fun `plugin version matches root consumer catalog version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val versionLine =
            buildScript
                .lineSequence()
                .first { it.trimStart().startsWith("version =") }

        // MEM-CAT-ROLLOUT-4 (D3) — self version derived from the published workspace catalog.
        assertThat(versionLine)
            .withFailMessage("build.gradle.kts version must derive from the published workspace catalog (ws.versions.hyperframes.plugin)")
            .contains("ws.versions.hyperframes.plugin.get()")

        // Hygiene (D5): local toml self version must match the published ws catalog version.
        val pluginCatalogVersion = hyperframesVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8))

        assertThat(pluginCatalogVersion)
            .withFailMessage("plugin catalog hyperframes-plugin version ($pluginCatalogVersion) must match ws catalog hyperframes-plugin version ($publishedPluginVersion)")
            .isEqualTo(publishedPluginVersion)
    }

    @Test
    fun `workspace bom platform pin matches ws catalog bom version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)

        assertThat(buildScript)
            .withFailMessage("workspace-bom platform pin must use the ws catalog BOM version ($publishedBomVersion)")
            .contains("""platform("education.cccp:workspace-bom:$publishedBomVersion")""")
    }

    private fun hyperframesVersionFrom(content: String): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("hyperframes-plugin =") || it.startsWith("hyperframes =") }
            .substringAfter("\"")
            .substringBefore("\"")

    @Test
    fun `plugin group and id are stable for publication`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val pluginId =
            pluginDir
                .resolve("gradle/libs.versions.toml")
                .readText(UTF_8)
                .lineSequence()
                .filter { it.contains("id = \"education.cccp.hyperframes\"") }
                .first()
                .substringAfter("id = \"")
                .substringBefore("\"")

        assertThat(buildScript).contains("group = \"education.cccp\"")
        assertThat(pluginId).isEqualTo("education.cccp.hyperframes")
    }
}