package hyperframes

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import java.io.File

/**
 * Tâche Gradle qui convertit un fichier AsciiDoc (index.adoc) en HTML
 * via AsciidoctorJ, puis post-processe le HTML avec [HyperframesHtmlProcessor]
 * pour ajouter les data-* attributes nécessaires au rendu HyperFrames.
 *
 * HF-1 : Conversion AsciiDoc → HTML standard.
 * HF-3 : Post-processing → stage div, data-composition-id, data-track-*, animations.
 */
abstract class GenerateHyperframesHtmlTask : DefaultTask() {

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val width: Property<Int>

    @get:Input
    abstract val height: Property<Int>

    @get:Input
    abstract val fps: Property<Int>

    @get:Input
    abstract val outputName: Property<String>

    @TaskAction
    fun generate() {
        val inputFile = inputDir.asFile.get()
        val outputFile = outputDir.asFile.get()
        outputFile.mkdirs()

        val sourceFile = File(inputFile, "index.adoc")
        require(sourceFile.exists()) {
            buildString {
                appendLine("AsciiDoc source not found at: ${sourceFile.absolutePath}")
                append("Create an index.adoc file in ${inputFile.absolutePath} or set inputDir to the correct path.")
            }
        }

        val asciidoctor = Asciidoctor.Factory.create()
        try {
            val attrs = Attributes.builder()
                .attribute("hyperframes-width", width.get().toString())
                .attribute("hyperframes-height", height.get().toString())
                .attribute("hyperframes-fps", fps.get().toString())
                .attribute("hyperframes-output", "${outputName.get()}.mp4")
                .build()

            val options = Options.builder()
                .backend("html5")
                .safe(SafeMode.UNSAFE)
                .toDir(outputFile)
                .attributes(attrs)
                .build()

            // Étape 1 : Conversion AsciiDoc → HTML (AsciidoctorJ)
            asciidoctor.convertFile(sourceFile, options)

            val generatedHtml = outputFile.resolve("index.html")
            require(generatedHtml.exists()) {
                "Generated HTML not found: ${generatedHtml.absolutePath}"
            }

            // Étape 2 : Post-processing HyperFrames (stage, data-*, GSAP)
            val rawHtml = generatedHtml.readText()
            val processor = HyperframesHtmlProcessor(
                width = width.get(),
                height = height.get(),
                fps = fps.get(),
                outputName = outputName.get()
            )
            val enhancedHtml = processor.enhance(rawHtml)
            generatedHtml.writeText(enhancedHtml)

            logger.lifecycle("HyperFrames HTML generated: ${generatedHtml.absolutePath}")
            logger.info("   Enhanced with HyperFrames data-* attributes (HF-3)")
        } finally {
            asciidoctor.shutdown()
        }
    }
}
