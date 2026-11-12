package hyperframes.sound

import java.util.Locale

/** A music cue resolved to a real asset, ready to be emitted. */
data class ResolvedMusic(
    val placement: SoundPlacement,
    val src: String,
)

/** A one-shot effect resolved to a real asset, ready to be emitted. */
data class ResolvedSfx(
    val placement: SfxPlacement,
    val src: String,
)

/**
 * Emits the sound layer as `<audio>` clips of the composition
 * (HF-MUSIC-LIBRARY US-3).
 *
 * The runtime only drives **direct children of `#stage`**, so every element is a
 * sibling of the scenes. Music occupies its own track range (`120+`) and effects
 * another (`140+`) so they never collide with the historical
 * `[.hyperframes-audio]` cues (`90+`), which stay byte-identical.
 */
object SoundHtmlRenderer {
    /** First track index of the music cues (legacy audio cues use `90+`). */
    const val MUSIC_TRACK_INDEX: Int = 120

    /** First track index of the one-shot effects. */
    const val SFX_TRACK_INDEX: Int = 140

    fun renderMusic(entries: List<ResolvedMusic>): String =
        entries
            .mapIndexed { index, entry ->
                val declaration = entry.placement.declaration
                buildString {
                    append("<audio id=\"hf-music-$index\" class=\"hyperframes-music\" ")
                    append("src=\"${escape(entry.src)}\" ")
                    append("data-start=\"${number(entry.placement.startSeconds)}\" ")
                    append("data-duration=\"${number(entry.placement.durationSeconds)}\" ")
                    append("data-track-index=\"${MUSIC_TRACK_INDEX + index}\" ")
                    append("data-volume=\"${number(declaration.volume)}\" ")
                    append("data-loop=\"${declaration.loop}\"")
                    if (declaration.fadeInSeconds > 0.0) {
                        append(" data-fade-in=\"${number(declaration.fadeInSeconds)}\"")
                    }
                    if (declaration.fadeOutSeconds > 0.0) {
                        append(" data-fade-out=\"${number(declaration.fadeOutSeconds)}\"")
                    }
                    append("></audio>")
                }
            }.joinToString("\n")

    fun renderSfx(entries: List<ResolvedSfx>): String =
        entries
            .mapIndexed { index, entry ->
                val declaration = entry.placement.declaration
                "<audio id=\"hf-sfx-$index\" class=\"hyperframes-sfx\" " +
                    "src=\"${escape(entry.src)}\" " +
                    "data-start=\"${number(entry.placement.startSeconds)}\" " +
                    "data-track-index=\"${SFX_TRACK_INDEX + index}\" " +
                    "data-volume=\"${number(declaration.volume)}\"></audio>"
            }.joinToString("\n")

    private fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
