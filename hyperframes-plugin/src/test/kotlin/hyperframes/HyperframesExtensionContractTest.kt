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
 * annotated `@InputDirectory` by mistake. S-026 additionally removed the
 * `@OutputDirectory` annotation S-017 had put on the *extension*: Gradle task
 * I/O annotations belong on **task** properties, never on the extension — using
 * the extension's `@OutputDirectory` as another task's input makes Gradle fail
 * with "declared as an output property of extension … but does not have a task
 * associated with it". This guard pins both intents so neither regression can
 * silently reappear.
 */
class HyperframesExtensionContractTest {

    private fun getter(name: String) = HyperframesExtension::class.java.getMethod(name)

    @Test
    fun `outputDir getter is not annotated as an input directory`() {
        val outputDir = getter("getOutputDir")

        assertFalse(
            outputDir.isAnnotationPresent(InputDirectory::class.java),
            "HyperframesExtension.outputDir must NOT be annotated @InputDirectory (D-A regression)"
        )
    }

    @Test
    fun `outputDir getter carries no task output annotation on the extension`() {
        val outputDir = getter("getOutputDir")

        assertFalse(
            outputDir.isAnnotationPresent(OutputDirectory::class.java),
            "HyperframesExtension.outputDir must NOT be annotated @OutputDirectory (S-026: task I/O belongs on tasks)"
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
