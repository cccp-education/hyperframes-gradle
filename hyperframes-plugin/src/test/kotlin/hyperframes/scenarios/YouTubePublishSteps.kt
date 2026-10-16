package hyperframes.scenarios

import hyperframes.publish.ChapterLineParser
import hyperframes.publish.DisabledVideoPublisher
import hyperframes.publish.HttpTransport
import hyperframes.publish.PublishHttpRequest
import hyperframes.publish.PublishHttpResponse
import hyperframes.publish.YouTubeCredentials
import hyperframes.publish.YouTubeDataApiPublisher
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoPublicationResult

/**
 * HF-YOUTUBE-PUBLISH US-2 — steps for the YouTube publication flow.
 *
 * Prefixed (`youtube publish ...`) to avoid glue collisions (pattern S-088).
 * The fake transport never touches the network.
 */
class YouTubePublishSteps {

    private class RecordingTransport(private val responses: MutableList<PublishHttpResponse>) : HttpTransport {
        val requests = mutableListOf<PublishHttpRequest>()
        override fun send(request: PublishHttpRequest): PublishHttpResponse {
            requests += request
            return if (responses.isEmpty()) PublishHttpResponse(500, "") else responses.removeAt(0)
        }
    }

    private var transport: RecordingTransport = RecordingTransport(mutableListOf())
    private var credentials: YouTubeCredentials = YouTubeCredentials("cid", "secret", "refresh")
    private lateinit var publisher: video.kernel.publish.VideoPublisher
    private var result: VideoPublicationResult? = null

    @Given("a youtube publish fake transport returning a token and a session and the video id {string}")
    fun transportHappyPath(videoId: String) {
        transport = RecordingTransport(
            mutableListOf(
                PublishHttpResponse(200, """{"access_token":"AT"}"""),
                PublishHttpResponse(200, "", mapOf("location" to "https://session")),
                PublishHttpResponse(200, """{"id":"$videoId"}"""),
            ),
        )
    }

    @Given("a youtube publish fake transport refusing the token")
    fun transportRefusingToken() {
        transport = RecordingTransport(mutableListOf(PublishHttpResponse(401, "bad")))
    }

    @Given("a youtube publish credentials with a refresh token")
    fun credentialsWithRefresh() {
        credentials = YouTubeCredentials("cid", "secret", "refresh")
        publisher = YouTubeDataApiPublisher(credentials, transport)
    }

    @Given("a youtube publish credentials without a refresh token")
    fun credentialsWithoutRefresh() {
        credentials = YouTubeCredentials("cid", "secret", refreshToken = "")
        publisher = YouTubeDataApiPublisher(credentials, transport)
    }

    @Given("a youtube publish publisher disabled")
    fun disabledPublisher() {
        publisher = DisabledVideoPublisher()
    }

    @When("I publish a youtube video from a temp file")
    fun publishVideo() {
        val video = File.createTempFile("hf-publish-", ".mp4").apply { writeText("video-bytes") }
        val publication = VideoPublication(
            videoFile = video,
            title = "A title",
            description = "Body",
            chapters = ChapterLineParser.parse(listOf("0:00 Intro", "0:10 Setup", "0:20 Outro")),
        )
        result = publisher.publish(publication)
        video.delete()
    }

    @Then("the youtube publish result is published")
    fun resultPublished() {
        check(result is VideoPublicationResult.Published) { "expected published, was $result" }
    }

    @Then("the youtube publish result is degraded")
    fun resultDegraded() {
        check(result is VideoPublicationResult.Degraded) { "expected degraded, was $result" }
    }

    @Then("the youtube publish result is failed")
    fun resultFailed() {
        check(result is VideoPublicationResult.Failed) { "expected failed, was $result" }
    }

    @Then("the youtube publish url is {string}")
    fun urlIs(expected: String) {
        val published = result as VideoPublicationResult.Published
        check(published.url == expected) { "expected '$expected', was '${published.url}'" }
    }

    @Then("the youtube publish transport received no request")
    fun noRequest() {
        check(transport.requests.isEmpty()) { "expected no request, was ${transport.requests.size}" }
    }
}
