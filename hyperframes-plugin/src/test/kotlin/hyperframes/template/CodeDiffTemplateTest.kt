package hyperframes.template

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD unit tests for [CodeDiffTemplate] — HF-5b.
 *
 * Baby-step (DDD red → green): the value object renders a HyperFrames
 * composition with two code blocks (before / after) and a GSAP timeline
 * that fades out the before block then fades in the after block.
 */
class CodeDiffTemplateTest {

    @Test
    fun `render produces a hyperframes-composition div with data-composition-id`() {
        val template = CodeDiffTemplate(
            id = "refactor-demo",
            beforeCode = "val x = oldFunc()",
            afterCode = "val x = newFunc()"
        )

        val html = template.render()

        assertTrue(html.contains("class=\"sect1 hyperframes-composition\""), "should expose composition role")
        assertContains(html, "data-composition-id=\"refactor-demo\"")
    }

    @Test
    fun `render includes a hyperframes-track with start zero and configured duration`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "val x = 1",
            afterCode = "val x = 2",
            durationSeconds = 5.0
        )

        val html = template.render()

        assertContains(html, "hyperframes-track")
        assertContains(html, "data-start=\"0\"")
        assertContains(html, "data-duration=\"5\"")
    }

    @Test
    fun `default duration is three seconds`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "a",
            afterCode = "b"
        )

        assertEquals(3.0, template.durationSeconds)
    }

    @Test
    fun `render contains before and after code in distinct pre blocks`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "val x = oldFunc()",
            afterCode = "val x = newFunc()"
        )

        val html = template.render()

        assertContains(html, "class=\"hf-code-diff-before")
        assertContains(html, "class=\"hf-code-diff-after")
        assertContains(html, "oldFunc()")
        assertContains(html, "newFunc()")
    }

    @Test
    fun `after block is hidden by default via inline style display none`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "a",
            afterCode = "b"
        )

        val html = template.render()

        assertContains(html, "hf-code-diff-after")
        assertContains(html, "display:none")
    }

    @Test
    fun `render includes a GSAP timeline that fades out before and fades in after`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "a",
            afterCode = "b"
        )

        val html = template.render()

        assertContains(html, "gsap.timeline")
        assertContains(html, "window.__timelines")
        assertContains(html, ".hf-code-diff-before")
        assertContains(html, ".hf-code-diff-after")
        assertContains(html, "opacity: 0")
    }

    @Test
    fun `render escapes HTML special characters in code blocks`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "val list = listOf<Int>()",
            afterCode = "val set = setOf<String>()"
        )

        val html = template.render()

        assertContains(html, "&lt;Int&gt;")
        assertContains(html, "&lt;String&gt;")
        assertFalse(html.contains("<Int>"), "raw angle brackets must be escaped")
    }

    @Test
    fun `kotlin keywords are wrapped in span hf-kw`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "val x = 1",
            afterCode = "fun foo() {}",
            lang = "kotlin"
        )

        val html = template.render()

        assertContains(html, "<span class=\"hf-kw\">val</span>")
        assertContains(html, "<span class=\"hf-kw\">fun</span>")
    }

    @Test
    fun `unknown lang does not wrap keywords but still escapes HTML`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "val x = 1",
            afterCode = "fun foo()",
            lang = "brainfuck"
        )

        val html = template.render()

        assertFalse(html.contains("hf-kw"), "unknown lang must not wrap keywords")
        assertContains(html, "val x = 1")
    }

    @Test
    fun `render exposes the lang class on both pre blocks`() {
        val template = CodeDiffTemplate(
            id = "demo",
            beforeCode = "a",
            afterCode = "b",
            lang = "python"
        )

        val html = template.render()

        assertContains(html, "hf-lang-python")
        val langCount = html.split("hf-lang-python").size - 1
        assertEquals(2, langCount, "both before and after blocks must carry the lang class")
    }
}