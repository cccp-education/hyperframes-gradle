package hyperframes.publish

import java.io.File

/**
 * Loads [YouTubeCredentials] from a gitignored `youtube.yaml`
 * (HF-YOUTUBE-PUBLISH US-2).
 *
 * Format (see `youtube_example.yaml`):
 *
 * [source,yaml]
 * ----
 * youtube:
 *   clientId: "…apps.googleusercontent.com"
 *   clientSecret: "…"
 *   refreshToken: "…"
 *   channelId: "UC…"
 *   apiKey: "AIza…"    # optional
 * ----
 *
 * The parser is deliberately minimal (top-level `youtube:` map of `key: value`
 * lines) — no YAML library dependency, mirroring capsule's `fish.yaml` loader.
 * It **never logs the values** (Rule 6).
 */
object YouTubeCredentialsLoader {

    /** Loads credentials from [file], or `null` when the file is absent/empty. */
    fun load(file: File): YouTubeCredentials? {
        if (!file.exists() || !file.isFile) return null
        val values = parse(file.readText())
        if (values.isEmpty()) return null
        return YouTubeCredentials(
            clientId = values["clientid"].orEmpty(),
            clientSecret = values["clientsecret"].orEmpty(),
            refreshToken = values["refreshtoken"].orEmpty(),
            channelId = values["channelid"].orEmpty(),
            apiKey = values["apikey"].orEmpty(),
        )
    }

    /**
     * Parses the simple `youtube:` map. Keys are matched case-insensitively and
     * stripped of quotes; comments (`#`) and blank lines are ignored. The
     * `youtube:` section header is optional (keys may appear at top level).
     */
    fun parse(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line == "youtube:") continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim().trim('"', '\'')
            if (key.isEmpty() || value.isEmpty()) continue
            values[key] = value
        }
        return values
    }
}
