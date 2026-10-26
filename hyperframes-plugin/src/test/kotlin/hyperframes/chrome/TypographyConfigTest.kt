package hyperframes.chrome

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
