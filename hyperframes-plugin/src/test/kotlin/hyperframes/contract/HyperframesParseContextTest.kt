package hyperframes.contract

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [HyperframesParseContext] serialization contract N3.
 *
 * Baby-step TDD — these tests pin the JSON shape consumed by runner-gradle:
 * top-level fields `plugin`, `version`, `output`, `source`, `renderedAt`,
 * `renderDurationMs`, and nested video/source descriptors.
 */
class HyperframesParseContextTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `plugin id is always hyperframes-gradle`() {
        val ctx = sampleContext()
        assertEquals("hyperframes-gradle", ctx.plugin)
        assertEquals(HyperframesParseContext.PLUGIN_ID, ctx.plugin)
    }

    @Test
    fun `serializes to JSON with required top-level fields`() {
        val ctx = sampleContext()
        val json = mapper.writeValueAsString(ctx)
        val tree = mapper.readTree(json)
        assertEquals("hyperframes-gradle", tree["plugin"].asText())
        assertEquals("0.0.1", tree["version"].asText())
        assertTrue(tree.has("output"))
        assertTrue(tree.has("source"))
        assertTrue(tree.has("renderedAt"))
        assertTrue(tree.has("renderDurationMs"))
    }

    @Test
    fun `output descriptor contains video path and dimensions`() {
        val ctx = sampleContext()
        val json = mapper.writeValueAsString(ctx)
        val output = mapper.readTree(json)["output"]
        assertEquals("docker-kubernetes.mp4", output["video"].asText())
        assertEquals("/output/docker-kubernetes.mp4", output["path"].asText())
        assertEquals(1920, output["width"].asInt())
        assertEquals(1080, output["height"].asInt())
        assertEquals(30, output["fps"].asInt())
        assertEquals("h264", output["codec"].asText())
        assertTrue(output["sizeBytes"].asLong() > 0)
        assertTrue(output["durationSeconds"].asDouble() >= 0.0)
    }

    @Test
    fun `source descriptor contains asciidoc name compositions and tracks`() {
        val ctx = sampleContext()
        val json = mapper.writeValueAsString(ctx)
        val source = mapper.readTree(json)["source"]
        assertEquals("ma-formation.adoc", source["asciidoc"].asText())
        val compositions = source["compositions"]
        assertEquals(2, compositions.size())
        assertEquals("intro", compositions[0].asText())
        assertEquals("comparison", compositions[1].asText())
        assertEquals(5, source["tracks"].asInt())
    }

    @Test
    fun `deserializes back to equal instance`() {
        val ctx = sampleContext()
        val json = mapper.writeValueAsString(ctx)
        val back = mapper.readValue(json, HyperframesParseContext::class.java)
        assertEquals(ctx, back)
    }

    private fun sampleContext() = HyperframesParseContext(
        version = "0.0.1",
        output = HyperframesVideoOutput(
            video = "docker-kubernetes.mp4",
            path = "/output/docker-kubernetes.mp4",
            sizeBytes = 25165824L,
            durationSeconds = 60.0,
            width = 1920,
            height = 1080,
            fps = 30
        ),
        source = HyperframesAsciidocSource(
            asciidoc = "ma-formation.adoc",
            compositions = listOf("intro", "comparison"),
            tracks = 5
        ),
        renderedAt = "2026-05-31T14:30:00Z",
        renderDurationMs = 45230L
    )
}