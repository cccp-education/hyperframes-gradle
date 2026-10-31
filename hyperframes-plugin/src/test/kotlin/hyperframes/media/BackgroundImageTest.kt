package hyperframes.media

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * HF-DEMO-QUALITY US-6 — pure background-image domain: parsing, validation and
 * the HTML fragment the composition embeds.
 */
class BackgroundImageTest {

    // ──────────────────────────────────────────────
    // BackgroundImageParser
    // ──────────────────────────────────────────────

    @Test
    fun `parses the file and applies the documented defaults`() {
        val background = BackgroundImageParser.parse("file: assets/img/fiber.jpg")

        assertEquals("assets/img/fiber.jpg", background?.file)
        assertEquals(BackgroundImage.DEFAULT_OPACITY, background?.opacity)
        assertEquals(true, background?.scrim)
        assertEquals(false, background?.grid)
        assertEquals(false, background?.grain)
    }

    @Test
    fun `parses every option`() {
        val background = BackgroundImageParser.parse(
            """
            file: assets/img/datacenter.jpg
            opacity: 0.35
            scrim: false
            grid: true
            grain: true
            """.trimIndent()
        )

        assertEquals("assets/img/datacenter.jpg", background?.file)
        assertEquals(0.35, background?.opacity)
        assertEquals(false, background?.scrim)
        assertEquals(true, background?.grid)
        assertEquals(true, background?.grain)
    }

    @Test
    fun `ignores blank lines comments and unknown keys`() {
        val background = BackgroundImageParser.parse(
            """

            # technical background
            grid: true
            file: assets/img/hexagon.webp
            focus: center
            """.trimIndent()
        )

        assertEquals("assets/img/hexagon.webp", background?.file)
        assertEquals(true, background?.grid)
    }

    @Test
    fun `parses the parallax option`() {
        val background = BackgroundImageParser.parse("file: a.jpg\nparallax: 0.1")

        assertEquals(0.1, background?.parallax)
    }

    @Test
    fun `keeps the default parallax when the value is invalid or out of range`() {
        assertEquals(
            BackgroundImage.DEFAULT_PARALLAX,
            BackgroundImageParser.parse("file: a.jpg\nparallax: 0.9")?.parallax,
        )
        assertEquals(
            BackgroundImage.DEFAULT_PARALLAX,
            BackgroundImageParser.parse("file: a.jpg\nparallax: -0.1")?.parallax,
        )
        assertEquals(
            BackgroundImage.DEFAULT_PARALLAX,
            BackgroundImageParser.parse("file: a.jpg\nparallax: fast")?.parallax,
        )
    }

    @Test
    fun `keeps the default opacity when the value is invalid or out of range`() {
        assertEquals(
            BackgroundImage.DEFAULT_OPACITY,
            BackgroundImageParser.parse("file: a.jpg\nopacity: not-a-number")?.opacity,
        )
        assertEquals(
            BackgroundImage.DEFAULT_OPACITY,
            BackgroundImageParser.parse("file: a.jpg\nopacity: 1.8")?.opacity,
        )
        assertEquals(
            BackgroundImage.DEFAULT_OPACITY,
            BackgroundImageParser.parse("file: a.jpg\nopacity: -0.2")?.opacity,
        )
    }

    @Test
    fun `returns null when the file is missing or blank`() {
        assertNull(BackgroundImageParser.parse(""))
        assertNull(BackgroundImageParser.parse("opacity: 0.4"))
        assertNull(BackgroundImageParser.parse("file:    "))
    }

    // ──────────────────────────────────────────────
    // BackgroundImage invariants
    // ──────────────────────────────────────────────

    @Test
    fun `rejects a blank file and an out-of-range opacity`() {
        assertFailsWith<IllegalArgumentException> { BackgroundImage(file = "   ") }
        assertFailsWith<IllegalArgumentException> { BackgroundImage(file = "a.jpg", opacity = 1.1) }
        assertFailsWith<IllegalArgumentException> { BackgroundImage(file = "a.jpg", opacity = -0.1) }
        assertFailsWith<IllegalArgumentException> { BackgroundImage(file = "a.jpg", parallax = 0.3) }
        assertFailsWith<IllegalArgumentException> { BackgroundImage(file = "a.jpg", parallax = -0.1) }
    }

    // ──────────────────────────────────────────────
    // BackgroundImageHtml
    // ──────────────────────────────────────────────

    @Test
    fun `renders a full-bleed media layer with the scrim by default`() {
        val html = BackgroundImageHtml.render(
            BackgroundImage(file = "assets/img/fiber.jpg", opacity = 0.55)
        )

        assertContains(html, """<div class="hf-bg" aria-hidden="true">""")
        assertContains(html, """<img class="hf-bg-media" src="assets/img/fiber.jpg" alt="" style="opacity:0.55">""")
        assertContains(html, """<div class="hf-bg-scrim"></div>""")
        assertFalse(html.contains("hf-bg-grid"))
        assertFalse(html.contains("hf-bg-grain"))
    }

    @Test
    fun `renders the optional grid and grain overlays`() {
        val html = BackgroundImageHtml.render(
            BackgroundImage(file = "a.jpg", scrim = false, grid = true, grain = true)
        )

        assertFalse(html.contains("hf-bg-scrim"))
        assertContains(html, """<div class="hf-bg-grid"></div>""")
        assertContains(html, """<div class="hf-bg-grain"></div>""")
    }

    @Test
    fun `escapes the file for the html attribute`() {
        val html = BackgroundImageHtml.render(
            BackgroundImage(file = """a&b"c.jpg""")
        )

        assertContains(html, """src="a&amp;b&quot;c.jpg"""")
        assertFalse(html.contains("""a&b"c.jpg"""))
    }
}
