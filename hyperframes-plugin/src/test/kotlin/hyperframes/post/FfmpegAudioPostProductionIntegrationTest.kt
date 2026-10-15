package hyperframes.post

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import video.kernel.AudioPostSettings

/**
 * S-028 — real-FFmpeg integration guard for [FfmpegAudioPostProduction] (VK-4
 * US-4b / CAP-AUDIO parity).
 *
 * The hermetic unit tests only assert the argv strings; they cannot catch a
 * filtergraph that *runs* but produces a truncated stream. A dogfooding session
 * (S-028) found exactly that: `loudnorm` emits 192 kHz and a following `amix`
 * truncated the output to ~0.12 s, and the ducking branch reused the `[voice]`
 * label (ffmpeg error). This test runs the **real** FFmpeg on a generated video
 * + BGM and asserts the post-processed duration matches the input.
 *
 * Skip-guarded (`assumeTrue`): skipped wherever `ffmpeg` is absent (hermetic CI),
 * exercised locally and in `.github/workflows/real-render-hyperframes.yml`.
 */
class FfmpegAudioPostProductionIntegrationTest {

    @TempDir
    lateinit var tmp: Path

    private fun available(bin: String, arg: String): Boolean = try {
        ProcessBuilder(bin, arg).redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun run(vararg argv: String): Int =
        ProcessBuilder(*argv).redirectErrorStream(true).start().let { p ->
            p.inputStream.bufferedReader().readText(); p.waitFor()
        }

    private fun duration(file: File): Double = try {
        val p = ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=nw=1:nk=1", file.absolutePath,
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.toDoubleOrNull() ?: 0.0
    } catch (_: Exception) {
        0.0
    }

    @Test
    fun `audio post with BGM and ducking keeps the full duration`() {
        assumeTrue(available("ffmpeg", "-version"), "ffmpeg not available — skipped")
        assumeTrue(available("ffprobe", "-version"), "ffprobe not available — skipped")

        val video = tmp.resolve("in.mp4").toFile()
        val bgm = tmp.resolve("bgm.mp3").toFile()
        assertThat(run(
            "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=duration=3:size=320x180:rate=25",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.absolutePath,
        )).isEqualTo(0)
        assertThat(run("ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=3", "-c:a", "libmp3lame", bgm.absolutePath)).isEqualTo(0)

        val output = tmp.resolve("out.mp4").toFile()
        val ok = FfmpegAudioPostProduction().process(
            video, output,
            AudioPostSettings(bgmEnabled = true, bgmFile = bgm.absolutePath, duckingEnabled = true),
        )

        assertThat(ok).`as`("audio post should succeed with real ffmpeg").isTrue()
        assertThat(duration(output)).`as`("post-processed duration must match the input (no truncation)")
            .isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.5))
    }

    @Test
    fun `audio post loudness-only keeps the full duration`() {
        assumeTrue(available("ffmpeg", "-version"), "ffmpeg not available — skipped")
        assumeTrue(available("ffprobe", "-version"), "ffprobe not available — skipped")

        val video = tmp.resolve("in2.mp4").toFile()
        assertThat(run(
            "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=320x180:rate=25",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.absolutePath,
        )).isEqualTo(0)

        val output = tmp.resolve("out2.mp4").toFile()
        val ok = FfmpegAudioPostProduction().process(video, output, AudioPostSettings())

        assertThat(ok).isTrue()
        assertThat(duration(output)).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.5))
    }
}
