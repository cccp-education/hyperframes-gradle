package hyperframes.publish

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JdkHttpRequest
import java.net.http.HttpResponse as JdkHttpResponse
import java.time.Duration

/**
 * A minimal, engine-neutral HTTP request the publisher sends to the YouTube Data
 * API (HF-YOUTUBE-PUBLISH US-2).
 *
 * @property method  HTTP verb (`GET`, `POST`, `PUT`).
 * @property url     absolute request URL.
 * @property headers request headers.
 * @property body    raw request body bytes (empty for a body-less request).
 */
data class PublishHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublishHttpRequest) return false
        return method == other.method && url == other.url &&
            headers == other.headers && body.contentEquals(other.body)
    }

    override fun hashCode(): Int =
        ((method.hashCode() * 31 + url.hashCode()) * 31 + headers.hashCode()) * 31 + body.contentHashCode()
}

/**
 * Outcome of a [PublishHttpRequest]: status code and response body (text).
 * Response headers are exposed because the resumable upload session URL is
 * returned in the `Location` header of the init response.
 */
data class PublishHttpResponse(
    val statusCode: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Seam over HTTP — test-injectable (pattern [hyperframes.post.CommandRunner]).
 *
 * The concrete [JdkHttpTransport] spawns a real request through the JDK client;
 * unit tests inject a fake that returns canned responses, so **no test ever
 * touches the network**.
 */
fun interface HttpTransport {
    fun send(request: PublishHttpRequest): PublishHttpResponse
}

/**
 * Default [HttpTransport] built on [`java.net.http.HttpClient`] (JDK 11+), so the
 * plugin needs **no Google SDK and no extra dependency** (decision D3).
 *
 * Never throws: any I/O failure yields a synthetic `-1` status so the caller
 * degrades instead of crashing the build.
 */
class JdkHttpTransport(
    private val timeout: Duration = Duration.ofMinutes(5),
) : HttpTransport {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun send(request: PublishHttpRequest): PublishHttpResponse {
        return try {
            val builder = JdkHttpRequest.newBuilder()
                .uri(URI.create(request.url))
                .timeout(timeout)
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            val publisher = if (request.body.isEmpty()) {
                JdkHttpRequest.BodyPublishers.noBody()
            } else {
                JdkHttpRequest.BodyPublishers.ofByteArray(request.body)
            }
            builder.method(request.method, publisher)
            val response: JdkHttpResponse<String> = client.send(builder.build(), JdkHttpResponse.BodyHandlers.ofString())
            val headers = response.headers().map().entries.associate { (k, v) ->
                k.lowercase() to (v.firstOrNull() ?: "")
            }
            PublishHttpResponse(response.statusCode(), response.body(), headers)
        } catch (e: Exception) {
            PublishHttpResponse(statusCode = -1, body = e.message ?: "")
        }
    }
}
