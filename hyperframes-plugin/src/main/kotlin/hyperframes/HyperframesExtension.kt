package hyperframes

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

/**
 * DSL d'extension [hyperframes] pour configure le pipeline HyperFrames.
 *
 * Propriétés configurables dans le bloc `hyperframes { }` du build.gradle.kts.
 */
abstract class HyperframesExtension {

    /** Répertoire contenant le ou les fichiers AsciiDoc source */
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    /**
     * Répertoire de sortie pour le HTML intermédiaire et le MP4 final.
     *
     * D-A (S-017) — cette propriété était annotée `@InputDirectory` par erreur :
     * elle décrit une **sortie**. Le noyau du plugin porte déjà l'annotation
     * `@OutputDirectory` sur les tâches ; l'annotation ici exprime l'intention DSL.
     */
    @get:OutputDirectory
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

    /**
     * D-C (S-017) — chemin vers l'exécutable `ffprobe` utilisé pour mesurer la
     * durée réelle du MP4 produit (descripteur d'issue N3 `durationSeconds`).
     * Défaut : `ffprobe` résolu sur le `PATH`. Absent → dégradation silencieuse
     * (`durationSeconds = 0.0`), jamais un échec de build.
     */
    @get:Input
    @get:Optional
    abstract val ffprobeExecutable: Property<String>

    /**
     * D-F (S-017) — `strict` transforme les dégradations silencieuses en échec.
     * Par défaut `false` (dégradé, backward-compatible). Quand `true`, une
     * collecte sans artefacts rendus (MP4/HTML) échoue la tâche au lieu de
     * seulement journaliser.
     */
    @get:Input
    abstract val strict: Property<Boolean>

    /**
     * VK-4 US-4c — optional subtitle file (SRT/VTT) burned into the rendered MP4
     * by `postProcessHyperframes` through the shared `SubtitleBurnIn` port.
     */
    @get:Input
    @get:Optional
    abstract val subtitleFile: Property<String>

    /** VK-4 US-4b — enable audio post-production in `postProcessHyperframes`. */
    @get:Input
    abstract val audioPostEnabled: Property<Boolean>

    /** Optional background-music file mixed under the voice track. */
    @get:Input
    @get:Optional
    abstract val bgmFile: Property<String>

    /** Background-music level under the voice, in dB (default `-18.0`). */
    @get:Input
    abstract val bgmLevelDb: Property<Double>

    /** Target loudness for normalization, in LUFS (default `-16.0`). */
    @get:Input
    abstract val loudnessTargetLufs: Property<Double>

    /** Lower the background bed while the voice speaks. */
    @get:Input
    abstract val duckingEnabled: Property<Boolean>

    /** Optional `ffmpeg` executable for post-production (default: PATH). */
    @get:Input
    @get:Optional
    abstract val ffmpegExecutable: Property<String>

    /** VK-4 US-4d — optional TTS executable for narration (default: `espeak`). */
    @get:Input
    @get:Optional
    abstract val ttsExecutable: Property<String>

    /** Optional narration voice (e.g. `fr`, `en`); blank = engine default. */
    @get:Input
    @get:Optional
    abstract val ttsVoice: Property<String>

    init {
        width.convention(1920)
        height.convention(1080)
        fps.convention(30)
        outputName.convention("output")
        renderTimeoutMs.convention(300_000L)
        strict.convention(false)
        audioPostEnabled.convention(false)
        bgmLevelDb.convention(-18.0)
        loudnessTargetLufs.convention(-16.0)
        duckingEnabled.convention(false)
    }
}
