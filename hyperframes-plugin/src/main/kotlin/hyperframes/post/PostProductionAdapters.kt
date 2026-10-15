package hyperframes.post

import java.io.File
import video.kernel.AudioPostProduction
import video.kernel.AudioPostSettings
import video.kernel.SubtitleBurnIn
import video.kernel.TextToSpeech

/**
 * N2 implementations of the shared N1 video ports for the HyperFrames engine
 * (VK-4 US-4b→d): hyperframes is the **second consumer** of the post-production
 * and narration capabilities alongside capsule.
 *
 * Each adapter delegates to an external binary (FFmpeg / espeak) through the
 * test-injectable [CommandRunner] and **degrades to `false`** on any failure —
 * the kernel ports never throw (a build must never fail because an optional
 * post-production tool is unavailable).
 */

/** FFmpeg-backed [SubtitleBurnIn] (US-4c). */
class FfmpegSubtitleBurnIn(
    private val ffmpegPath: String = "ffmpeg",
    private val runner: CommandRunner = ProcessBuilderCommandRunner(),
    private val timeoutMillis: Long = 300_000L,
) : SubtitleBurnIn {

    override fun burnIn(video: File, subtitles: File, output: File): Boolean {
        if (!video.exists() || !subtitles.exists()) return false
        output.parentFile?.mkdirs()
        val result = runner.run(
            HyperframesSubtitleCommand.buildArgv(video, subtitles, output, ffmpegPath),
            output.parentFile ?: video.parentFile,
            timeoutMillis,
        )
        return result.exitCode == 0 && output.exists() && output.length() > 0L
    }
}

/** FFmpeg-backed [AudioPostProduction] (US-4b). */
class FfmpegAudioPostProduction(
    private val ffmpegPath: String = "ffmpeg",
    private val runner: CommandRunner = ProcessBuilderCommandRunner(),
    private val timeoutMillis: Long = 300_000L,
) : AudioPostProduction {

    override fun process(video: File, output: File, settings: AudioPostSettings): Boolean {
        if (!video.exists()) return false
        val bgm: File? = if (settings.bgmEnabled && settings.bgmFile.isNotBlank()) {
            File(settings.bgmFile).takeIf { it.exists() }
        } else {
            null
        }
        // Background bed requested but unusable → degrade (keep the original).
        if (settings.bgmEnabled && bgm == null) return false

        output.parentFile?.mkdirs()
        val result = runner.run(
            HyperframesAudioPostCommand.buildArgv(video, bgm, output, settings, ffmpegPath),
            output.parentFile ?: video.parentFile,
            timeoutMillis,
        )
        return result.exitCode == 0 && output.exists() && output.length() > 0L
    }
}

/**
 * espeak-backed [TextToSpeech] (US-4d) — local narration synthesis.
 *
 * The voice is a property of the adapter (a language-bound espeak voice),
 * never of the neutral port. Writes a WAV container; `""` blank text degrades.
 */
class EspeakTextToSpeech(
    private val executable: String = "espeak",
    private val voice: String = "",
    private val runner: CommandRunner = ProcessBuilderCommandRunner(),
    private val timeoutMillis: Long = 60_000L,
) : TextToSpeech {

    override fun synthesize(text: String, output: File): Boolean {
        if (text.isBlank()) return false
        output.parentFile?.mkdirs()
        val result = runner.run(
            HyperframesTtsCommand.buildArgv(executable, voice, output, text),
            output.parentFile ?: File("."),
            timeoutMillis,
        )
        return result.exitCode == 0 && output.exists() && output.length() > 0L
    }
}
