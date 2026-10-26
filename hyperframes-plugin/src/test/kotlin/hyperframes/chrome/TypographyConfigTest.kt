package hyperframes.chrome

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-4 — typography configuration guard.
 */
class TypographyConfigTest {

    @Test
    fun `defaults follow the reference demo direction`() {
        val typography = TypographyConfig()

        assertEquals(""""League Gothic", "Arial Narrow", Impact, sans-serif""", typography.displayFont)
        assertEquals(""""IBM Plex Mono", "JetBrains Mono", ui-monospace, monospace""", typography.monoFont)
    }

    @Test
    fun `blank font stacks are rejected`() {
        assertFailsWith<IllegalArgumentException> { TypographyConfig(displayFont = "  ") }
        assertFailsWith<IllegalArgumentException> { TypographyConfig(monoFont = "") }
    }

    @Test
    fun `css metacharacters cannot escape the declaration`() {
        assertFailsWith<IllegalArgumentException> { TypographyConfig(displayFont = "Inter;color:red") }
        assertFailsWith<IllegalArgumentException> { TypographyConfig(monoFont = "url(evil)") }
        assertFailsWith<IllegalArgumentException> { TypographyConfig(displayFont = "Inter}") }
    }

    @Test
    fun `named families declare a local font face and generics are skipped`() {
        val css = TypographyConfig().fontFaceCss()

        assertContains(css, "@font-face{font-family:'League Gothic';src:local('League Gothic')}")
        assertContains(css, "@font-face{font-family:'Arial Narrow';src:local('Arial Narrow')}")
        assertContains(css, "@font-face{font-family:'Impact';src:local('Impact')}")
        assertContains(css, "@font-face{font-family:'IBM Plex Mono';src:local('IBM Plex Mono')}")
        assertContains(css, "@font-face{font-family:'JetBrains Mono';src:local('JetBrains Mono')}")
        assertFalse(css.contains("'sans-serif'"))
        assertFalse(css.contains("'monospace'"))
        assertFalse(css.contains("'ui-monospace'"))
    }

    @Test
    fun `font faces are deduplicated`() {
        val css = TypographyConfig(
            displayFont = "Inter, Inter, sans-serif",
            monoFont = "Inter, monospace",
        ).fontFaceCss()

        assertEquals(1, Regex("""font-family:'Inter'""").findAll(css).count())
    }
}
