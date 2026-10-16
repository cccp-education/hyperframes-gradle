package hyperframes.publish

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoVisibility

/**
 * Pure builders and parsers for the YouTube Data API v3 publication flow
 * (HF-YOUTUBE-PUBLISH US-2).
 *
 * No I/O, no Gradle: every request shape and every response parse is a pure
 * function, so the exact wire contract is unit-tested without a network. The
 * adapter [YouTubeDataApiPublisher] only sequences these functions through the
 * [HttpTransport] seam.
 *
 * The flow has four steps:
 * 1. exchange the refresh token for a short-lived access token (`oauth2/token`);
 * 2. initiate a resumable upload (`upload/youtube/v3/videos?uploadType=resumable`)
 *    → the session URL is returned in the `Location` header;
 * 3. `PUT` the video bytes to the session URL → the video resource (with `id`);
 * 4. set the thumbnail (`upload/youtube/v3/thumbnails/set?videoId=…`).
 */
object YouTubePublishCommand {

    /** OAuth 2.0 token endpoint (refresh-token grant). */
    const val TOKEN_URL: String = "https://oauth2.googleapis.com/token"

    /** Resumable upload initiation endpoint. */
    const val UPLOAD_INIT_URL: String =
        "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"

    /** Thumbnail-set endpoint (the video id is appended). */
    const val THUMBNAIL_SET_URL: String =
        "https://www.googleapis.com/upload/youtube/v3/thumbnails/set"

    /** Public watch URL for a video id. */
    fun watchUrl(videoId: String): String = "https://youtu.be/$videoId"

    private val mapper: ObjectMapper = ObjectMapper()

    /** Maps a neutral visibility to the YouTube `privacyStatus` value. */
    fun privacyStatus(visibility: VideoVisibility): String = when (visibility) {
        VideoVisibility.PRIVATE -> "private"
        VideoVisibility.UNLISTED -> "unlisted"
        VideoVisibility.PUBLIC -> "public"
    }

    /** `application/x-www-form-urlencoded` body for the refresh-token grant. */
    fun tokenRequestBody(credentials: YouTubeCredentials): String =
        listOf(
            "client_id" to credentials.clientId,
            "client_secret" to credentials.clientSecret,
            "refresh_token" to credentials.refreshToken,
            "grant_type" to "refresh_token",
        ).joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    /** The `snippet` + `status` JSON body sent in the resumable upload init. */
    fun metadataJson(publication: VideoPublication): String {
        val root: ObjectNode = mapper.createObjectNode()

        val snippet = root.putObject("snippet")
        snippet.put("title", publication.title)
        if (publication.description.isNotBlank()) snippet.put("description", publication.description)
        if (publication.tags.isNotEmpty()) {
            val tagsArray = snippet.putArray("tags")
            publication.tags.forEach { tagsArray.add(it) }
        }
        if (publication.categoryId.isNotBlank()) snippet.put("categoryId", publication.categoryId)
        if (publication.languageCode.isNotBlank()) snippet.put("defaultLanguage", publication.languageCode)

        val status = root.putObject("status")
        status.put("privacyStatus", privacyStatus(publication.visibility))
        status.put("selfDeclaredMadeForKids", publication.madeForKids)
        publication.publishAt?.takeIf { it.isNotBlank() }?.let { status.put("publishAt", it) }

        return mapper.writeValueAsString(root)
    }

    /** Resumable-upload init request (JSON metadata, no bytes yet). */
    fun initRequest(accessToken: String, publication: VideoPublication): PublishHttpRequest =
        PublishHttpRequest(
            method = "POST",
            url = UPLOAD_INIT_URL,
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Content-Type" to "application/json; charset=UTF-8",
                "X-Upload-Content-Type" to "video/*",
                "X-Upload-Content-Length" to publication.videoFile.length().toString(),
            ),
            body = metadataJson(publication).toByteArray(StandardCharsets.UTF_8),
        )

    /** Content-upload request: `PUT` the whole video to the session URL. */
    fun contentRequest(sessionUrl: String, videoFile: File): PublishHttpRequest =
        PublishHttpRequest(
            method = "PUT",
            url = sessionUrl,
            headers = mapOf("Content-Type" to "video/*"),
            body = videoFile.readBytes(),
        )

    /** Thumbnail-set request (`POST` the image bytes with an auth header). */
    fun thumbnailRequest(accessToken: String, videoId: String, thumbnail: File): PublishHttpRequest =
        PublishHttpRequest(
            method = "POST",
            url = "$THUMBNAIL_SET_URL?videoId=${encode(videoId)}",
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Content-Type" to "image/png",
            ),
            body = thumbnail.readBytes(),
        )

    /** Extracts `access_token` from a token response body, or `null`. */
    fun parseAccessToken(body: String): String? = readField(body, "access_token")

    /** Extracts the created video `id` from an upload response body, or `null`. */
    fun parseVideoId(body: String): String? = readField(body, "id")

    private fun readField(body: String, field: String): String? = try {
        mapper.readTree(body).path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
