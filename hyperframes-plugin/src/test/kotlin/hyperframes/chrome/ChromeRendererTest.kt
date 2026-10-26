package hyperframes.chrome

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HF-DEMO-QUALITY US-4 — pure chrome renderer contract.
 */
class ChromeRendererTest {

    @Test
    fun `timecode formats hours minutes seconds and frames`() {
        assertEquals("00:00:00:00", ChromeRenderer.formatTimecode(0.0, 30))
        assertEquals("00:00:05:00", ChromeRenderer.formatTimecode(5.0, 30))
        assertEquals("01:01:01:15", ChromeRenderer.formatTimecode(3661.5, 30))
    }

    @Test
    fun `timecode rounds to the nearest frame and clamps negatives`() {
        assertEquals("00:00:01:15", ChromeRenderer.formatTimecode(1.5, 30))
        assertEquals("00:00:00:00", ChromeRenderer.formatTimecode(-3.0, 30))
    }

    @Test
    fun `disabled chrome renders nothing`() {
        assertEquals("", ChromeRenderer.sceneChrome(ChromeConfig(), 0.0, 30, 1, 3))
        assertEquals("", ChromeRenderer.progressBar(ChromeConfig()))
        assertEquals("", ChromeRenderer.rootProgressTween(ChromeConfig(), 10.0))
    }

    @Test
    fun `enabled chrome renders four marks a timecode and the scene counter`() {
        val html = ChromeRenderer.sceneChrome(
            ChromeConfig(enabled = true, label = "HYP-FRAMES / LAB"),
            sceneStartSeconds = 10.0,
            fps = 30,
            sceneNumber = 2,
            sceneTotal = 5,
        )

        assertEquals(4, Regex("<span></span>").findAll(html).count())
        assertContains(html, """class="hf-chrome"""")
        assertContains(html, """class="hf-chrome-timecode">00:00:10:00<""")
        assertContains(html, "HYP-FRAMES / LAB · 02 / 05")
        assertContains(html, "data-layout-allow-occlusion")
    }

    @Test
    fun `chrome can hide the timecode or the corner marks`() {
        val html = ChromeRenderer.sceneChrome(
            ChromeConfig(enabled = true, timecode = false, cornerMarks = false),
            sceneStartSeconds = 3.0,
            fps = 24,
            sceneNumber = 1,
            sceneTotal = 2,
        )

        assertFalse(html.contains("hf-chrome-timecode"))
        assertFalse(html.contains("hf-chrome-marks"))
        assertContains(html, "01 / 02")
    }

    @Test
    fun `progress bar and tween are emitted only when enabled`() {
        val config = ChromeConfig(enabled = true, progressBar = true)
        assertContains(ChromeRenderer.progressBar(config), """id="hf-chrome-progress"""")
        assertContains(
            ChromeRenderer.rootProgressTween(config, 12.0),
            """rootTl.fromTo("#hf-chrome-progress span", { scaleX: 0 }, { scaleX: 1, duration: 12, ease: "none" }, 0);"""
        )
    }

    @Test
    fun `progress bar honours its own switch`() {
        val config = ChromeConfig(enabled = true, progressBar = false)
        assertEquals("", ChromeRenderer.progressBar(config))
        assertEquals("", ChromeRenderer.rootProgressTween(config, 12.0))
    }

    @Test
    fun `label is escaped`() {
        val html = ChromeRenderer.sceneChrome(
            ChromeConfig(enabled = true, label = "<script>"),
            sceneStartSeconds = 0.0,
            fps = 30,
            sceneNumber = 1,
            sceneTotal = 1,
        )

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
