package hyperframes.pronunciation

/**
 * HF-7b — Aggregate root collecting TTS pronunciation hints.
 *
 * Collects [PronunciationHint] instances, deduplicates them on (word, language),
 * and renders a JSON island injected into the HyperFrames HTML so the CLI
 * can correct TTS phonetics at render time.
 *
 * A bad pronunciation ("dos" spoken "dosse" instead of "do") destroys the
 * credibility of training content — this dictionary gives the AsciiDoc author
 * the power to fix it without touching the engine.
 */
class PronunciationDictionary {

    private val hints: MutableMap<String, PronunciationHint> = LinkedHashMap()

    /**
     * Adds a hint. Deduplicates on (normalized word + language):
     * an existing hint for the same key is replaced by the new one.
     */
    fun add(hint: PronunciationHint) {
        hints[hint.deduplicationKey()] = hint
    }

    /** Number of hints currently collected. */
    fun size(): Int = hints.size

    /**
     * Looks up a hint by word (case-insensitive).
     *
     * @param language when provided, prefers the hint matching this language;
     *                 otherwise falls back to the language-agnostic hint,
     *                 then to any hint for that word.
     */
    fun find(word: String, language: String? = null): PronunciationHint? {
        val normalized = word.trim().lowercase()
        if (language != null) {
            val keyed = hints["$normalized|$language"]
            if (keyed != null) return keyed
        }
        hints["$normalized|_"]?.let { return it }
        return hints.values.firstOrNull { it.word == normalized }
    }

    /**
     * Merges this dictionary with [other] and returns a new dictionary.
     *
     * Hints from this receiver override [other] on key conflict (word + language).
     * Neither the receiver nor the argument is mutated.
     */
    fun merge(other: PronunciationDictionary): PronunciationDictionary {
        val merged = PronunciationDictionary()
        other.hints.values.forEach { merged.add(it) }
        this.hints.values.forEach { merged.add(it) }
        return merged
    }

    /**
     * Renders the JSON island to inject into the HyperFrames HTML.
     *
     * Empty: `<script type="application/json" id="hf-pronunciation">[]</script>`
     * Non-empty: the array holds the JSON object of each hint.
     */
    fun render(): String = buildString {
        append("""<script type="application/json" id="hf-pronunciation">""")
        append("[")
        hints.values.joinTo(this, separator = ",") { it.render() }
        append("]")
        append("</script>")
    }

    /** Immutable snapshot of the hints (for N3 traceability / metadata.json). */
    fun snapshot(): List<PronunciationHint> = hints.values.toList()
}