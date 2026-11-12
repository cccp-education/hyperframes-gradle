package hyperframes.sound

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import video.kernel.music.MusicLibrary
import video.kernel.music.MusicLicence
import video.kernel.music.MusicTrack
import java.io.File

/**
 * Reads the `music-library.json` manifest produced by the `newpipe` borough
 * (HF-MUSIC-LIBRARY US-3/US-4).
 *
 * The manifest is the **only** exchange surface between the producer and this
 * plugin: no code dependency, no network, no YouTube. Only metadata is versioned
 * — the audio stays in the gitignored library directory.
 */
object MusicLibraryManifestLoader {
    private val mapper = jacksonObjectMapper()

    /**
     * Loads [file]; a missing manifest yields an **empty** library (the author
     * either declares an explicit `file:` or gets an actionable unresolved-cue
     * error naming `music.libraryFile`).
     */
    fun load(file: File): MusicLibrary {
        if (!file.isFile) return MusicLibrary()
        val dto =
            runCatching { mapper.readValue<ManifestDto>(file.readText()) }
                .getOrElse { error ->
                    throw IllegalArgumentException("invalid music library manifest '${file.path}': ${error.message}")
                }
        return toLibrary(dto)
    }

    /** Parses a manifest text ([load] without the file system). */
    fun parse(text: String): MusicLibrary = toLibrary(mapper.readValue<ManifestDto>(text))

    private fun toLibrary(dto: ManifestDto): MusicLibrary =
        MusicLibrary(
            schemaVersion = dto.schemaVersion.coerceAtLeast(1),
            tracks = dto.tracks.mapNotNull(::toTrack),
        )

    /** Drops unusable entries (the N1 model requires a non-blank id and title). */
    private fun toTrack(dto: TrackDto): MusicTrack? {
        if (dto.id.isBlank() || dto.title.isBlank()) return null
        return runCatching {
            MusicTrack(
                id = dto.id,
                title = dto.title,
                artist = dto.artist,
                file = dto.file,
                sha256 = dto.sha256,
                durationSeconds = dto.durationSeconds,
                licence = MusicLicence.parse(dto.licence),
                sourceUrl = dto.sourceUrl,
                attribution = dto.attribution,
                tags = dto.tags,
                mood = dto.mood,
                energy = dto.energy,
            )
        }.getOrNull()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ManifestDto(
        val schemaVersion: Int = 1,
        val tracks: List<TrackDto> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TrackDto(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val file: String = "",
        val sha256: String = "",
        val durationSeconds: Double = 0.0,
        val licence: String = "",
        val sourceUrl: String = "",
        val attribution: String = "",
        val tags: List<String> = emptyList(),
        val mood: String = "",
        val energy: Int = 0,
    )
}
