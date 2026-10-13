package hyperframes.contract

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * HF-KERNEL-ADAPTER US-1 — DS(D-C) unit tests.
 *
 * Baby-step TDD — pins the `ffprobe` parsing contract and the degraded behaviour
 * of the probe (missing executable / failure / timeout → `0.0`, never a throw).
 */
class FfprobeDurationProbeTest {

    @TempDir
    lateinit var tempDir: Path

    // ──────────────────────────────────────────────
    // FfprobeDurationParser — pure
    // ──────────────────────────────────────────────

    @Test
    fun `parses a single decimal line`() {
        assertEquals(12.5, FfprobeDurationParser.parse("12.5\n"))
    }

    @Test
    fun `parses an integer duration`() {
        assertEquals(60.0, FfprobeDurationParser.parse("60"))
    }

    @Test
    fun `ignores leading and trailing blank lines`() {
        assertEquals(3.25, FfprobeDurationParser.parse("\n  3.25  \n\n"))
    }

    @Test
    fun `returns null on empty output`() {
        assertNull(FfprobeDurationParser.parse(""))
        assertNull(FfprobeDurationParser.parse("\n  \n"))
    }

    @Test
    fun `returns null on non numeric output`() {
        assertNull(FfprobeDurationParser.parse("N/A\n"))
    }

    @Test
    fun `returns null on negative or non finite output`() {
        assertNull(FfprobeDurationParser.parse("-1.0"))
        assertNull(FfprobeDurationParser.parse("NaN"))
        assertNull(FfprobeDurationParser.parse("Infinity"))
    }

    // ──────────────────────────────────────────────
    // FfprobeDurationProbe — with injected runner
    // ──────────────────────────────────────────────

    private fun writeMp4(name: String = "video.mp4", size: Int = 256): File {
        val f = tempDir.toFile().resolve(name)
        f.writeBytes(ByteArray(size))
        return f
    }

    @Test
    fun `probe returns the parsed duration`() {
        val seenArgs = mutableListOf<List<String>>()
        val probe = FfprobeDurationProbe("ffprobe", runner = { argv, _ ->
            seenArgs.add(argv)
            "12.5\n"
        })

        assertEquals(12.5, probe.probeSeconds(writeMp4()))
        assertEquals("ffprobe", seenArgs.first().first())
    }

    @Test
    fun `probe degrades to zero when the output is unparsable`() {
        val probe = FfprobeDurationProbe("ffprobe", runner = { _, _ -> "" })

        assertEquals(0.0, probe.probeSeconds(writeMp4()))
    }

    @Test
    fun `probe degrades to zero when the file is missing`() {
        var invoked = false
        val probe = FfprobeDurationProbe("ffprobe", runner = { _, _ ->
            invoked = true
            "12.5"
        })

        assertEquals(0.0, probe.probeSeconds(tempDir.toFile().resolve("missing.mp4")))
        assertEquals(false, invoked, "the runner must not be called for a missing file")
    }
}
