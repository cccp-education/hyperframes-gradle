package hyperframes.publish

import video.kernel.publish.VideoPublication
import video.kernel.publish.VideoPublicationResult
import video.kernel.publish.VideoPublisher

/**
 * A [VideoPublisher] that never reaches the network (HF-YOUTUBE-PUBLISH US-2).
 *
 * Used when publishing is disabled (`publish.enabled = false`, the default) or
 * when credentials are absent: publication is skipped and reported as
 * [VideoPublicationResult.Degraded] so the build keeps going. Economy of ink.
 */
class DisabledVideoPublisher(private val reason: String = "YouTube publication disabled") : VideoPublisher {

    override fun publish(publication: VideoPublication): VideoPublicationResult =
        VideoPublicationResult.Degraded(reason)
}
