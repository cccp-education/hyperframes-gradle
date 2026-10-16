package hyperframes.publish

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * HF-YOUTUBE-PUBLISH US-2 — resolver factory (fail-soft).
 */
class HyperframesPublisherResolverTest {

    @TempDir
    lateinit var tempDir: File

    private fun credentialsFile(complete: Boolean = true): File {
        val file = File(tempDir, "youtube.yaml")
        file.writeText(
            if (complete) {
                "youtube:\n  clientId: a\n  clientSecret: b\n  refreshToken: c\n"
            } else {
                "youtube:\n  clientId: a\n  clientSecret: b\n"
            },
        )
        return file
    }

    @Test
    fun `disabled yields the disabled publisher`() {
        val publisher = HyperframesPublisherResolver.resolve(false, "youtube", credentialsFile())

        assertThat(publisher).isInstanceOf(DisabledVideoPublisher::class.java)
    }

    @Test
    fun `an unknown platform yields the disabled publisher`() {
        val publisher = HyperframesPublisherResolver.resolve(true, "vimeo", credentialsFile())

        assertThat(publisher).isInstanceOf(DisabledVideoPublisher::class.java)
    }

    @Test
    fun `a missing credentials file yields the disabled publisher`() {
        val publisher = HyperframesPublisherResolver.resolve(true, "youtube", File(tempDir, "absent.yaml"))

        assertThat(publisher).isInstanceOf(DisabledVideoPublisher::class.java)
    }

    @Test
    fun `incomplete credentials yield the disabled publisher`() {
        val publisher = HyperframesPublisherResolver.resolve(true, "youtube", credentialsFile(complete = false))

        assertThat(publisher).isInstanceOf(DisabledVideoPublisher::class.java)
    }

    @Test
    fun `complete credentials yield the youtube publisher`() {
        val publisher = HyperframesPublisherResolver.resolve(true, "youtube", credentialsFile())

        assertThat(publisher).isInstanceOf(YouTubeDataApiPublisher::class.java)
    }

    @Test
    fun `platform match is case-insensitive`() {
        val publisher = HyperframesPublisherResolver.resolve(true, "YouTube", credentialsFile())

        assertThat(publisher).isInstanceOf(YouTubeDataApiPublisher::class.java)
    }
}
