package hyperframes.sound

/**
 * Outcome of parsing one sound listing block
 * (HF-MUSIC-LIBRARY US-3).
 *
 * Parsing is **defensive**: an unusable declaration never breaks the build, it
 * yields an actionable [Invalid] the caller drops (the same posture as the
 * historical `[.hyperframes-audio]` block).
 */
sealed interface SoundBlockParseResult<out T> {
    /** The block declared a usable cue. */
    data class Parsed<T>(
        val declaration: T,
    ) : SoundBlockParseResult<T>

    /** The block declared nothing usable; [reasons] are secret-free and actionable. */
    data class Invalid(
        val reasons: List<String>,
    ) : SoundBlockParseResult<Nothing>
}

/**
 * Parser of the pilot-document sound blocks (HF-MUSIC-LIBRARY US-3 / D12).
 *
 * Grammar — one `key: value` per line (`#` comments and blank lines ignored):
 *
 * ```
 * [.hyperframes-music]
 * ----
 * mood: documentary
 * track: calm-piano-01
 * volume: 0.25
 * fade-in: 1.5
 * fade-out: 2.0
 * loop: true
 * ----
 *
 * [.hyperframes-sfx]
 * ----
 * file: whoosh-impact
 * offset: 1.2
 * volume: 0.4
 * ----
 * ```
 *
 * Asciidoctor may join the lines of an **open block** (`--`) into a single
 * paragraph, so the parser first splits on the known keys: the DSL works the
 * same whether the author used `----` (listing) or `--` (open block).
 */
object SoundBlockParser {
    private const val DEFAULT_MUSIC_VOLUME = MusicDeclaration.DEFAULT_VOLUME
    private const val DEFAULT_SFX_VOLUME = SfxDeclaration.DEFAULT_VOLUME

    private val KEYS =
        listOf(
            "mood",
            "track",
            "file",
            "volume",
            "fade-in",
            "fade-out",
            "loop",
            "stop",
            "music",
            "offset",
            "at",
        )

    /**
     * A key boundary inside a single line: Asciidoctor may collapse the lines of
     * an open block, so `mood: calm track: x` must still produce two entries.
     */
    private val KEY_BOUNDARY =
        Regex("""\s+(?=(?:${KEYS.joinToString("|")})\s*:)""", RegexOption.IGNORE_CASE)

    /** Splits a block text into significant `key: value` lines. */
    fun lines(text: String): List<String> =
        text
            .lineSequence()
            .flatMap { line -> line.split(KEY_BOUNDARY).asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    fun parseMusic(text: String): SoundBlockParseResult<MusicDeclaration> {
        var trackId = ""
        var mood = ""
        var file = ""
        var volume = DEFAULT_MUSIC_VOLUME
        var fadeIn = 0.0
        var fadeOut = 0.0
        var loop = true
        var stop = false
        val reasons = mutableListOf<String>()

        lines(text).forEach { line ->
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (value.isEmpty()) {
                reasons += "key '$key' has no value"
                return@forEach
            }
            when (key) {
                "track" ->
                    when {
                        value.equals("none", ignoreCase = true) -> stop = true
                        else -> trackId = value
                    }
                "mood" -> mood = value
                "file" -> file = value
                "volume" ->
                    volume = value.toDoubleOrNull() ?: run {
                        reasons += "volume '$value' is not a number"
                        volume
                    }
                "fade-in" ->
                    fadeIn = value.toDoubleOrNull() ?: run {
                        reasons += "fade-in '$value' is not a number"
                        fadeIn
                    }
                "fade-out" ->
                    fadeOut = value.toDoubleOrNull() ?: run {
                        reasons += "fade-out '$value' is not a number"
                        fadeOut
                    }
                "loop" ->
                    loop = value.toBooleanFlag() ?: run {
                        reasons += "loop '$value' is not a boolean"
                        loop
                    }
                "stop" ->
                    stop = value.toBooleanFlag() ?: run {
                        reasons += "stop '$value' is not a boolean"
                        stop
                    }
                "music" ->
                    when {
                        value.equals("off", ignoreCase = true) || value.equals("none", ignoreCase = true) -> stop = true
                        else -> reasons += "music '$value' is not supported (use 'off')"
                    }
                else -> reasons += "key '$key' is not supported by a music block"
            }
        }

        if (reasons.isEmpty()) {
            runCatching { MusicDeclaration(trackId, mood, file, volume, fadeIn, fadeOut, loop, stop) }
                .onSuccess { return SoundBlockParseResult.Parsed(it) }
                .onFailure { reasons += it.message ?: "invalid music declaration" }
        }
        return SoundBlockParseResult.Invalid(reasons)
    }

    fun parseSfx(text: String): SoundBlockParseResult<SfxDeclaration> {
        var file = ""
        var offset = 0.0
        var volume = DEFAULT_SFX_VOLUME
        val reasons = mutableListOf<String>()

        lines(text).forEach { line ->
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (value.isEmpty()) {
                reasons += "key '$key' has no value"
                return@forEach
            }
            when (key) {
                "file", "track" -> file = value
                "offset", "at" ->
                    offset = value.toDoubleOrNull() ?: run {
                        reasons += "offset '$value' is not a number"
                        offset
                    }
                "volume" ->
                    volume = value.toDoubleOrNull() ?: run {
                        reasons += "volume '$value' is not a number"
                        volume
                    }
                else -> reasons += "key '$key' is not supported by an sfx block"
            }
        }
        if (file.isBlank()) reasons += "an sfx block must declare 'file'"

        if (reasons.isEmpty()) {
            runCatching { SfxDeclaration(file, offset, volume) }
                .onSuccess { return SoundBlockParseResult.Parsed(it) }
                .onFailure { reasons += it.message ?: "invalid sfx declaration" }
        }
        return SoundBlockParseResult.Invalid(reasons)
    }

    private fun String.toBooleanFlag(): Boolean? =
        when (trim().lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
}
