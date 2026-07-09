package hyperframes.contract

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HyperframesContextExtractor] — pure domain service.
 *
 * Baby-step TDD — pins the extraction contract: stage dimensions, compositions,
 * tracks count, MP4 file metadata, AsciiDoc source descriptor.
 */
class HyperframesContextExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    private val extractor = HyperframesContextExtractor(pluginVersion = "0.0.1")

    @Test
    fun `extracts stage dimensions from data-width height fps`() {
        val html = sampleHtml(width = 1280, height = 720, fps = 24)
        val mp4 = fakeMp4("intro.mp4")
        val ctx = extractor.extract(
            html = html,
            mp4 = mp4,
            asciidocName = "intro.adoc",
            renderedAt = "2026-05-31T14:30:00Z",
            renderDurationMs = 1000L
        )
        assertEquals(1280, ctx.output.width)
        assertEquals(720, ctx.output.height)
        assertEquals(24, ctx.output.fps)
    }

    @Test
    fun `defaults to 1920x1080 30fps when stage missing`() {
        val html = "<html><body>no stage</body></html>"
        val mp4 = fakeMp4("video.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals(1920, ctx.output.width)
        assertEquals(1080, ctx.output.height)
        assertEquals(30, ctx.output.fps)
    }

    @Test
    fun `collects composition ids from data-composition-id attrs`() {
        val html = sampleHtml(compositions = listOf("intro", "comparison", "intro"))
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals(listOf("intro", "comparison"), ctx.source.compositions)
    }

    @Test
    fun `counts distinct track indexes from data-track-index attrs`() {
        val html = sampleHtml(tracksCount = 5)
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals(5, ctx.source.tracks)
    }

    @Test
    fun `mp4 descriptor carries name path sizeBytes`() {
        val mp4 = fakeMp4("docker-kubernetes.mp4", content = ByteArray(1024))
        val ctx = extractor.extract(sampleHtml(), mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals("docker-kubernetes.mp4", ctx.output.video)
        assertEquals(mp4.absolutePath, ctx.output.path)
        assertEquals(1024L, ctx.output.sizeBytes)
    }

    @Test
    fun `missing mp4 file yields zero sizeBytes`() {
        val missingMp4 = tempDir.toFile().resolve("does-not-exist.mp4")
        val ctx = extractor.extract(sampleHtml(), missingMp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals(0L, ctx.output.sizeBytes)
        assertEquals("does-not-exist.mp4", ctx.output.video)
    }

    @Test
    fun `carries asciidoc name and renderedAt and renderDuration`() {
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(sampleHtml(), mp4, "ma-formation.adoc", "2026-07-06T10:00:00Z", 45230L)
        assertEquals("ma-formation.adoc", ctx.source.asciidoc)
        assertEquals("2026-07-06T10:00:00Z", ctx.renderedAt)
        assertEquals(45230L, ctx.renderDurationMs)
        assertEquals("0.0.1", ctx.version)
    }

    @Test
    fun `codec defaults to h264`() {
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(sampleHtml(), mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)
        assertEquals("h264", ctx.output.codec)
    }

    // ──────────────────────────────────────────────
    // HF-7f — Pronunciation section in metadata.json
    // ──────────────────────────────────────────────

    @Test
    fun `pronunciation section is null when the HTML has no pronunciation island`() {
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(sampleHtml(), mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)

        assertNull(ctx.pronunciation, "pronunciation should be null when no island is present")
    }

    @Test
    fun `pronunciation section is extracted from the JSON island`() {
        val html = sampleHtml() + """
            <script type="application/json" id="hf-pronunciation">[{"word":"dos","phonetic":"do"}]</script>
        """.trimIndent()
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)

        val pronunciation = ctx.pronunciation
        assertTrue(pronunciation != null, "pronunciation section should be present")
        assertEquals(1, pronunciation.hintsCount)
        assertEquals("dos", pronunciation.hints[0].word)
        assertEquals("do", pronunciation.hints[0].phonetic)
    }

    @Test
    fun `pronunciation section with language tags extracts the language field`() {
        val html = sampleHtml() + """
            <script type="application/json" id="hf-pronunciation">[{"word":"dos","phonetic":"do","language":"fr"}]</script>
        """.trimIndent()
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)

        assertEquals("fr", ctx.pronunciation?.hints?.get(0)?.language)
    }

    @Test
    fun `pronunciation section hints count matches the number of hints`() {
        val html = sampleHtml() + """
            <script type="application/json" id="hf-pronunciation">[{"word":"dos","phonetic":"do"},{"word":"kubernetes","phonetic":"koobernetayz"}]</script>
        """.trimIndent()
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)

        assertEquals(2, ctx.pronunciation?.hintsCount)
        assertEquals(2, ctx.pronunciation?.hints?.size)
    }

    @Test
    fun `pronunciation section empty island yields zero hints`() {
        val html = sampleHtml() + """
            <script type="application/json" id="hf-pronunciation">[]</script>
        """.trimIndent()
        val mp4 = fakeMp4("v.mp4")
        val ctx = extractor.extract(html, mp4, "x.adoc", "2026-05-31T14:30:00Z", 0L)

        assertEquals(0, ctx.pronunciation?.hintsCount)
        assertTrue(ctx.pronunciation?.hints?.isEmpty() == true)
    }

    private fun fakeMp4(name: String, content: ByteArray = ByteArray(256)): File {
        val f = tempDir.toFile().resolve(name)
        f.writeBytes(content)
        return f
    }

    private fun sampleHtml(
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 30,
        compositions: List<String> = listOf("intro"),
        tracksCount: Int = 1
    ): String {
        val compDivs = compositions.joinToString("\n") { id ->
            """<div class="sect1 hyperframes-composition" data-composition-id="$id"><h2 id="$id">$id</h2></div>"""
        }
        val trackDivs = (0 until tracksCount).joinToString("\n") { idx ->
            """<div class="paragraph hyperframes-track" data-track-index="$idx" data-start="0" data-duration="5"></div>"""
        }
        return """
            <html><body>
            <div id="stage" data-width="$width" data-height="$height" data-fps="$fps" data-output="video.mp4">
            $compDivs
            $trackDivs
            </div>
            </body></html>
        """.trimIndent()
    }
}