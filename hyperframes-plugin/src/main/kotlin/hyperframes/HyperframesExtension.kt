package hyperframes

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

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

    /** Timeout du rendu vidéo en millisecondes (défaut : 300000 = 5 min) */
    @get:Input
    abstract val renderTimeoutMs: Property<Long>

    /** Chemin vers le script CLI HyperFrames (ex: node_modules/.bin/hyperframes) */
    @get:Input
    @get:Optional
    abstract val cliScript: Property<String>

    /**
     * Chemin vers l'exécutable Node.js (utilisé en test pour bypasser
     * le download du plugin Gradle Node). Si non défini, résolu
     * automatiquement depuis `NodeExtension`.
     */
    @get:Input
    @get:Optional
    abstract val nodeExecutable: Property<String>

    /**
     * HF-7 evolution — optional pre-built pronunciation domain loaded from
     * classpath resources (`domain/<name>.json`).
     *
     * Built-in domains cover generic video-content vocabulary:
     * - `video-fr` — French video production vocabulary
     * - `video-en` — English video production vocabulary
     *
     * When set, the domain is merged with author hints (pronunciation
     * blocks + inline hints) so that author hints override domain hints
     * on conflict (word + language). Domain-only words are injected as
     * the baseline dictionary — the whole point of the configuration.
     *
     * The plugin is a generic public OSS artefact: domain dictionaries
     * cover video vocabulary only. A consumer borough with a private
     * business vocabulary injects its own dictionary via a custom domain
     * file rather than embedding it in the plugin.
     */
    @get:Input
    @get:Optional
    abstract val pronunciationDomain: Property<String>

    init {
        width.convention(1920)
        height.convention(1080)
        fps.convention(30)
        outputName.convention("output")
        renderTimeoutMs.convention(300_000L)
    }
}
