package hyperframes.media

/**
 * Parses the listing block of a `[.hyperframes-background]` scene (HF-DEMO-QUALITY
 * US-6).
 *
 * DSL — one `key: value` per line:
 *
 * ```
 * file: assets/img/fiber.jpg
 * opacity: 0.55
 * scrim: true
 * grid: false
 * grain: false
 * ```
 *
 * Tolerant by design (a typo never breaks the build): blank lines and `#`
 * comments are ignored, unknown keys are skipped, an invalid/out-of-range
 * opacity/parallax falls back to its default. A missing or blank `file` cancels
 * the background (`null`).
 *
 * Pure — no I/O, no Gradle.
 */
object BackgroundImageParser {

    /** Parses [listing], or `null` when it does not declare a usable file. */
    fun parse(listing: String): BackgroundImage? {
        val values = LinkedHashMap<String, String>()
        for (line in listing.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val separator = trimmed.indexOf(':')
            if (separator <= 0) continue
            val key = trimmed.substring(0, separator).trim().lowercase()
            val value = trimmed.substring(separator + 1).trim()
            if (key.isNotEmpty()) values[key] = value
        }

        val file = values["file"]?.takeIf { it.isNotBlank() } ?: return null
        return BackgroundImage(
            file = file,
            opacity = values["opacity"]?.toDoubleOrNull()
                ?.takeIf { it in BackgroundImage.MIN_OPACITY..BackgroundImage.MAX_OPACITY }
                ?: BackgroundImage.DEFAULT_OPACITY,
            scrim = values["scrim"]?.let(::boolean) ?: true,
            grid = values["grid"]?.let(::boolean) ?: false,
            grain = values["grain"]?.let(::boolean) ?: false,
            parallax = values["parallax"]?.toDoubleOrNull()
                ?.takeIf { it in BackgroundImage.MIN_PARALLAX..BackgroundImage.MAX_PARALLAX }
                ?: BackgroundImage.DEFAULT_PARALLAX,
        )
    }

    private fun boolean(value: String): Boolean? = when (value.trim().lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> null
    }
}
