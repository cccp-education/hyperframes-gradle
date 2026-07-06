package hyperframes.contract

/**
 * Extrait un [HyperframesParseContext] depuis les artefacts produits
 * par le pipeline HyperFrames (HTML enrichi + MP4 rendu).
 *
 * Domain Service (DDD) — pure function, pas d'effet de bord.
 * Lit les `data-*` attributes du `<div id="stage">` pour les dimensions,
 * les `data-composition-id` pour les compositions,
 * les `data-track-index` pour les tracks.
 *
 * @param pluginVersion version du plugin émetteur (injectée par la task Gradle)
 */
class HyperframesContextExtractor(private val pluginVersion: String) {

    /**
     * Extrait le contexte N3 depuis le HTML HyperFrames et le MP4 rendu.
     *
     * @param html le HTML HyperFrames enrichi (post-processor)
     @ @param mp4 le fichier MP4 rendu (doit exister sur disque)
     * @param asciidocName nom du fichier AsciiDoc source
     * @param renderedAt ISO-8601 timestamp de rendu
     * @param renderDurationMs durée du rendu en ms
     */
    fun extract(
        html: String,
        mp4: java.io.File,
        asciidocName: String,
        renderedAt: String,
        renderDurationMs: Long
    ): HyperframesParseContext {
        val stage = parseStage(html)
        val compositions = parseCompositions(html)
        val tracks = parseTracks(html)

        return HyperframesParseContext(
            version = pluginVersion,
            output = HyperframesVideoOutput(
                video = mp4.name,
                path = mp4.absolutePath,
                sizeBytes = if (mp4.exists()) mp4.length() else 0L,
                durationSeconds = 0.0,
                width = stage.width,
                height = stage.height,
                fps = stage.fps
            ),
            source = HyperframesAsciidocSource(
                asciidoc = asciidocName,
                compositions = compositions,
                tracks = tracks
            ),
            renderedAt = renderedAt,
            renderDurationMs = renderDurationMs
        )
    }

    private data class StageAttributes(val width: Int, val height: Int, val fps: Int)

    private fun parseStage(html: String): StageAttributes {
        val stageRegex = Regex("""<div[^>]*id="stage"[^>]*>""")
        val stageMatch = stageRegex.find(html) ?: return StageAttributes(1920, 1080, 30)
        val openTag = stageMatch.value
        val width = Regex("""data-width="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 1920
        val height = Regex("""data-height="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
        val fps = Regex("""data-fps="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        return StageAttributes(width, height, fps)
    }

    private fun parseCompositions(html: String): List<String> {
        val regex = Regex("""data-composition-id="([^"]*)"""")
        return regex.findAll(html).map { it.groupValues[1] }.distinct().toList()
    }

    private fun parseTracks(html: String): Int {
        val regex = Regex("""data-track-index="(\d+)"""")
        return regex.findAll(html).map { it.groupValues[1].toInt() }.distinct().count()
    }
}