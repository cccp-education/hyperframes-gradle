package hyperframes.publish

import java.nio.charset.StandardCharsets
import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoPublicationResult
import video.kernel.publish.VideoPublisher

/**
 * N2 implementation of the engine-neutral [VideoPublisher] port backed by the
 * YouTube Data API v3 (HF-YOUTUBE-PUBLISH US-2).
 *
 * Sequenced through the [HttpTransport] seam, it performs the four-step
 * resumable-upload flow built by [YouTubePublishCommand]:
 * refresh-token → upload init → content `PUT` → thumbnail set.
 *
 * **Never throws** (port contract): each step maps to
 * [VideoPublicationResult.Degraded] (nothing to publish, safe skip) or
 * [VideoPublicationResult.Failed] (the API refused). The thumbnail step is
 * best-effort — a thumbnail failure never loses an uploaded video.
 *
 * No Google SDK, no dependency beyond the JDK HTTP client and Jackson (D3).
 *
 * @property credentials the OAuth 2.0 credentials (never logged).
 * @property transport   the HTTP seam (a fake in tests — never the network).
 * @property onLog       diagnostic sink (wired to the Gradle logger by the task).
 */
class YouTubeDataApiPublisher(
    private val credentials: YouTubeCredentials,
    private val transport: HttpTransport = JdkHttpTransport(),
    private val onLog: (String) -> Unit = {},
) : VideoPublisher {

    override fun publish(publication: VideoPublication): VideoPublicationResult {
        if (!publication.videoFile.exists() || publication.videoFile.length() == 0L) {
            return VideoPublicationResult.Degraded("no rendered video to publish: ${publication.videoFile.name}")
        }
        if (!credentials.canUpload()) {
            return VideoPublicationResult.Degraded("YouTube OAuth credentials incomplete (clientId/clientSecret/refreshToken)")
        }

        val accessToken = refreshAccessToken()
            ?: return VideoPublicationResult.Failed("YouTube OAuth token refresh failed")

        val sessionUrl = initiateUpload(accessToken, publication)
            ?: return VideoPublicationResult.Failed("YouTube resumable upload init failed (no session URL)")

        val videoId = uploadContent(sessionUrl, publication)
            ?: return VideoPublicationResult.Failed("YouTube upload failed (no video id returned)")

        setThumbnail(accessToken, videoId, publication)

        onLog("published to YouTube: ${YouTubePublishCommand.watchUrl(videoId)}")
        return VideoPublicationResult.Published(videoId, YouTubePublishCommand.watchUrl(videoId))
    }

    private fun refreshAccessToken(): String? {
        val request = PublishHttpRequest(
            method = "POST",
            url = YouTubePublishCommand.TOKEN_URL,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = YouTubePublishCommand.tokenRequestBody(credentials).toByteArray(StandardCharsets.UTF_8),
        )
        val response = transport.send(request)
        if (response.statusCode !in 200..299) {
            onLog("YouTube token refresh refused (status ${response.statusCode})")
            return null
        }
        return YouTubePublishCommand.parseAccessToken(response.body)
    }

    private fun initiateUpload(accessToken: String, publication: VideoPublication): String? {
        val response = transport.send(YouTubePublishCommand.initRequest(accessToken, publication))
        if (response.statusCode !in 200..299) {
            onLog("YouTube upload init refused (status ${response.statusCode})")
            return null
        }
        return response.headers["location"]?.takeIf { it.isNotBlank() }
    }

    private fun uploadContent(sessionUrl: String, publication: VideoPublication): String? {
        val response = transport.send(YouTubePublishCommand.contentRequest(sessionUrl, publication.videoFile))
        if (response.statusCode !in 200..299) {
            onLog("YouTube content upload refused (status ${response.statusCode})")
            return null
        }
        return YouTubePublishCommand.parseVideoId(response.body)
    }

    private fun setThumbnail(accessToken: String, videoId: String, publication: VideoPublication) {
        val thumbnail = publication.thumbnailFile ?: return
        if (!thumbnail.exists()) return
        val response = transport.send(YouTubePublishCommand.thumbnailRequest(accessToken, videoId, thumbnail))
        if (response.statusCode !in 200..299) {
            // Best-effort: never lose an uploaded video because of a thumbnail.
            onLog("YouTube thumbnail set refused (status ${response.statusCode}) — video kept")
        }
    }
}
