package hyperframes.media

import java.util.Locale

/**
 * Renders a [BackgroundImage] as the full-bleed layer child of a scene
 * (HF-DEMO-QUALITY US-6).
 *
 * The layer is decorative (`aria-hidden="true"`, `alt=""`): it sits behind the
 * `.scene-inner` content (z-index owned by the composition stylesheet) and never
 * carries semantics. The relative `src` is copied next to the composition by
 * [hyperframes.ReferencedAssets], which already scans `<img src>`.
 *
 * Pure — no I/O, no Gradle.
 */
object BackgroundImageHtml {
    /** The `hf-bg` fragment, without a trailing newline. */
    fun render(background: BackgroundImage): String =
        buildString {
            appendLine("""<div class="hf-bg" aria-hidden="true">""")
            appendLine(
                """  <img class="hf-bg-media" src="${escape(background.file)}" alt="" """ +
                    """style="opacity:${number(background.opacity)}">""",
            )
            if (background.scrim) appendLine("""  <div class="hf-bg-scrim"></div>""")
            if (background.grid) appendLine("""  <div class="hf-bg-grid"></div>""")
            if (background.grain) appendLine("""  <div class="hf-bg-grain"></div>""")
            append("</div>")
        }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
