package hyperframes

import java.io.File

/**
 * Copies the assets a composition references (`<img>` / `<audio>`) next to the
 * generated `index.html` (HF-DEMO-QUALITY US-3).
 *
 * AsciidoctorJ converts the AsciiDoc **into** `outputDir` but never copies the
 * images it references; the generated `<img src="images/…">` would therefore be
 * a dead link (and the HyperFrames CLI renders a blank panel). The same applies
 * to the audio cues the processor emits.
 *
 * Resolution rules:
 * * relative `src` only — `http(s):`, `data:` and absolute paths are ignored;
 * * the source is resolved against the **input** directory (the one containing
 *   `index.adoc`) and copied to the same relative path under `outputDir`;
 * * a source outside the input directory (path traversal) or a missing file is
 *   skipped — the build never fails because of an asset.
 */
object ReferencedAssets {

    private val SRC = Regex("""<(?:img|audio)\b[^>]*\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE)

    /** Distinct relative asset paths referenced by the composition. */
    fun extract(html: String): List<String> =
        SRC.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && isRelative(it) }
            .distinct()
            .toList()

    /** Copies every referenced asset, preserving its relative path. */
    fun copy(html: String, inputDir: File, outputDir: File): List<File> {
        val inputRoot = inputDir.canonicalFile
        val outputRoot = outputDir.canonicalFile
        return extract(html).mapNotNull { relative ->
            val source = File(inputRoot, relative).canonicalFile
            if (!source.startsWith(inputRoot) || !source.isFile) return@mapNotNull null
            val target = File(outputRoot, relative).canonicalFile
            if (!target.startsWith(outputRoot) || target == source) return@mapNotNull null
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target
        }
    }

    private fun isRelative(path: String): Boolean =
        !path.startsWith("http://") &&
            !path.startsWith("https://") &&
            !path.startsWith("data:") &&
            !path.startsWith("/") &&
            !path.startsWith("\\")
}
