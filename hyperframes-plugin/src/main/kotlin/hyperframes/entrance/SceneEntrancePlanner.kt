package hyperframes.entrance

import java.util.Locale

/**
 * HF-DEMO-QUALITY US-5 — pure planner for choreographed scene entrances.
 *
 * Produces the two GSAP statements the scene timeline needs:
 *
 * 1. a **load-time** `gsap.set` hiding the scene's content atoms (so the first
 *    rendered frame never shows the final state before the tween runs);
 * 2. a **staggered** `t.to` revealing them in document order.
 *
 * The selectors are scoped to the scene id, so two scenes never animate each
 * other's content. No I/O, no Gradle, no clock.
 */
object SceneEntrancePlanner {

    /**
     * Content atoms animated independently. Structural wrappers
     * (`.sectionbody`, `.paragraph`, `ul`) are deliberately excluded: animating
     * both a wrapper and its children would double the motion and desynchronise
     * the reveal.
     */
    val ATOM_SELECTORS: List<String> = listOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "li", "pre", "blockquote", "img", "table", "figure",
        ".hf-title-card-logo", ".hf-title-card-title", ".hf-title-card-subtitle",
        ".hf-data-chart-title", ".hf-data-chart-col",
        ".hf-kinetic-caption",
    )

    /** `gsap.set` statement establishing the hidden start state at load time. */
    fun setupScript(sceneId: String, config: EntranceConfig): String {
        if (!config.enabled) return ""
        return "    var items = gsap.utils.toArray(\"${selector(sceneId)}\");\n" +
            "    gsap.set(items, { opacity: 0, y: ${number(config.yOffset)} });"
    }

    /** Staggered `t.to` statement revealing the atoms in document order. */
    fun tweenScript(sceneId: String, config: EntranceConfig): String {
        if (!config.enabled) return ""
        return "    t.to(items, { opacity: 1, y: 0, duration: ${number(config.durationSeconds)}, " +
            "ease: \"power3.out\", stagger: ${number(config.staggerSeconds)} }, ${number(config.offsetSeconds)});"
    }

    /** Scene-scoped selector list (one entry per atom kind). */
    fun selector(sceneId: String): String =
        ATOM_SELECTORS.joinToString(", ") { "#$sceneId .scene-inner $it" }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)
}
