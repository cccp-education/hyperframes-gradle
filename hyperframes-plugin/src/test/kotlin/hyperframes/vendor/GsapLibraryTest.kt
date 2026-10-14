package hyperframes.vendor

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HF-CR3-1 — unit tests for the vendored GSAP runtime.
 *
 * The plugin must no longer depend on the GSAP CDN at render time: the
 * runtime is embedded as a classpath resource and inlined into the generated
 * HTML `<head>`. These tests pin the vendoring contract:
 * - the vendored script ships with the plugin,
 * - the GreenSock license banner is preserved,
 * - the external CDN is never referenced.
 */
class GsapLibraryTest {

    @Test
    fun `reports the vendored GSAP version`() {
        assertEquals("3.12.5", GsapLibrary.VERSION)
    }

    @Test
    fun `inline script wraps the runtime in a script element`() {
        val inline = GsapLibrary.inlineScript()

        assertTrue(inline.startsWith("<script>"), "the runtime must be inlined in a <script> element")
        assertTrue(inline.trimEnd().endsWith("</script>"), "the inline script element must be closed")
        assertContains(inline, "gsap")
    }

    @Test
    fun `preserves the GreenSock license header`() {
        val inline = GsapLibrary.inlineScript()

        assertContains(inline, "/*!")
        assertContains(inline, "GSAP 3.12.5")
        assertContains(inline, "GreenSock")
    }

    @Test
    fun `never references the external CDN`() {
        val inline = GsapLibrary.inlineScript()

        assertFalse(inline.contains("cdnjs.cloudflare.com"), "the vendored runtime must not need the CDN")
        assertFalse(inline.contains("src=\"http"), "no external script source")
    }
}
