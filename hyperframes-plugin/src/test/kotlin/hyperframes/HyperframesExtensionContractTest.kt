package hyperframes

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HF-KERNEL-ADAPTER US-1 — DS(D-A) contract guard.
 *
 * The DSL extension's `outputDir` describes an **output**; it was previously
 * annotated `@InputDirectory` by mistake. This guard pins the intent so the
 * regression cannot silently reappear.
 */
class HyperframesExtensionContractTest {

    private fun getter(name: String) = HyperframesExtension::class.java.getMethod(name)

    @Test
    fun `outputDir getter is annotated as an output directory`() {
        val outputDir = getter("getOutputDir")

        assertTrue(
            outputDir.isAnnotationPresent(OutputDirectory::class.java),
            "HyperframesExtension.outputDir must be annotated @OutputDirectory (D-A)"
        )
        assertFalse(
            outputDir.isAnnotationPresent(InputDirectory::class.java),
            "HyperframesExtension.outputDir must NOT be annotated @InputDirectory (D-A regression)"
        )
    }

    @Test
    fun `inputDir getter remains an input directory`() {
        val inputDir = getter("getInputDir")

        assertTrue(
            inputDir.isAnnotationPresent(InputDirectory::class.java),
            "HyperframesExtension.inputDir must stay annotated @InputDirectory"
        )
    }
}
