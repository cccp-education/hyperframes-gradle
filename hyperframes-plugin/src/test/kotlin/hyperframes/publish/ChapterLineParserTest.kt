package hyperframes.publish

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * HF-YOUTUBE-PUBLISH US-2 — pure chapter-line parser.
 */
class ChapterLineParserTest {

    @Test
    fun `parses a minute-second timestamp`() {
        val chapter = ChapterLineParser.parseLine("1:30 Setup")

        assertThat(chapter).isEqualTo(video.kernel.publish.VideoChapter(90, "Setup"))
    }

    @Test
    fun `parses an hour timestamp`() {
        val chapter = ChapterLineParser.parseLine("1:00:00 Conclusion")

        assertThat(chapter).isEqualTo(video.kernel.publish.VideoChapter(3600, "Conclusion"))
    }

    @Test
    fun `parses a first chapter at zero`() {
        assertThat(ChapterLineParser.parseLine("0:00 Introduction"))
            .isEqualTo(video.kernel.publish.VideoChapter(0, "Introduction"))
    }

    @Test
    fun `accepts an equals separator`() {
        assertThat(ChapterLineParser.parseLine("1:30 = Setup"))
            .isEqualTo(video.kernel.publish.VideoChapter(90, "Setup"))
    }

    @Test
    fun `ignores blank and comment lines`() {
        val chapters = ChapterLineParser.parse(listOf("", "   ", "# a heading", "0:00 Intro"))

        assertThat(chapters).hasSize(1)
    }

    @Test
    fun `ignores a line without a timestamp`() {
        assertThat(ChapterLineParser.parseLine("Just a title")).isNull()
    }

    @Test
    fun `ignores a line with an empty title`() {
        assertThat(ChapterLineParser.parseLine("1:00")).isNull()
    }
}
