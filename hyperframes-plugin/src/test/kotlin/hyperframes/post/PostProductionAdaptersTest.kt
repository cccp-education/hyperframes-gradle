package hyperframes.post

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import video.kernel.AudioPostSettings

/**
 * N2 adapters of the shared N1 ports (VK-4 US-4b→d) — hermetic, via a fake
 * [CommandRunner] (never a real FFmpeg / espeak).
 */
class PostProductionAdaptersTest {

    @TempDir
    lateinit var tmp: Path

    /** Fake runner that writes the output file (last argv item) and returns exit 0. */
    private fun writingRunner(): CommandRunner = CommandRunner { argv, _, _ ->
        File(argv.last()).apply { parentFile?.mkdirs() }.writeText("produced")
        CommandResult(exitCode = 0)
    }

    private fun failingRunner(): CommandRunner = CommandRunner { _, _, _ -> CommandResult(exitCode = 1, output = "boom") }

    @Test
    fun `subtitle burn-in produces the output`() {
        val video = tmp.resolve("in.mp4").toFile().apply { writeText("v") }
        val subs = tmp.resolve("s.vtt").toFile().apply { writeText("s") }
        val output = tmp.resolve("out.mp4").toFile()

        assertThat(FfmpegSubtitleBurnIn(runner = writingRunner()).burnIn(video, subs, output)).isTrue()
        assertThat(output).exists()
    }

    @Test
    fun `subtitle burn-in degrades on missing input`() {
        val output = tmp.resolve("out.mp4").toFile()

        assertThat(
            FfmpegSubtitleBurnIn(runner = writingRunner())
                .burnIn(tmp.resolve("absent.mp4").toFile(), tmp.resolve("s.vtt").toFile(), output)
        ).isFalse()
    }

    @Test
    fun `subtitle burn-in degrades on command failure`() {
        val video = tmp.resolve("in.mp4").toFile().apply { writeText("v") }
        val subs = tmp.resolve("s.vtt").toFile().apply { writeText("s") }

        assertThat(
            FfmpegSubtitleBurnIn(runner = failingRunner())
                .burnIn(video, subs, tmp.resolve("out.mp4").toFile())
        ).isFalse()
    }

    @Test
    fun `audio post produces the output`() {
        val video = tmp.resolve("in.mp4").toFile().apply { writeText("v") }
        val output = tmp.resolve("out.mp4").toFile()

        assertThat(FfmpegAudioPostProduction(runner = writingRunner()).process(video, output, AudioPostSettings())).isTrue()
        assertThat(output).exists()
    }

    @Test
    fun `audio post degrades when a requested bed is missing`() {
        val video = tmp.resolve("in.mp4").toFile().apply { writeText("v") }

        assertThat(
            FfmpegAudioPostProduction(runner = writingRunner())
                .process(video, tmp.resolve("out.mp4").toFile(), AudioPostSettings(bgmEnabled = true, bgmFile = "/absent.mp3"))
        ).isFalse()
    }

    @Test
    fun `audio post degrades on missing video`() {
        assertThat(
            FfmpegAudioPostProduction(runner = writingRunner())
                .process(tmp.resolve("absent.mp4").toFile(), tmp.resolve("out.mp4").toFile(), AudioPostSettings())
        ).isFalse()
    }

    @Test
    fun `tts synthesizes non-blank text`() {
        val output = tmp.resolve("n.wav").toFile()

        assertThat(EspeakTextToSpeech(runner = writingRunner()).synthesize("bonjour", output)).isTrue()
        assertThat(output).exists()
    }

    @Test
    fun `tts degrades on blank text`() {
        assertThat(EspeakTextToSpeech(runner = writingRunner()).synthesize("   ", tmp.resolve("n.wav").toFile())).isFalse()
    }

    @Test
    fun `tts degrades on command failure`() {
        assertThat(EspeakTextToSpeech(runner = failingRunner()).synthesize("bonjour", tmp.resolve("n.wav").toFile())).isFalse()
    }
}
