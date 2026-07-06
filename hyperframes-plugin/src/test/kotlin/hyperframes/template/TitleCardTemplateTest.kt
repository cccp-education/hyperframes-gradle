package hyperframes.template

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD unit tests for [TitleCardTemplate] — HF-5a.
 *
 * Baby-step 1 (DDD red): the value object renders a HyperFrames composition
 * with fade-in animation, logo, title and subtitle.
 */
class TitleCardTemplateTest {

    @Test
    fun `render produces a hyperframes-composition div with data-composition-id`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )

        val html = template.render()

        assertTrue(html.contains("class=\"sect1 hyperframes-composition\""), "should expose composition role")
        assertContains(html, "data-composition-id=\"intro\"")
    }

    @Test
    fun `render includes title and subtitle text`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )

        val html = template.render()

        assertContains(html, "My Formation")
        assertContains(html, "Module 01")
    }

    @Test
    fun `render includes a fade-in GSAP animation timeline`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )

        val html = template.render()

        assertContains(html, "gsap.timeline")
        assertContains(html, "opacity: 0")
        assertContains(html, "window.__timelines")
    }

    @Test
    fun `render includes a track with start zero and configured duration`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01",
            durationSeconds = 3.0
        )

        val html = template.render()

        assertContains(html, "hyperframes-track")
        assertContains(html, "data-start=\"0\"")
        assertContains(html, "data-duration=\"3\"")
    }

    @Test
    fun `render without logo omits img tag`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )

        val html = template.render()

        assertTrue(!html.contains("<img"), "no img tag when logo is absent")
    }

    @Test
    fun `render with logo includes img tag with the logo path`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01",
            logoPath = "assets/logo.png"
        )

        val html = template.render()

        assertContains(html, "<img src=\"assets/logo.png\"")
    }

    @Test
    fun `default duration is two seconds`() {
        val template = TitleCardTemplate(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )

        assertEquals(2.0, template.durationSeconds)
    }
}