package hyperframes.publish

import video.kernel.publish.VideoChapter

/**
 * Parses author-provided chapter lines into neutral [VideoChapter]s
 * (HF-YOUTUBE-PUBLISH US-2).
 *
 * Accepted line forms (the `<timestamp> <title>` shape YouTube itself
 * recognises, tolerating a `=` or `:` separator used in build scripts):
 *
 * ```
 * 0:00 Introduction
 * 1:30 = Setup
 * 1:00:00 Conclusion
 * ```
 *
 * Pure — no I/O. Lines that are blank, comment-only (`#`) or whose timestamp
 * cannot be parsed are ignored (defensive: a typo never breaks the build).
 */
object ChapterLineParser {

    fun parse(lines: List<String>): List<VideoChapter> =
        lines.mapNotNull(::parseLine)

    /** Parses one line, or `null` when it carries no usable timestamp/title. */
    fun parseLine(line: String): VideoChapter? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val match = PATTERN.matchEntire(trimmed) ?: return null
        val (hours, minutes, seconds, rawTitle) = match.destructured
        val title = rawTitle.trim().trimStart('=', ':').trim()
        if (title.isEmpty()) return null

        val totalSeconds = (hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()
        return VideoChapter(totalSeconds, title)
    }

    // H:MM:SS or M:SS, then a separator space (optional '=' / ':'), then the title.
    private val PATTERN =
        Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})[\s:=]+(.+)$""")
}
