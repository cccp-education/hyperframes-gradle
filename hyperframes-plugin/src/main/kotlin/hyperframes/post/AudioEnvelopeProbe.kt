package hyperframes.post

import video.kernel.audio.RmsEnvelope
import java.io.File

/**
 * N2 adapter that decodes a narration audio file to a per-frame RMS envelope
 * (VK-4 US-4e).
 *
 * The **decision** (silence) lives in the shared N1
 * [video.kernel.audio.AudioSilenceDetector]; this adapter only supplies the
 * frames. It mirrors capsule's `AudioEnvelopeExtractor` on purpose: the kernel
 * carries the pure capability, each engine keeps its own external-process
 * coupling (doctrine S-162, verbatim N2 duplication tolerated).
 *
 * Degrades to `null` on any failure (missing file, ffmpeg absent, empty output):
 * the silence check is then skipped, never read as clean.
 */
class AudioEnvelopeProbe(
    private val ffmpegPath: String = "ffmpeg",
    private val runner: CommandRunner = ProcessBuilderCommandRunner(),
    private val timeoutMillis: Long = 300_000L,
) {
    /**
     * @param audioFile the narration file (WAV/MP3/…) to analyse.
     * @return one normalised RMS amplitude per 20 ms frame, or `null`.
     */
    fun probe(audioFile: File): DoubleArray? {
        if (!audioFile.isFile || audioFile.length() == 0L) return null
        val pcmFile = File.createTempFile("hyperframes-envelope", ".pcm")
        return try {
            val result =
                runner.run(
                    decodeArgs(audioFile, pcmFile, SAMPLE_RATE, ffmpegPath),
                    pcmFile.parentFile ?: File("."),
                    timeoutMillis,
                )
            if (result.exitCode != 0 || !pcmFile.isFile || pcmFile.length() == 0L) {
                null
            } else {
                RmsEnvelope.framesFromPcm(
                    pcm = pcmFile.readBytes(),
                    bytesPerSample = 2,
                    frameSamples = SAMPLE_RATE * FRAME_MILLIS / 1000,
                )
            }
        } finally {
            pcmFile.delete()
        }
    }

    /**
     * The ffmpeg argv that decodes [audioFile] to raw s16le mono PCM in
     * [pcmFile]. Exposed for tests — the decode command is part of the contract.
     * `-y` is mandatory: [File.createTempFile] already created the target, and
     * without `-y` ffmpeg exits 0 with an empty file (real bug caught in S-209).
     */
    internal fun decodeArgs(
        audioFile: File,
        pcmFile: File,
        sampleRate: Int,
        ffmpegPath: String,
    ): List<String> =
        listOf(
            ffmpegPath,
            "-v",
            "error",
            "-y",
            "-i",
            audioFile.absolutePath,
            "-ac",
            "1",
            "-ar",
            sampleRate.toString(),
            "-f",
            "s16le",
            pcmFile.absolutePath,
        )

    companion object {
        /** 16 kHz mono is the usual ASR/envelope working rate and keeps frames tiny. */
        const val SAMPLE_RATE: Int = 16_000

        /** 20 ms frames — short enough to isolate a breath, long enough to be stable. */
        const val FRAME_MILLIS: Int = 20
    }
}
