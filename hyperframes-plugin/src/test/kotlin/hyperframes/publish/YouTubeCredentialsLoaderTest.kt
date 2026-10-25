package hyperframes.publish

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * HF-YOUTUBE-PUBLISH US-2 — pure credentials loader + redaction (Rule 6).
 */
class YouTubeCredentialsLoaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parses a youtube section with quoted values`() {
        val values = YouTubeCredentialsLoader.parse(
            """
            youtube:
              clientId: "abc.apps.googleusercontent.com"
              clientSecret: "sekret"
              refreshToken: "refresh"
              channelId: "UC123"
              apiKey: 'AIza'
            """.trimIndent(),
        )

        assertThat(values["clientid"]).isEqualTo("abc.apps.googleusercontent.com")
        assertThat(values["clientsecret"]).isEqualTo("sekret")
        assertThat(values["refreshtoken"]).isEqualTo("refresh")
        assertThat(values["channelid"]).isEqualTo("UC123")
        assertThat(values["apikey"]).isEqualTo("AIza")
    }

    @Test
    fun `ignores comments and blank lines`() {
        val values = YouTubeCredentialsLoader.parse(
            """
            # a comment
            youtube:
              clientId: abc

              clientSecret: s # inline comment
            """.trimIndent(),
        )

        assertThat(values["clientid"]).isEqualTo("abc")
        assertThat(values["clientsecret"]).isEqualTo("s")
    }

    @Test
    fun `normalises snake_case and kebab-case keys`() {
        val values = YouTubeCredentialsLoader.parse(
            """
            youtube:
              client_id: a
              client-secret: b
              refresh_token: c
              channel-id: UC123
            """.trimIndent(),
        )

        assertThat(values["clientid"]).isEqualTo("a")
        assertThat(values["clientsecret"]).isEqualTo("b")
        assertThat(values["refreshtoken"]).isEqualTo("c")
        assertThat(values["channelid"]).isEqualTo("UC123")
    }

    @Test
    fun `missing upload fields are reported by name only`() {
        val credentials = YouTubeCredentials(clientId = "a", clientSecret = "", refreshToken = "")

        assertThat(credentials.missingUploadFields()).containsExactly("clientSecret", "refreshToken")
        assertThat(credentials.canUpload()).isFalse()
    }

    @Test
    fun `load returns null for a missing file`() {
        assertThat(YouTubeCredentialsLoader.load(File(tempDir, "absent.yaml"))).isNull()
    }

    @Test
    fun `load reads a credentials file`() {
        val file = File(tempDir, "youtube.yaml")
        file.writeText("youtube:\n  clientId: a\n  clientSecret: b\n  refreshToken: c\n")

        val credentials = YouTubeCredentialsLoader.load(file)

        assertThat(credentials).isNotNull
        assertThat(credentials!!.canUpload()).isTrue()
    }

    @Test
    fun `canUpload is false without a refresh token`() {
        val credentials = YouTubeCredentials(clientId = "a", clientSecret = "b", refreshToken = "")

        assertThat(credentials.canUpload()).isFalse()
    }

    @Test
    fun `toString redacts every secret`() {
        val credentials = YouTubeCredentials(
            clientId = "1234567890.apps.googleusercontent.com",
            clientSecret = "super-secret-value",
            refreshToken = "1//0refresh-token-value",
            channelId = "UC123",
            apiKey = "AIzaSecretKey",
        )

        val text = credentials.toString()

        assertThat(text).doesNotContain("super-secret-value")
        assertThat(text).doesNotContain("0refresh-token-value")
        assertThat(text).doesNotContain("AIzaSecretKey")
        assertThat(text).contains("UC123")
    }
}
