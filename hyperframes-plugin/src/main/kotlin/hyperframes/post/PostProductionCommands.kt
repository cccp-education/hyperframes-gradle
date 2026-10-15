package hyperframes.post

import java.io.File
import video.kernel.AudioPostSettings

/**
 * Pure builders for the FFmpeg argv of the hyperframes post-production adapters
 * (VK-4 US-4b→d). Pure objects — no I/O, fully testable.
 *
 * Duplicated from capsule's equivalent builders on purpose: the kernel carries
 * the **ports**, the engines carry their own **implementations** (verbatim N2
 * duplication is tolerated — doctrine S-162). hyperframes renders MP4 (AAC
 * audio), capsule renders WebM (Vorbis), so the encoder flags legitimately
 * differ.
 */
object HyperframesSubtitleCommand {

    /**
     * `ffmpeg -y -i <video> -vf subtitles=<subs> -c:a copy <out>`.
     *
     * The subtitle file (SRT/VTT) is passed directly to the `subtitles` filter;
     * FFmpeg auto-detects the format by extension. `-c:a copy` preserves the
     * audio stream unchanged.
     */
    fun buildArgv(video: File, subtitles: File, output: File, ffmpegPath: String): List<String> =
        listOf(
            ffmpegPath, "-y",
            "-i", video.absolutePath,
            "-vf", "subtitles=${subtitles.absolutePath}",
            "-c:a", "copy",
            output.absolutePath,
        )
}

object HyperframesAudioPostCommand {

    /**
     * Builds the FFmpeg filter graph: loudness normalization (EBU R128) always,
     * plus a background-music bed (`volume + amix`) when enabled, plus sidechain
     * ducking when enabled **and** a bed is present (ducking without a bed is
     * meaningless and ignored).
     */
    fun buildFilterGraph(settings: AudioPostSettings, hasBgmInput: Boolean): String {
        val loudness = "loudnorm=I=${settings.loudnessTargetLufs}:TP=-1.5:LRA=11"
        if (!(settings.bgmEnabled && hasBgmInput)) return loudness

        val bgmVolume = "volume=${settings.bgmLevelDb}dB"
        val amix = "amix=inputs=2:duration=first:dropout_transition=0"
        if (!settings.duckingEnabled) {
            return "[0:a]${loudness}[voice];[1:a]${bgmVolume}[bgm];[voice][bgm]${amix}[aout]"
        }
        val ducking = "sidechaincompress=threshold=0.1:ratio=4:attack=5:release=300"
        return "[0:a]${loudness}[voice];[1:a]${bgmVolume}[bgm];" +
            "[voice][bgm]${ducking}[ducked];[voice][ducked]${amix}[aout]"
    }

    /** Full argv; the background bed is a second `-i` when enabled. */
    fun buildArgv(
        video: File,
        bgmFile: File?,
        output: File,
        settings: AudioPostSettings,
        ffmpegPath: String,
    ): List<String> {
        val argv = mutableListOf(ffmpegPath, "-y", "-i", video.absolutePath)
        val hasBgm = settings.bgmEnabled && bgmFile != null
        if (hasBgm) {
            argv += listOf(
                "-i", bgmFile.absolutePath,
                "-filter_complex", buildFilterGraph(settings, hasBgmInput = true),
                "-map", "0:v", "-map", "[aout]",
            )
        } else {
            argv += listOf("-af", buildFilterGraph(settings, hasBgmInput = false))
        }
        argv += listOf("-c:v", "copy", "-c:a", "aac", "-shortest", output.absolutePath)
        return argv
    }
}

object HyperframesTtsCommand {

    /**
     * `espeak -v <voice> -w <output.wav> <text>` (voice omitted when blank).
     * The produced file is a WAV container regardless of the file name.
     */
    fun buildArgv(executable: String, voice: String, output: File, text: String): List<String> {
        val argv = mutableListOf(executable)
        if (voice.isNotBlank()) argv += listOf("-v", voice)
        argv += listOf("-w", output.absolutePath, text)
        return argv
    }
}
