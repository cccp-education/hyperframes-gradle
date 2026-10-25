package hyperframes

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * HF-DEMO-QUALITY US-3 — referenced asset extraction and copy.
 */
class ReferencedAssetsTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `extract collects distinct relative img and audio sources`() {
        val html = """
            <img src="images/a.png" alt="a">
            <audio src="assets/tone.mp3" data-start="0"></audio>
            <img src="images/a.png" alt="duplicate">
            <img src="https://example.com/remote.png" alt="remote">
            <img src="data:image/png;base64,AAAA" alt="inline">
            <img src="/absolute.png" alt="absolute">
        """.trimIndent()

        assertThat(ReferencedAssets.extract(html))
            .containsExactly("images/a.png", "assets/tone.mp3")
    }

    @Test
    fun `copy preserves the relative path and the bytes`() {
        val input = File(tempDir, "in").apply { mkdirs() }
        val output = File(tempDir, "out").apply { mkdirs() }
        File(input, "images").mkdirs()
        File(input, "images/logo.png").writeText("PNG-BYTES")
        File(input, "assets").mkdirs()
        File(input, "assets/tone.mp3").writeText("MP3-BYTES")

        val html = """<img src="images/logo.png"><audio src="assets/tone.mp3"></audio>"""
        val copied = ReferencedAssets.copy(html, input, output)

        assertThat(copied).hasSize(2)
        assertThat(File(output, "images/logo.png").readText()).isEqualTo("PNG-BYTES")
        assertThat(File(output, "assets/tone.mp3").readText()).isEqualTo("MP3-BYTES")
    }

    @Test
    fun `copy skips a missing file and a path traversal`() {
        val input = File(tempDir, "in").apply { mkdirs() }
        val output = File(tempDir, "out").apply { mkdirs() }
        File(tempDir, "secret.txt").writeText("SECRET")

        val html = """<img src="missing.png"><img src="../secret.txt">"""
        val copied = ReferencedAssets.copy(html, input, output)

        assertThat(copied).isEmpty()
        assertThat(File(output, "missing.png")).doesNotExist()
    }
}
