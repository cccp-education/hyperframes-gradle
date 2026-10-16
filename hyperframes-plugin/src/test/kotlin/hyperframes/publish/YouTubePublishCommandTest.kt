package hyperframes.publish

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoVisibility

/**
 * HF-YOUTUBE-PUBLISH US-2 — pure request builders / response parsers.
 */
class YouTubePublishCommandTest {

    @TempDir
    lateinit var tempDir: File

    private fun publication(
        title: String = "Title",
        description: String = "",
        tags: List<String> = emptyList(),
        visibility: VideoVisibility = VideoVisibility.PRIVATE,
    ) = VideoPublication(
        videoFile = File(tempDir, "video.mp4").apply { writeText("x") },
        title = title,
        description = description,
        tags = tags,
        visibility = visibility,
        categoryId = "27",
        languageCode = "fr",
    )

    @Test
    fun `token request body is a refresh-token form`() {
        val body = YouTubePublishCommand.tokenRequestBody(
            YouTubeCredentials("cid", "secret", "refresh"),
        )

        assertThat(body).contains("grant_type=refresh_token")
        assertThat(body).contains("client_id=cid")
        assertThat(body).contains("refresh_token=refresh")
    }

    @Test
    fun `metadata json carries snippet and status`() {
        val json = YouTubePublishCommand.metadataJson(
            publication(title = "Hello", description = "Body", tags = listOf("a", "b")),
        )

        assertThat(json).contains("\"title\":\"Hello\"")
        assertThat(json).contains("\"description\":\"Body\"")
        assertThat(json).contains("\"tags\":[\"a\",\"b\"]")
        assertThat(json).contains("\"categoryId\":\"27\"")
        assertThat(json).contains("\"privacyStatus\":\"private\"")
        assertThat(json).contains("\"selfDeclaredMadeForKids\":false")
    }

    @Test
    fun `metadata json omits blank optional fields`() {
        val json = YouTubePublishCommand.metadataJson(publication())

        assertThat(json).doesNotContain("\"description\"")
        assertThat(json).doesNotContain("\"tags\"")
    }

    @Test
    fun `visibility maps to the youtube privacy status`() {
        assertThat(YouTubePublishCommand.privacyStatus(VideoVisibility.PRIVATE)).isEqualTo("private")
        assertThat(YouTubePublishCommand.privacyStatus(VideoVisibility.UNLISTED)).isEqualTo("unlisted")
        assertThat(YouTubePublishCommand.privacyStatus(VideoVisibility.PUBLIC)).isEqualTo("public")
    }

    @Test
    fun `watch url wraps the video id`() {
        assertThat(YouTubePublishCommand.watchUrl("abc")).isEqualTo("https://youtu.be/abc")
    }

    @Test
    fun `init request carries bearer auth and upload length`() {
        val request = YouTubePublishCommand.initRequest("tok", publication())

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url).isEqualTo(YouTubePublishCommand.UPLOAD_INIT_URL)
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer tok")
        assertThat(request.headers["X-Upload-Content-Length"]).isEqualTo(File(tempDir, "video.mp4").length().toString())
    }

    @Test
    fun `content request puts the file bytes`() {
        val file = File(tempDir, "v.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val request = YouTubePublishCommand.contentRequest("https://session", file)

        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url).isEqualTo("https://session")
        assertThat(request.body).containsExactly(1, 2, 3)
    }

    @Test
    fun `thumbnail request targets the video id`() {
        val image = File(tempDir, "t.png").apply { writeBytes(byteArrayOf(9)) }

        val request = YouTubePublishCommand.thumbnailRequest("tok", "vid123", image)

        assertThat(request.url).contains("videoId=vid123")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer tok")
    }

    @Test
    fun `parses the access token`() {
        assertThat(YouTubePublishCommand.parseAccessToken("""{"access_token":"AT","expires_in":3600}"""))
            .isEqualTo("AT")
    }

    @Test
    fun `parses the video id`() {
        assertThat(YouTubePublishCommand.parseVideoId("""{"kind":"youtube#video","id":"XYZ"}"""))
            .isEqualTo("XYZ")
    }

    @Test
    fun `parsing malformed json is null`() {
        assertThat(YouTubePublishCommand.parseAccessToken("not json")).isNull()
        assertThat(YouTubePublishCommand.parseVideoId("")).isNull()
    }
}
