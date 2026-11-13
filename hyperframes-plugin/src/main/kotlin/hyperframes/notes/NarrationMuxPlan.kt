package hyperframes.notes

/**
 * One narration take aligned on the timeline (HF-SPEAKER-NOTES US-5).
 *
 * @property file absolute path of the synthesized take (`narration-<n>.wav`).
 * @property startSeconds scene start on the composition timeline (from the
 *   `data-narration-start` anchor emitted per scene).
 */
data class NarrationTake(
    val file: String,
    val startSeconds: Double,
)

/**
 * The takes to mux over the rendered video, in scene order.
 *
 * An empty plan means the composition authors no speaker note (historical
 * document): the mux step must carry the rendered MP4 through untouched.
 */
data class NarrationMuxPlan(
    val takes: List<NarrationTake> = emptyList(),
) {
    fun isEmpty(): Boolean = takes.isEmpty()

    val size: Int
        get() = takes.size
}

/**
 * HF-SPEAKER-NOTES US-5 — aligns the synthesized takes with the composition
 * timeline.
 *
 * The processor anchors every narration marker with `data-narration-start`
 * (the scene start); the takes are produced in the same document order, so the
 * n-th take belongs to the n-th anchored marker. A marker without a numeric
 * anchor, or a take without a marker, is ignored (the plan can only be
 * shorter — never misaligned).
 *
 * Pure transform — no I/O, no FFmpeg.
 */
object NarrationMuxPlanner {
    private val markerRegex =
        Regex("""<div[^>]*class="[^"]*hyperframes-narration[^"]*"[^>]*>""")
    private val startRegex = Regex("""data-narration-start="([0-9]+(?:\.[0-9]+)?)"""")

    /**
     * @param html the enriched composition carrying the narration anchors.
     * @param takeFiles the take files, in scene order.
     * @return the aligned plan (empty when nothing can be muxed).
     */
    fun plan(
        html: String,
        takeFiles: List<String>,
    ): NarrationMuxPlan {
        if (takeFiles.isEmpty()) return NarrationMuxPlan()
        val starts =
            markerRegex
                .findAll(html)
                .mapNotNull { marker ->
                    startRegex
                        .find(marker.value)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull()
                }.toList()
        val aligned = starts.zip(takeFiles).map { (start, file) -> NarrationTake(file = file, startSeconds = start) }
        return NarrationMuxPlan(aligned)
    }
}

/**
 * HF-SPEAKER-NOTES US-5 — pure builder of the FFmpeg argv that muxes the
 * narration takes over the rendered video.
 *
 * Each take is delayed to its scene start (`adelay`), the takes are mixed
 * (`amix`, no normalization so the narration level is preserved) and the mixed
 * track is padded with silence (`apad`) so the **video always bounds** the
 * result: a longer take is trimmed, a shorter narration never truncates the
 * last scene (`-shortest` on a padded track = the video duration). The video
 * stream is **copied** (no re-encode).
 */
object NarrationMuxCommand {
    /**
     * @param video the rendered MP4.
     * @param output the muxed MP4.
     * @param plan the aligned takes.
     * @param ffmpegPath the `ffmpeg` executable.
     * @return the argv, ready for the process runner.
     */
    fun buildArgv(
        video: String,
        output: String,
        plan: NarrationMuxPlan,
        ffmpegPath: String = "ffmpeg",
    ): List<String> {
        require(!plan.isEmpty()) { "NarrationMuxCommand requires at least one take" }
        val argv = mutableListOf(ffmpegPath, "-y", "-i", video)
        plan.takes.forEach { argv.addAll(listOf("-i", it.file)) }
        argv.addAll(
            listOf(
                "-filter_complex",
                filterGraph(plan),
                "-map",
                "0:v:0",
                "-map",
                "[aout]",
                "-c:v",
                "copy",
                "-c:a",
                "aac",
                "-shortest",
                output,
            ),
        )
        return argv
    }

    /** `[1:a]adelay=…,apad[aout]` / `…amix=…,apad[aout]`. */
    fun filterGraph(plan: NarrationMuxPlan): String {
        require(!plan.isEmpty()) { "NarrationMuxCommand requires at least one take" }

        fun delay(index: Int): String {
            val millis = Math.round(plan.takes[index].startSeconds * 1000.0)
            return "[${index + 1}:a]adelay=$millis|$millis"
        }

        if (plan.takes.size == 1) return delay(0) + ",apad[aout]"
        val delayed = plan.takes.indices.map { delay(it) + "[a$it]" }
        val labels = plan.takes.indices.joinToString("") { "[a$it]" }
        val mix = "amix=inputs=${plan.takes.size}:normalize=0,apad[aout]"
        return (delayed + "$labels$mix").joinToString(";")
    }
}
