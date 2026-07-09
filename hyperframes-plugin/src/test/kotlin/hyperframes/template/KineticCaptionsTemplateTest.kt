package hyperframes.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD unit tests for [KineticCaptionsTemplate] — HF-5d.
 *
 * Baby-step (DDD red -> green): the value object renders a HyperFrames
 * composition with timed captions that appear at their start time and
 * disappear at their end time. The GSAP timeline shows and hides each
 * caption according to its time window.
 */
class KineticCaptionsTemplateTest {

    @Test
    fun `render produces a hyperframes-composition div with data-composition-id`() {
        val template = KineticCaptionsTemplate(
            id = "intro-captions",
            captions = listOf(Caption(0.0, 2.0, "Hello world"))
        )

        val html = template.render()

        assertTrue(html.contains("class=\"sect1 hyperframes-composition\""), "should expose composition role")
        assertContains(html, "data-composition-id=\"intro-captions\"")
    }

    @Test
    fun `render includes a hyperframes-track with start zero and configured duration`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A")),
            durationSeconds = 7.0
        )

        val html = template.render()

        assertContains(html, "hyperframes-track")
        assertContains(html, "data-start=\"0\"")
        assertContains(html, "data-duration=\"7\"")
    }

    @Test
    fun `default duration is five seconds`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A"))
        )

        assertEquals(5.0, template.durationSeconds)
    }

    @Test
    fun `render contains a kinetic-captions container div`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A"))
        )

        val html = template.render()

        assertContains(html, "class=\"hf-kinetic-captions\"")
    }

    @Test
    fun `render contains one caption div per caption with data-start and data-end`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(
                Caption(0.0, 2.0, "Hello"),
                Caption(2.5, 4.5, "World")
            )
        )

        val html = template.render()

        assertContains(html, "class=\"hf-kinetic-caption\"")
        assertContains(html, "data-start=\"0.0\"")
        assertContains(html, "data-end=\"2.0\"")
        assertContains(html, "data-start=\"2.5\"")
        assertContains(html, "data-end=\"4.5\"")
    }

    @Test
    fun `render includes the caption text inside each caption div`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "Hello world"))
        )

        val html = template.render()

        assertContains(html, ">Hello world<")
    }

    @Test
    fun `captions are hidden by default via inline style display none`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A"))
        )

        val html = template.render()

        assertContains(html, "display:none")
    }

    @Test
    fun `render includes a GSAP timeline that shows and hides captions`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A"))
        )

        val html = template.render()

        assertContains(html, "gsap.timeline")
        assertContains(html, "window.__timelines")
        assertContains(html, ".hf-kinetic-caption")
        assertContains(html, "opacity: 1")
        assertContains(html, "opacity: 0")
    }

    @Test
    fun `timeline shows each caption at its start time`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(
                Caption(1.5, 3.0, "Late"),
                Caption(0.0, 1.0, "Early")
            )
        )

        val html = template.render()

        assertContains(html, ", 1.5)")
        assertContains(html, ", 0.0)")
    }

    @Test
    fun `timeline hides each caption at its end minus start offset`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = listOf(Caption(0.0, 2.0, "A"))
        )

        val html = template.render()

        assertContains(html, "2.0")
    }

    @Test
    fun `empty captions renders an empty container without caption divs`() {
        val template = KineticCaptionsTemplate(
            id = "demo",
            captions = emptyList()
        )

        val html = template.render()

        assertContains(html, "class=\"hf-kinetic-captions\"")
        assertFalse(html.contains("hf-kinetic-caption\""), "empty captions must not contain any caption div")
    }

    @Test
    fun `Caption rejects a negative start`() {
        assertThrows<IllegalArgumentException> {
            Caption(-0.5, 2.0, "A")
        }
    }

    @Test
    fun `Caption rejects an end before start`() {
        assertThrows<IllegalArgumentException> {
            Caption(3.0, 1.0, "A")
        }
    }

    @Test
    fun `Caption rejects blank text`() {
        assertThrows<IllegalArgumentException> {
            Caption(0.0, 2.0, "  ")
        }
    }
}