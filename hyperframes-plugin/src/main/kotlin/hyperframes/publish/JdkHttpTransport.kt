package hyperframes.publish

import video.kernel.publish.HttpTransport
import video.kernel.publish.PublishHttpRequest
import video.kernel.publish.PublishHttpResponse
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.net.http.HttpRequest as JdkHttpRequest
import java.net.http.HttpResponse as JdkHttpResponse

/**
 * Default N2 [HttpTransport] built on [`java.net.http.HttpClient`] (JDK 11+), so
 * the plugin needs **no Google SDK and no extra dependency** (decision D3).
 *
 * The seam, the request and the response are the shared N1 types
 * (`video.kernel.publish`, PUBLISH-ADAPTER-N1); only the network implementation
 * stays in this engine.
 *
 * Never throws: any I/O failure yields a synthetic `-1` status so the caller
 * degrades instead of crashing the build.
 */
class JdkHttpTransport(
    private val timeout: Duration = Duration.ofMinutes(5),
) : HttpTransport {
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    override fun send(request: PublishHttpRequest): PublishHttpResponse =
        try {
            val builder =
                JdkHttpRequest
                    .newBuilder()
                    .uri(URI.create(request.url))
                    .timeout(timeout)
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            val publisher =
                if (request.body.isEmpty()) {
                    JdkHttpRequest.BodyPublishers.noBody()
                } else {
                    JdkHttpRequest.BodyPublishers.ofByteArray(request.body)
                }
            builder.method(request.method, publisher)
            val response: JdkHttpResponse<String> = client.send(builder.build(), JdkHttpResponse.BodyHandlers.ofString())
            val headers =
                response.headers().map().entries.associate { (k, v) ->
                    k.lowercase() to (v.firstOrNull() ?: "")
                }
            PublishHttpResponse(response.statusCode(), response.body(), headers)
        } catch (e: Exception) {
            PublishHttpResponse(statusCode = -1, body = e.message ?: "")
        }
}
