package hyperframes

import hyperframes.sound.MusicAssetPlanner
import hyperframes.sound.MusicLibraryManifestLoader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest

/**
 * HF-MUSIC-LIBRARY US-4 — places the resolved library tracks next to the
 * composition.
 *
 * `generateHyperframesHtml` emits `<audio class="hyperframes-music"
 * data-track-id="…" src="assets/audio/…">`; this task reads the **manifest** and
 * the **composition** and copies each referenced track to the path the runtime
 * loads (`assets/audio/<track-id>.<ext>`).
 *
 * Contract, not coupling: no code dependency on the producer (`newpipe`, GPL
 * boundary) — only a versioned JSON manifest and the audio files.
 *
 * Economy of ink: a track whose `sha256` matches the already-present asset is
 * **not** rewritten. A missing manifest/audio degrades to a warning (the
 * composition still renders); [strict] promotes it to a build failure.
 *
 * Not cacheable: it reads the gitignored audio store, whose content is outside
 * Gradle's input snapshot.
 */
@DisableCachingByDefault(because = "copies audio from the gitignored library store; the copy is local, not a build output to share")
abstract class ResolveHyperframesBgmTask : DefaultTask() {
    /** `music-library.json` (unset = no library configured). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val musicLibraryFiles: ConfigurableFileCollection

    /** Directory holding the library audio (default: the manifest directory). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val audioDir: DirectoryProperty

    /** The composition HTML emitted by `generateHyperframesHtml`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compositionHtml: ConfigurableFileCollection

    /** Promote a missing asset to a build failure. */
    @get:Input
    abstract val strict: Property<Boolean>

    /** Directory receiving the resolved assets (`<outputDir>/assets/audio`). */
    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun resolve() {
        val manifest = musicLibraryFiles.files.filter { it.isFile }.minByOrNull { it.path }
        if (manifest == null) {
            logger.lifecycle("No music library configured — music cues must declare an explicit file")
            return
        }
        val html = compositionHtml.files.firstOrNull { it.isFile }
        if (html == null) {
            degrade("composition HTML not found — run generateHyperframesHtml first")
            return
        }
        val library = MusicLibraryManifestLoader.load(manifest)
        val requests = MusicAssetPlanner.requests(html.readText())
        if (requests.isEmpty()) {
            logger.info("No library-resolved music asset referenced by the composition")
            return
        }

        val root = audioDir.orNull?.asFile ?: manifest.parentFile
        val assets = assetsDir.asFile.get()
        val plan =
            MusicAssetPlanner.plan(
                requests = requests,
                library = library,
                audioRoot = root.path,
                existsSha256 = { src -> targetFor(assets, src)?.takeIf { it.isFile }?.let(::sha256Of) },
            )

        plan.missing.forEach { reason -> logger.warn("Music asset missing: $reason") }
        plan.upToDate.forEach { asset ->
            logger.info("Music asset up to date: ${asset.src} (sha256 match, no copy)")
        }

        var missing = plan.missing.size
        var copied = 0
        plan.copies.forEach { asset ->
            val target = targetFor(assets, asset.src)
            if (target == null) {
                logger.warn("Music asset '${asset.src}' escapes the composition directory — skipped")
                missing++
                return@forEach
            }
            val source = File(asset.source)
            if (!source.isFile) {
                logger.warn("Music asset source not found: ${asset.source} (track '${asset.trackId}')")
                missing++
                return@forEach
            }
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            copied++
            logger.lifecycle("Music asset copied: ${asset.trackId} → ${asset.src}")
        }

        if (missing > 0) {
            degrade("$missing music asset(s) could not be placed next to the composition")
        } else if (copied == 0 && plan.upToDate.isNotEmpty()) {
            logger.lifecycle("Music assets already in place (${plan.upToDate.size} skipped — economy of ink)")
        }
    }

    private fun degrade(message: String) {
        if (strict.getOrElse(false)) throw GradleException(message)
        logger.warn(message)
    }

    /** The target file for a composition-relative [src], or `null` if it escapes. */
    private fun targetFor(
        assetsDir: File,
        src: String,
    ): File? {
        val root = assetsDir.parentFile?.parentFile?.canonicalFile ?: return null
        val target = File(root, src).canonicalFile
        if (!target.startsWith(root) || target == root) return null
        return target
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
