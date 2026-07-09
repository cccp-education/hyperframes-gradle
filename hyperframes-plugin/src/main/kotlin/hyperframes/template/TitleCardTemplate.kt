package hyperframes.template

/**
 * HF-5a — "title-card" template: fade-in, logo, title and subtitle.
 *
 * Pure DDD value object — produces a valid HyperFrames HTML fragment,
 * composed of a `hyperframes-composition` block + a track carrying a
 * GSAP fade-in timeline. No side effects.
 *
 * @property id composition identifier (rendered as `data-composition-id`)
 * @property title main displayed text
 * @property subtitle secondary text displayed under the title
 * @property logoPath optional logo path (rendered as `<img src=...>`)
 * @property durationSeconds track duration in seconds (default: 2.0)
 */
data class TitleCardTemplate(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val logoPath: String? = null,
    val durationSeconds: Double = 2.0
) {

    /**
     * Renders the HyperFrames HTML fragment.
     *
     * Returns a `.hyperframes-composition` block containing a single track
     * whose duration is [durationSeconds] (rounded to the nearest integer
     * for `data-duration`), an optional visual, the title, the subtitle,
     * and a GSAP fade-in timeline injected via `window.__timelines`.
     */
    fun render(): String = buildString {
        appendLine("<div class=\"sect1 hyperframes-composition\" data-composition-id=\"$id\">")
        appendLine("  <div class=\"sectionbody\">")
        appendLine("    <div class=\"paragraph hyperframes-track\" data-track-index=\"0\" data-start=\"0\" data-duration=\"${durationSeconds.toInt()}\">")
        appendLine("      <p>")
        if (logoPath != null) {
            appendLine("        <img src=\"$logoPath\" alt=\"logo\" class=\"hf-title-card-logo\" />")
        }
        appendLine("        <span class=\"hf-title-card-title\">$title</span>")
        if (subtitle != null) {
            appendLine("        <span class=\"hf-title-card-subtitle\">$subtitle</span>")
        }
        appendLine("      </p>")
        appendLine("    </div>")
        appendLine("    <script type=\"text/javascript\">")
        appendLine("      window.__timelines = window.__timelines || {};")
        appendLine("      (function () {")
        appendLine("        const tl = gsap.timeline({ paused: true });")
        if (logoPath != null) {
            appendLine("        tl.from(\"#$id .hf-title-card-logo\", { opacity: 0, y: 30, duration: 0.4 }, 0);")
        }
        appendLine("        tl.from(\"#$id .hf-title-card-title\", { opacity: 0, y: 40, duration: 0.6 }, 0.2);")
        if (subtitle != null) {
            appendLine("        tl.from(\"#$id .hf-title-card-subtitle\", { opacity: 0, y: 20, duration: 0.5 }, 0.5);")
        }
        appendLine("        window.__timelines[\"$id\"] = tl;")
        appendLine("      })();")
        appendLine("    </script>")
        appendLine("  </div>")
        append("</div>")
    }
}