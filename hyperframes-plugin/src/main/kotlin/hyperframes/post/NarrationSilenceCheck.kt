package hyperframes.post

import java.io.File
import video.kernel.audio.AudioSilenceDetector

/**
 * Pure consumer of the shared N1 silence detector for HyperFrames narration
 * (VK-4 US-4e).
 *
 * `generateHyperframesNarration` synthesises `narration-<n>.wav`; a NoOp/broken
 * TTS can leave a technically valid but mute file. This check turns the shared
 * [AudioSilenceDetector] into a file verdict without any I/O: the caller injects
 * the envelope probe, so the decision is unit-testable.
 *
 * A `null` envelope (probe unavailable) is **not** flagged — "cannot check"
 * must never be read as "silent".
 */
object NarrationSilenceCheck {

    /**
     * @param files the narration files to inspect.
     * @param probe per-file RMS envelope provider (`null` when unavailable).
     * @return the silent files, in input order.
     */
    fun silentFiles(files: List<File>, probe: (File) -> DoubleArray?): List<File> =
        files.filter { file -> isSilent(file, probe) }

    /** `true` only when a usable envelope proves the file silent. */
    fun isSilent(file: File, probe: (File) -> DoubleArray?): Boolean =
        probe(file)?.let { frames -> AudioSilenceDetector.analyze(frames).isSilent } == true
}
