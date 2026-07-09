package hyperframes.template

/**
 * HF-5c — "data-chart" template: animated bar chart with bars that rise
 * from zero to their proportional height.
 *
 * Pure DDD value object — produces a valid HyperFrames HTML fragment,
 * composed of a `hyperframes-composition` block + a track carrying one
 * bar per [DataPoint] and a GSAP timeline that scales bars from `scaleY: 0`
 * to their natural height, staggered so bars rise one after another.
 * No side effects.
 *
 * @property id composition identifier (rendered as `data-composition-id`)
 * @property title chart title displayed above the bars
 * @property dataPoints the bars of the chart (label + value)
 * @property durationSeconds track duration in seconds (default: 4.0)
 */
data class DataChartTemplate(
    val id: String,
    val title: String,
    val dataPoints: List<DataPoint>,
    val durationSeconds: Double = 4.0
) {

    /**
     * Renders the HyperFrames HTML fragment.
     *
     * Returns a `.hyperframes-composition` block containing a single track
     * whose duration is [durationSeconds] (rounded to the nearest integer
     * for `data-duration`), the chart title, one bar div per data point
     * (height proportional to its value relative to the max), and a GSAP
     * timeline that animates bars from `scaleY: 0` to `scaleY: 1` with a
     * stagger so they rise one after another.
     */
    fun render(): String = buildString {
        val durationInt = durationSeconds.toInt()
        val maxValue = dataPoints.maxOfOrNull { it.value } ?: 0.0
        appendLine("<div class=\"sect1 hyperframes-composition\" data-composition-id=\"$id\">")
        appendLine("  <div class=\"sectionbody\">")
        appendLine("    <div class=\"paragraph hyperframes-track\" data-track-index=\"0\" data-start=\"0\" data-duration=\"$durationInt\">")
        appendLine("      <div class=\"hf-data-chart\">")
        appendLine("        <span class=\"hf-data-chart-title\">$title</span>")
        if (dataPoints.isEmpty()) {
            appendLine("        <div class=\"hf-data-chart-plot hf-data-chart-empty\"></div>")
        } else {
            appendLine("        <div class=\"hf-data-chart-plot\">")
            dataPoints.forEach { point ->
                val heightPercent = if (maxValue > 0) (point.value / maxValue * 100).toInt() else 0
                appendLine("          <div class=\"hf-data-chart-bar\" data-label=\"${point.label}\" data-value=\"${point.value}\" style=\"height:${heightPercent}%\">")
                appendLine("            <span class=\"hf-data-chart-label\">${point.label}</span>")
                appendLine("          </div>")
            }
            appendLine("        </div>")
        }
        appendLine("      </div>")
        appendLine("    </div>")
        appendLine("    <script type=\"text/javascript\">")
        appendLine("      window.__timelines = window.__timelines || {};")
        appendLine("      (function () {")
        appendLine("        const tl = gsap.timeline({ paused: true });")
        if (dataPoints.isNotEmpty()) {
            appendLine("        tl.from(\"#$id .hf-data-chart-bar\", { scaleY: 0, duration: 0.6, stagger: 0.15, transformOrigin: \"bottom\" }, 0.2);")
        }
        appendLine("        window.__timelines[\"$id\"] = tl;")
        appendLine("      })();")
        appendLine("    </script>")
        appendLine("  </div>")
        append("</div>")
    }
}

/**
 * One bar of a [DataChartTemplate] — a labelled numeric value.
 *
 * @property label the bar label displayed under the bar (non-blank)
 * @property value the bar value (non-negative); bar height is proportional
 *                 to this value relative to the chart max
 */
data class DataPoint(
    val label: String,
    val value: Double
) {
    init {
        require(label.isNotBlank()) { "DataPoint label must not be blank" }
        require(value >= 0.0) { "DataPoint value must be non-negative (got $value)" }
    }
}