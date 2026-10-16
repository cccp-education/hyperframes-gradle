package hyperframes.publish

/**
 * YouTube OAuth 2.0 credentials loaded from a gitignored `youtube.yaml`
 * (HF-YOUTUBE-PUBLISH US-2).
 *
 * The YouTube Data API requires **OAuth 2.0** (not a plain API key) to upload a
 * video (`videos.insert`) or set its thumbnail. The [refreshToken] is obtained
 * once by the channel owner (see the cadrage §2.2) and exchanged for a
 * short-lived access token on every publication.
 *
 * **Rule 6 — the secret never leaks**: [toString] is overridden to redact every
 * credential so a careless log line cannot print the refresh token.
 *
 * @property clientId     OAuth client identifier (`…apps.googleusercontent.com`).
 * @property clientSecret OAuth client secret.
 * @property refreshToken long-lived refresh token bound to the channel owner.
 * @property channelId    target channel id (`UC…`); optional (diagnostics only).
 * @property apiKey       optional plain API key (read-only queries only).
 */
data class YouTubeCredentials(
    val clientId: String,
    val clientSecret: String,
    val refreshToken: String,
    val channelId: String = "",
    val apiKey: String = "",
) {
    /** `true` when enough is present to perform an authenticated upload. */
    fun canUpload(): Boolean =
        clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()

    /** Redacted representation — never prints a secret. */
    override fun toString(): String =
        "YouTubeCredentials(clientId=${redact(clientId)}, clientSecret=***, " +
            "refreshToken=***, channelId=$channelId, apiKey=${if (apiKey.isBlank()) "" else "***"})"

    private fun redact(value: String): String =
        if (value.isBlank()) "" else "${value.take(6)}…"
}
