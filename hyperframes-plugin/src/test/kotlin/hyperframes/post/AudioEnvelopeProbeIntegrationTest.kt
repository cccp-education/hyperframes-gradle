package hyperframes.post

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import video.kernel.audio.AudioSilenceDetector
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional test for [AudioEnvelopeProbe] (VK-4 US-4e) — real ffmpeg, real
 * WAV files, no Gradle TestKit.
 *
 * Skip-guarded: the probe is an optional quality signal, never a prerequisite
 * of the build.
 */
class AudioEnvelopeProbeIntegrationTest {
    @field:TempDir
    lateinit var tempDir: File

    private fun ffmpegOnPath(): Boolean =
        System.getenv("PATH")?.split(File.pathSeparator).orEmpty().any { entry ->
            File(entry, "ffmpeg").let { it.isFile && it.canExecute() }
        }

    private fun generate(
        fileName: String,
        source: String,
    ) {
        val output = File(tempDir, fileName)
        val process =
            ProcessBuilder(
                "ffmpeg",
                "-v",
                "error",
                "-y",
                "-f",
                "lavfi",
                "-i",
                source,
                "-t",
                "1",
                output.absolutePath,
            ).redirectErrorStream(true).start()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        assumeTrue(finished && process.exitValue() == 0) {
            "ffmpeg could not produce $fileName — envelope functional check not exercised here"
        }
    }

    @Test
    fun `a real silent wav is detected as silent`() {
        assumeTrue(ffmpegOnPath()) { "ffmpeg is required for the envelope functional check" }
        generate("silent.wav", "anullsrc=r=16000:cl=mono")

        val frames = AudioEnvelopeProbe().probe(File(tempDir, "silent.wav"))
        val envelope = requireNotNull(frames) { "expected an envelope for the silent wav" }

        assertTrue(envelope.isNotEmpty(), "expected at least one frame")
        assertTrue(AudioSilenceDetector.analyze(envelope).isSilent)
    }

    @Test
    fun `a real tone wav is not detected as silent`() {
        assumeTrue(ffmpegOnPath()) { "ffmpeg is required for the envelope functional check" }
        generate("tone.wav", "sine=frequency=440:sample_rate=16000:duration=1")

        val frames = AudioEnvelopeProbe().probe(File(tempDir, "tone.wav"))
        val envelope = requireNotNull(frames) { "expected an envelope for the tone wav" }

        assertTrue(envelope.isNotEmpty(), "expected at least one frame")
        assertFalse(AudioSilenceDetector.analyze(envelope).isSilent)
    }
}
