package hyperframes.template

/**
 * HF-5d — "kinetic-captions" template: timed subtitles that appear at their
 * start time and disappear at their end time, animated via a GSAP timeline.
 *
 * Pure DDD value object — produces a valid HyperFrames HTML fragment,
 * composed of a `hyperframes-composition` block + a track carrying one
 * caption div per [Caption] (hidden by default) and a GSAP timeline that
 * fades each caption in at its start and out at its end. No side effects.
 *
 * @property id composition identifier (rendered as `data-composition-id`)
 * @property captions the timed captions of the track
 * @property durationSeconds track duration in seconds (default: 5.0)
 */
data class KineticCaptionsTemplate(
    val id: String,
    val captions: List<Caption>,
    val durationSeconds: Double = 5.0
) {

    /**
     * Renders the HyperFrames HTML fragment.
     *
     * Returns a `.hyperframes-composition` block containing a single track
     * whose duration is [durationSeconds] (rounded to the nearest integer
     * for `data-duration`), one caption div per [Caption] (hidden by default
     * via `display:none`), and a GSAP timeline that fades each caption in
     * at its start time and out at its end time.
     */
    fun render(): String = buildString {
        val durationInt = durationSeconds.toInt()
        appendLine("<div class=\"sect1 hyperframes-composition\" data-composition-id=\"$id\">")
        appendLine("  <div class=\"sectionbody\">")
        appendLine("    <div class=\"paragraph hyperframes-track\" data-track-index=\"0\" data-start=\"0\" data-duration=\"$durationInt\">")
        appendLine("      <div class=\"hf-kinetic-captions\">")
        if (captions.isEmpty()) {
            appendLine("        <div class=\"hf-kinetic-captions-empty\"></div>")
        } else {
            captions.forEach { caption ->
                appendLine("        <div class=\"hf-kinetic-caption\" data-start=\"${caption.start}\" data-end=\"${caption.end}\" style=\"display:none\">${caption.text}</div>")
            }
        }
        appendLine("      </div>")
        appendLine("    </div>")
        appendLine("    <script type=\"text/javascript\">")
        appendLine("      window.__timelines = window.__timelines || {};")
        appendLine("      (function () {")
        appendLine("        const tl = gsap.timeline({ paused: true });")
        captions.forEach { caption ->
            appendLine("        tl.to(\"#$id .hf-kinetic-caption[data-start=\\\"${caption.start}\\\"]\", { opacity: 1, display: \"block\", duration: 0.2 }, ${caption.start});")
            appendLine("        tl.to(\"#$id .hf-kinetic-caption[data-end=\\\"${caption.end}\\\"]\", { opacity: 0, duration: 0.2 }, ${caption.end});")
        }
        appendLine("        window.__timelines[\"$id\"] = tl;")
        appendLine("      })();")
        appendLine("    </script>")
        appendLine("  </div>")
        append("</div>")
    }
}

/**
 * One timed caption of a [KineticCaptionsTemplate].
 *
 * @property start the time in seconds at which the caption appears (non-negative)
 * @property end the time in seconds at which the caption disappears (>= start)
 * @property text the caption text displayed on screen (non-blank)
 */
data class Caption(
    val start: Double,
    val end: Double,
    val text: String
) {
    init {
        require(start >= 0.0) { "Caption start must be non-negative (got $start)" }
        require(end >= start) { "Caption end must be >= start (got end=$end, start=$start)" }
        require(text.isNotBlank()) { "Caption text must not be blank" }
    }
}