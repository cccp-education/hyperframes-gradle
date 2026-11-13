package hyperframes

import hyperframes.chrome.ChromeConfig
import hyperframes.chrome.TypographyConfig
import hyperframes.entrance.EntranceConfig
import hyperframes.notes.SpeakerNotesExtractor
import hyperframes.pronunciation.PronunciationDomainLoader
import hyperframes.sound.MusicDeclaration
import hyperframes.sound.MusicLibrary
import hyperframes.sound.MusicLibraryManifestLoader
import hyperframes.vendor.GsapLibrary
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that converts an AsciiDoc file (index.adoc) to HTML
 * via AsciidoctorJ, then post-processes the HTML with [HyperframesHtmlProcessor]
 * to add the data-* attributes needed for HyperFrames rendering.
 *
 * HF-1: AsciiDoc -> standard HTML conversion.
 * HF-3: Post-processing -> stage div, data-composition-id, data-track-*, animations.
 *
 * D-F (S-022) — `@CacheableTask`: the AsciiDoc -> HTML transform is deterministic
 * for a given source + configuration, so Gradle can cache it (build cache) and
 * mark it UP-TO-DATE. `RenderHyperframesTask` (external CLI process) and
 * `CollectHyperframesRetrieveTask` (reads the shared artifacts directory) stay
 * `@DisableCachingByDefault`.
 */
@CacheableTask
abstract class GenerateHyperframesHtmlTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
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

    /**
     * HF-7 evolution — optional pre-built pronunciation domain name
     * (e.g. `video-fr`, `video-en`). When set, the domain dictionary is
     * loaded from classpath resources and merged with author hints.
     */
    @get:Input
    @get:Optional
    abstract val pronunciationDomain: Property<String>

    // ── HF-DEMO-QUALITY US-4 — chrome + typography ───────────────────────────

    @get:Input
    abstract val chromeEnabled: Property<Boolean>

    @get:Input
    abstract val chromeTimecode: Property<Boolean>

    @get:Input
    abstract val chromeCornerMarks: Property<Boolean>

    @get:Input
    abstract val chromeProgressBar: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val chromeLabel: Property<String>

    @get:Input
    abstract val displayFont: Property<String>

    @get:Input
    abstract val monoFont: Property<String>

    @get:Input
    abstract val entrancesEnabled: Property<Boolean>

    // ── HF-MUSIC-LIBRARY US-4 — music library + fallback ambience ────────────

    /**
     * `music-library.json` resolved by the document sound cues. `@InputFiles`
     * (not `@InputFile`): an unset library must not fail input validation.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val musicLibraryFiles: ConfigurableFileCollection

    /** Explicit track id of the fallback bed (blank = select by mood). */
    @get:Input
    abstract val musicTrack: Property<String>

    /** Mood of the fallback bed (only used when the document declares no music). */
    @get:Input
    abstract val musicMood: Property<String>

    /** Level of the fallback bed. */
    @get:Input
    abstract val musicVolume: Property<Double>

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
            val attrs =
                Attributes
                    .builder()
                    .attribute("hyperframes-width", width.get().toString())
                    .attribute("hyperframes-height", height.get().toString())
                    .attribute("hyperframes-fps", fps.get().toString())
                    .attribute("hyperframes-output", "${outputName.get()}.mp4")
                    .build()

            val options =
                Options
                    .builder()
                    .backend("html5")
                    .safe(SafeMode.UNSAFE)
                    .toDir(outputFile)
                    .attributes(attrs)
                    .build()

            // Step 1: AsciiDoc -> HTML conversion (AsciidoctorJ)
            asciidoctor.convertFile(sourceFile, options)

            val generatedHtml = outputFile.resolve("index.html")
            require(generatedHtml.exists()) {
                "Generated HTML not found: ${generatedHtml.absolutePath}"
            }

            // Step 2: HyperFrames post-processing (stage, data-*, GSAP)
            val rawHtml = generatedHtml.readText()
            val domainDictionary = pronunciationDomain.orNull?.let { PronunciationDomainLoader.load(it) }
            // HF-SPEAKER-NOTES US-2 — the shared N1 grammar extracts the authored
            // notes from the *source*; the processor removes them from the visible
            // composition and emits one narration marker per scene (US-3).
            val speakerNotes = SpeakerNotesExtractor.extract(sourceFile.readText())
            val processor =
                HyperframesHtmlProcessor(
                    width = width.get(),
                    height = height.get(),
                    fps = fps.get(),
                    outputName = outputName.get(),
                ).withChrome(
                    ChromeConfig(
                        enabled = chromeEnabled.get(),
                        timecode = chromeTimecode.get(),
                        cornerMarks = chromeCornerMarks.get(),
                        progressBar = chromeProgressBar.get(),
                        label = chromeLabel.orNull.orEmpty(),
                    ),
                ).withTypography(
                    TypographyConfig(
                        displayFont = displayFont.get(),
                        monoFont = monoFont.get(),
                    ),
                ).withEntrances(EntranceConfig(enabled = entrancesEnabled.get()))
                    .withDomainDictionary(domainDictionary)
                    .withMusicLibrary(musicLibrary())
                    .withDefaultMusic(
                        MusicDeclaration.fallback(musicTrack.getOrElse(""), musicMood.getOrElse(""), musicVolume.getOrElse(MusicDeclaration.DEFAULT_VOLUME)),
                    ).withSpeakerNotes(speakerNotes)
            val enhancedHtml = processor.enhance(rawHtml)
            generatedHtml.writeText(enhancedHtml)

            // HF-CR4 — write the vendored GSAP runtime next to the composition
            // so the generated page loads it as an external, offline sibling
            // (the CLI lints an *inlined* third-party runtime as
            // `non_deterministic_code`).
            outputFile.resolve(GsapLibrary.FILE_NAME).writeText(GsapLibrary.source)

            // US-3 — the composition references images/audio with relative paths;
            // copy them next to index.html so the CLI resolves real files.
            val copiedAssets = ReferencedAssets.copy(enhancedHtml, inputFile, outputFile)

            logger.lifecycle("HyperFrames HTML generated: ${generatedHtml.absolutePath}")
            if (copiedAssets.isNotEmpty()) {
                logger.lifecycle("   Copied ${copiedAssets.size} referenced asset(s) next to the composition")
            }
            logger.info("   Enhanced with HyperFrames data-* attributes (HF-3)")
        } finally {
            asciidoctor.shutdown()
        }
    }

    /** HF-MUSIC-LIBRARY US-4 — the configured library, or `null` when unset. */
    private fun musicLibrary(): MusicLibrary? =
        musicLibraryFiles.files
            .filter { it.isFile }
            .minByOrNull { it.path }
            ?.let(MusicLibraryManifestLoader::load)
}
