package hyperframes.pronunciation

/**
 * HF-7a — One entry of the TTS pronunciation dictionary.
 *
 * Pure DDD value object: represents the phonetic correction of a word
 * misread by the TTS engine (e.g. "dos" spoken "dosse" instead of "do").
 *
 * The hint renders as a JSON object consumed by the HyperFrames CLI from
 * the `<script type="application/json" id="hf-pronunciation">` island
 * injected by [PronunciationDictionary] (HF-7b).
 *
 * @property word the word to correct, normalized to lowercase (case-insensitive lookups)
 * @property phonetic the phonetic approximation the TTS should speak
 * @property language optional language tag to disambiguate the same word
 *           across languages (e.g. "dos" = "do" in French, "doss" in Spanish)
 */
data class PronunciationHint private constructor(
    val word: String,
    val phonetic: String,
    val language: String?
) {
    /**
     * Renders the hint as a compact JSON object.
     *
     * Without language: `{"word":"dos","phonetic":"do"}`
     * With language:    `{"word":"dos","phonetic":"do","language":"fr"}`
     */
    fun render(): String = buildString {
        append("{")
        append(""""word":"$word"""")
        append(",")
        append(""""phonetic":"$phonetic"""")
        if (language != null) {
            append(",")
            append(""""language":"$language"""")
        }
        append("}")
    }

    /**
     * Deduplication key (word + language) used by [PronunciationDictionary].
     */
    fun deduplicationKey(): String = "$word|${language ?: "_"}"

    companion object {
        /**
         * Creates a hint, normalizing the word (trim + lowercase) and the phonetic (trim).
         *
         * @throws IllegalArgumentException if the word or phonetic is empty/blank
         */
        fun of(word: String, phonetic: String, language: String? = null): PronunciationHint {
            require(word.trim().isNotEmpty()) { "word must not be empty or blank" }
            require(phonetic.trim().isNotEmpty()) { "phonetic must not be empty or blank" }
            return PronunciationHint(
                word = word.trim().lowercase(),
                phonetic = phonetic.trim(),
                language = language
            )
        }
    }
}