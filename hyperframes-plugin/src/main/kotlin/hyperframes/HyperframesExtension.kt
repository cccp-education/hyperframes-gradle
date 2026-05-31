package hyperframes

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory

/**
 * DSL d'extension [hyperframes] pour configure le pipeline HyperFrames.
 *
 * Propriétés configurables dans le bloc `hyperframes { }` du build.gradle.kts.
 */
abstract class HyperframesExtension {

    /** Répertoire contenant le ou les fichiers AsciiDoc source */
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    /** Répertoire de sortie pour le HTML intermédiaire et le MP4 final */
    @get:InputDirectory
    abstract val outputDir: DirectoryProperty

    /** Largeur de la vidéo en pixels (défaut : 1920) */
    @get:Input
    abstract val width: Property<Int>

    /** Hauteur de la vidéo en pixels (défaut : 1080) */
    @get:Input
    abstract val height: Property<Int>

    /** Images par seconde (défaut : 30) */
    @get:Input
    abstract val fps: Property<Int>

    /** Nom du fichier de sortie sans extension (défaut : "output") */
    @get:Input
    abstract val outputName: Property<String>

    init {
        width.convention(1920)
        height.convention(1080)
        fps.convention(30)
        outputName.convention("output")
    }
}
