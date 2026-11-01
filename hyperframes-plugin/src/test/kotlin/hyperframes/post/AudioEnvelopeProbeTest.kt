package hyperframes.post

import video.kernel.audio.AudioSilenceDetector
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AudioEnvelopeProbe] (VK-4 US-4e) — the N2 ffmpeg adapter of
 * the shared N1 audio-quality capability. The command is intercepted; no real
 * ffmpeg is needed.
 */
class AudioEnvelopeProbeTest {
    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArrayOutputStream()
        samples.forEach { sample ->
            out.write(sample and 0xFF)
            out.write((sample shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    @Test
    fun `the decode argv overwrites the temp pcm and requests raw mono`() {
        val probe = AudioEnvelopeProbe()
        val audio = File("/tmp/narration-1.wav")
        val pcmFile = File("/tmp/envelope.pcm")

        val args = probe.decodeArgs(audio, pcmFile, AudioEnvelopeProbe.SAMPLE_RATE, "ffmpeg")

        assertEquals("ffmpeg", args.first())
        assertTrue("-y" in args, "ffmpeg must overwrite the temp PCM target")
        assertTrue(args.windowed(2).any { it == listOf("-ac", "1") })
        assertTrue(args.windowed(2).any { it == listOf("-ar", "16000") })
        assertTrue(args.windowed(2).any { it == listOf("-f", "s16le") })
        assertEquals(pcmFile.absolutePath, args.last())
    }

    @Test
    fun `a silent pcm yields a silent envelope`() {
        val probe =
            AudioEnvelopeProbe(
                runner = { _, _, _ ->
                    // 320 samples = one full 20 ms frame @ 16 kHz, all zero.
                    CommandResult(exitCode = 0)
                },
            )
        // The fake runner must write to the temp target (last argv entry).
        val injecting =
            AudioEnvelopeProbe(
                runner = { argv, _, _ ->
                    File(argv.last()).writeBytes(pcm(*IntArray(320) { 0 }))
                    CommandResult(exitCode = 0)
                },
            )
        val audio = File.createTempFile("narration", ".wav").apply { writeText("fake") }

        val frames = injecting.probe(audio)

        assertTrue(frames != null && frames.size == 1, "expected one 20 ms frame, got $frames")
        assertTrue(AudioSilenceDetector.analyze(frames!!).isSilent)
        // The non-injecting probe degrades (no PCM written by the fake).
        assertNull(probe.probe(audio))
    }

    @Test
    fun `a missing file degrades to null`() {
        assertNull(AudioEnvelopeProbe().probe(File("/tmp/does-not-exist-hf-xyz.wav")))
    }
}
