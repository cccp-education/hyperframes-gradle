package hyperframes.publish

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoPublicationResult
import video.kernel.publish.VideoVisibility

/**
 * HF-YOUTUBE-PUBLISH US-2 — [YouTubeDataApiPublisher] driven through a **fake
 * transport**: the four-step OAuth/resumable flow is exercised end-to-end
 * without touching the network.
 */
class YouTubeDataApiPublisherTest {

    @TempDir
    lateinit var tempDir: File

    /** Queued fake transport: returns [responses] in order and records [requests]. */
    private class FakeTransport(private val responses: MutableList<PublishHttpResponse>) : HttpTransport {
        val requests = mutableListOf<PublishHttpRequest>()
        override fun send(request: PublishHttpRequest): PublishHttpResponse {
            requests += request
            return if (responses.isEmpty()) PublishHttpResponse(500, "no canned response")
            else responses.removeAt(0)
        }
    }

    private fun publication(thumbnail: File? = null) = VideoPublication(
        videoFile = File(tempDir, "video.mp4").apply { writeText("video-bytes") },
        title = "Title",
        description = "Body",
        tags = listOf("a"),
        visibility = VideoVisibility.PRIVATE,
        categoryId = "27",
        thumbnailFile = thumbnail,
    )

    private fun credentials() = YouTubeCredentials("cid", "secret", "refresh", channelId = "UC1")

    @Test
    fun `happy path publishes and returns the video url`() {
        val transport = FakeTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", mapOf("location" to "https://session")),
                PublishHttpResponse(200, """{"id":"VID"}"""),
            ),
        )

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication())

        assertThat(result).isEqualTo(VideoPublicationResult.Published("VID", "https://youtu.be/VID"))
        assertThat(transport.requests.map { it.method }).containsExactly("POST", "POST", "PUT")
    }

    @Test
    fun `thumbnail is set best-effort when provided`() {
        val thumbnail = File(tempDir, "thumb.png").apply { writeBytes(byteArrayOf(1)) }
        val transport = FakeTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", mapOf("location" to "https://session")),
                PublishHttpResponse(200, """{"id":"VID"}"""),
                PublishHttpResponse(200, "{}"),
            ),
        )

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication(thumbnail))

        assertThat(result).isInstanceOf(VideoPublicationResult.Published::class.java)
        assertThat(transport.requests).hasSize(4)
        assertThat(transport.requests.last().url).contains("thumbnails/set")
    }

    @Test
    fun `a thumbnail refusal still keeps the published video`() {
        val thumbnail = File(tempDir, "thumb.png").apply { writeBytes(byteArrayOf(1)) }
        val transport = FakeTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", mapOf("location" to "https://session")),
                PublishHttpResponse(200, """{"id":"VID"}"""),
                PublishHttpResponse(403, "forbidden"),
            ),
        )

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication(thumbnail))

        assertThat(result).isEqualTo(VideoPublicationResult.Published("VID", "https://youtu.be/VID"))
    }

    @Test
    fun `a token refusal fails`() {
        val transport = FakeTransport(mutableListOf(PublishHttpResponse(401, "bad")))

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication())

        assertThat(result).isInstanceOf(VideoPublicationResult.Failed::class.java)
        assertThat((result as VideoPublicationResult.Failed).reason).contains("token")
    }

    @Test
    fun `a missing session url fails`() {
        val transport = FakeTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", emptyMap()),
            ),
        )

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication())

        assertThat(result).isInstanceOf(VideoPublicationResult.Failed::class.java)
    }

    @Test
    fun `a missing video id fails`() {
        val transport = FakeTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", mapOf("location" to "https://session")),
                PublishHttpResponse(200, "{}"),
            ),
        )

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(publication())

        assertThat(result).isInstanceOf(VideoPublicationResult.Failed::class.java)
    }

    @Test
    fun `a missing video file degrades without any request`() {
        val transport = FakeTransport(mutableListOf())
        val missing = VideoPublication(videoFile = File(tempDir, "nope.mp4"), title = "T")

        val result = YouTubeDataApiPublisher(credentials(), transport).publish(missing)

        assertThat(result).isInstanceOf(VideoPublicationResult.Degraded::class.java)
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `incomplete credentials degrade without any request`() {
        val transport = FakeTransport(mutableListOf())
        val incomplete = YouTubeCredentials("cid", "secret", refreshToken = "")

        val result = YouTubeDataApiPublisher(incomplete, transport).publish(publication())

        assertThat(result).isInstanceOf(VideoPublicationResult.Degraded::class.java)
        assertThat(transport.requests).isEmpty()
    }
}
