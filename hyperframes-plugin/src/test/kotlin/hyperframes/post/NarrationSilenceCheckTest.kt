package hyperframes.post

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [NarrationSilenceCheck] (VK-4 US-4e) — the pure consumer of
 * the shared N1 silence detector, fed by a fake envelope probe.
 */
class NarrationSilenceCheckTest {

    private fun file(name: String) = File(name)

    @Test
    fun `a silent narration file is flagged`() {
        val silent = file("narration-1.wav")

        assertTrue(NarrationSilenceCheck.isSilent(silent) { DoubleArray(20) { 0.0 } })
        assertEquals(listOf(silent), NarrationSilenceCheck.silentFiles(listOf(silent)) { DoubleArray(20) { 0.0 } })
    }

    @Test
    fun `an audible narration file is not flagged`() {
        val audible = file("narration-2.wav")

        assertFalse(NarrationSilenceCheck.isSilent(audible) { DoubleArray(20) { 0.1 } })
        assertTrue(NarrationSilenceCheck.silentFiles(listOf(audible)) { DoubleArray(20) { 0.1 } }.isEmpty())
    }

    @Test
    fun `an unavailable probe is never read as silent`() {
        val unknown = file("narration-3.wav")

        assertFalse(NarrationSilenceCheck.isSilent(unknown) { null })
        assertTrue(NarrationSilenceCheck.silentFiles(listOf(unknown)) { null }.isEmpty())
    }

    @Test
    fun `the result keeps the input order`() {
        val files = listOf(file("narration-1.wav"), file("narration-2.wav"), file("narration-3.wav"))
        val silent = mapOf(
            "narration-1.wav" to DoubleArray(1) { 0.0 },
            "narration-2.wav" to DoubleArray(1) { 0.1 },
            "narration-3.wav" to DoubleArray(1) { 0.0 },
        )

        val result = NarrationSilenceCheck.silentFiles(files) { silent[it.name] }

        assertEquals(listOf(files[0], files[2]), result)
    }
}
