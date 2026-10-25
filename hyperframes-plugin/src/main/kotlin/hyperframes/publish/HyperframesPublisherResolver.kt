package hyperframes.publish

import java.io.File
import video.kernel.publish.VideoPublisher

/**
 * Resolves the [VideoPublisher] for the HyperFrames publish step
 * (HF-YOUTUBE-PUBLISH US-2).
 *
 * Factory pattern (mirrors `CapsuleManager.resolve*`): the resolution order is
 * deliberate and **fail-soft** — a disabled/unconfigured publish yields
 * [DisabledVideoPublisher] (a degraded, network-free publisher) rather than an
 * error, so a build never fails because publishing is not set up.
 *
 * 1. `enabled == false`                     → [DisabledVideoPublisher]
 * 2. `platform` not `youtube`               → [DisabledVideoPublisher]
 * 3. credentials file absent                → [DisabledVideoPublisher]
 * 4. credentials incomplete for upload      → [DisabledVideoPublisher]
 * 5. otherwise                              → [YouTubeDataApiPublisher]
 */
object HyperframesPublisherResolver {

    const val YOUTUBE_PLATFORM: String = "youtube"

    fun resolve(
        enabled: Boolean,
        platform: String,
        credentialsFile: File,
        transport: HttpTransport = JdkHttpTransport(),
        onLog: (String) -> Unit = {},
    ): VideoPublisher {
        if (!enabled) {
            return DisabledVideoPublisher("YouTube publication disabled (publish.enabled=false)")
        }
        if (!platform.equals(YOUTUBE_PLATFORM, ignoreCase = true)) {
            return DisabledVideoPublisher("unsupported publish platform '$platform' (only '$YOUTUBE_PLATFORM')")
        }
        val credentials = YouTubeCredentialsLoader.load(credentialsFile)
            ?: return DisabledVideoPublisher("credentials file not found or empty: ${credentialsFile.name}")
        if (!credentials.canUpload()) {
            return DisabledVideoPublisher(
                "credentials incomplete in ${credentialsFile.name}: missing " +
                    credentials.missingUploadFields().joinToString(", "),
            )
        }
        onLog("YouTube publisher enabled (channel '${credentials.channelId.ifBlank { "unknown" }}')")
        return YouTubeDataApiPublisher(credentials, transport, onLog)
    }
}
