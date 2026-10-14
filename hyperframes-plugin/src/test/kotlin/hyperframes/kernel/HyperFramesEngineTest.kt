package hyperframes.kernel

import video.kernel.FileVisualSource
import video.kernel.VideoRenderRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * SPIKE — HyperFrames engine implements the shared `video.kernel.VideoEngine`
 * port (video-kernel 0.0.2). Verifies the neutral contract fits a whole-document
 * engine with no segments/durations/viewport.
 */
class HyperFramesEngineTest {

    @TempDir
    lateinit var tempDir: File

    private fun config() = HyperFramesEngineConfig(
        nodeExecutable = "/fake/node",
        cliScript = "/fake/hyperframes",
        width = 1920,
        height = 1080,
    )

    private fun request(): VideoRenderRequest {
        val html = File(tempDir, "index.html").apply { writeText("<html><body>hi</body></html>") }
        return VideoRenderRequest(
            name = "output",
            visual = FileVisualSource(html),
            outputDir = File(tempDir, "out"),
        )
    }

    @Test
    fun `renders the whole document to an MP4 and returns it as produced`() {
        val engine = HyperFramesEngine(config(), runner = { argv, workingDir, _ ->
            // The real CLI writes the file named by --output; emulate it.
            val outputPath = argv[argv.indexOf("--output") + 1]
            File(outputPath).apply { parentFile?.mkdirs(); writeText("fake-mp4") }
            HyperFramesProcessResult(exitCode = 0)
        })

        val result = engine.render(request())

        assertEquals("output.mp4", result.produced?.name)
        assertTrue(result.failedUnits.isEmpty())
        // Uniform outcome descriptor (video-kernel 0.0.3) is filled.
        assertEquals(1920, result.outcome?.width)
        assertEquals(1080, result.outcome?.height)
        assertEquals("h264", result.outcome?.codec)
        assertTrue((result.outcome?.sizeBytes ?: 0L) > 0L)
    }

    @Test
    fun `a failing CLI yields no produced video, without throwing`() {
        val engine = HyperFramesEngine(config(), runner = { _, _, _ ->
            HyperFramesProcessResult(exitCode = 1, output = "boom")
        })

        val result = engine.render(request())

        assertNull(result.produced)
        assertTrue(result.failedUnits.isEmpty())
    }

    @Test
    fun `engine identity is stable and availability reflects configured files`() {
        val engine = HyperFramesEngine(config(), runner = { _, _, _ -> HyperFramesProcessResult(0) })

        assertEquals("hyperframes", engine.name())
        assertTrue(!engine.isAvailable(), "fake node/cli paths do not exist")
    }

    @Test
    fun `the request timeout is forwarded to the runner, zero means the engine default`() {
        val seen = mutableListOf<Long>()
        val engine = HyperFramesEngine(config(), runner = { _, _, timeout ->
            seen.add(timeout)
            HyperFramesProcessResult(exitCode = 1)
        })

        engine.render(request().copy(timeoutMillis = 1_234))
        engine.render(request().copy(timeoutMillis = 0))

        assertEquals(1_234L, seen[0])
        assertEquals(300_000L, seen[1])
    }

    // ──────────────────────────────────────────────
    // US-3 (S-018) — renderDetailed exposes the raw CLI outcome for diagnostics
    // ──────────────────────────────────────────────

    @Test
    fun `renderDetailed exposes the raw process outcome of a failing CLI`() {
        val engine = HyperFramesEngine(config(), runner = { _, _, _ ->
            HyperFramesProcessResult(exitCode = 7, output = "boom")
        })

        val outcome = engine.renderDetailed(request())

        assertNull(outcome.result.produced)
        assertEquals(7, outcome.process.exitCode)
        assertEquals("boom", outcome.process.output)
        assertFalse(outcome.process.timedOut)
    }

    @Test
    fun `renderDetailed exposes a timed out CLI`() {
        val engine = HyperFramesEngine(config(), runner = { _, _, _ ->
            HyperFramesProcessResult(exitCode = -1, timedOut = true)
        })

        val outcome = engine.renderDetailed(request())

        assertTrue(outcome.process.timedOut)
        assertNull(outcome.result.produced)
    }

    @Test
    fun `render returns the same result as renderDetailed`() {
        val engine = HyperFramesEngine(config(), runner = { argv, _, _ ->
            val outputPath = argv[argv.indexOf("--output") + 1]
            File(outputPath).apply { parentFile?.mkdirs(); writeText("fake-mp4") }
            HyperFramesProcessResult(exitCode = 0)
        })

        val direct = engine.render(request())
        val detailed = engine.renderDetailed(request()).result

        assertEquals(direct.produced?.name, detailed.produced?.name)
        assertEquals(direct.outcome?.width, detailed.outcome?.width)
    }
}
