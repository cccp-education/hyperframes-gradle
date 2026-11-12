package hyperframes

import hyperframes.sound.MusicDeclaration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * HF-MUSIC-LIBRARY US-4 — `hyperframes { music { … } }`.
 *
 * The **document** remains the source of truth: `[.hyperframes-music]` blocks
 * declared scene by scene drive the sound sequence. This block only says where
 * the library lives (and which ambience to use when the document declares none).
 *
 * ```
 * hyperframes {
 *     music {
 *         libraryFile.set(layout.projectDirectory.file("music-library.json"))
 *         audioDir.set(layout.projectDirectory.dir("audio"))  // default: manifest dir
 *         mood.set("calm")                                    // fallback bed
 *         volume.set(0.25)                                    // fallback level
 *         strict.set(true)                                    // promote missing assets
 *     }
 * }
 * ```
 */
abstract class MusicExtension {
    /**
     * `music-library.json` produced by the `newpipe` borough (versioned
     * manifest; the audio itself stays gitignored). Unset = no library, so a cue
     * must declare an explicit `file:`.
     */
    abstract val libraryFile: RegularFileProperty

    /**
     * Directory holding the library audio (`MusicTrack.file` paths are relative
     * to it). Defaults to the manifest directory.
     */
    abstract val audioDir: DirectoryProperty

    /** Explicit track id used as the fallback bed (blank = select by [mood]). */
    abstract val track: Property<String>

    /** Aesthetic intent used as the fallback bed when the document is silent. */
    abstract val mood: Property<String>

    /** Level of the fallback bed, in `0..1`. */
    abstract val volume: Property<Double>

    /**
     * Promote a missing/unresolved music asset to a build failure. Default
     * `false` (degraded: the composition is still produced, with a warning).
     */
    abstract val strict: Property<Boolean>

    init {
        track.convention("")
        mood.convention("")
        volume.convention(MusicDeclaration.DEFAULT_VOLUME)
        strict.convention(false)
    }
}
