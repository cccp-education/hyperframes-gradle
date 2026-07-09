package hyperframes.pronunciation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * HF-7d — Loads pre-built pronunciation domain dictionaries from classpath
 * resources (`domain/<name>.json`).
 *
 * Pre-built domains cover generic video-content vocabulary:
 * - `video-fr` — French video production vocabulary (narration, transition, render)
 * - `video-en` — English video production vocabulary (render, codec, keyframe)
 *
 * The plugin is a generic AsciiDoc-to-video transformer (public OSS): domain
 * dictionaries cover video production vocabulary only. Consumer boroughs with
 * a private business vocabulary (training, medical, legal...) inject their own
 * domain dictionary via configuration rather than embedding it in the plugin.
 *
 * A domain dictionary can be merged with an author dictionary so that
 * author hints override domain hints on conflict (word + language).
 */
object PronunciationDomainLoader {

    private val mapper = jacksonObjectMapper()

    /**
     * Loads a domain dictionary from the classpath resource `domain/$name.json`.
     *
     * @param name the domain identifier (e.g. "video-fr", "video-en")
     * @return a non-empty [PronunciationDictionary]
     * @throws IllegalArgumentException when the domain is unknown or the resource
     *         is missing or unreadable
     */
    fun load(name: String): PronunciationDictionary {
        val resourcePath = "domain/$name.json"
        val resource = PronunciationDomainLoader::class.java
            .classLoader
            .getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Unknown pronunciation domain: '$name' (resource '$resourcePath' not found on classpath)")

        val json = resource.bufferedReader().use { it.readText() }
        val entries: List<HintEntry> = mapper.readValue(
            json,
            mapper.typeFactory.constructCollectionType(List::class.java, HintEntry::class.java)
        )

        val dictionary = PronunciationDictionary()
        entries.forEach { entry ->
            dictionary.add(
                PronunciationHint.of(
                    word = entry.word,
                    phonetic = entry.phonetic,
                    language = entry.language
                )
            )
        }
        require(dictionary.size() > 0) { "Domain '$name' is empty — resource '$resourcePath' contains no valid hints" }
        return dictionary
    }

    /** Internal DTO for JSON deserialization of domain files. */
    private data class HintEntry(
        val word: String,
        val phonetic: String,
        val language: String? = null
    )
}