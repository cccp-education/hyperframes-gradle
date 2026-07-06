package hyperframes.contract

/**
 * Contrat N3 — contexte exporté par hyperframes-gradle pour runner-gradle.
 *
 * Source unique de vérité du format `metadata.json` échangé entre
 * le plugin N2 (Watts) et l'orchestrateur N3 (New Jersey).
 *
 * @property plugin identifiant du plugin émetteur (toujours "hyperframes-gradle")
 * @property version version du plugin émetteur
 * @property output descripteur de la vidéo MP4 produite
 * @property source descripteur du document AsciiDoc d'origine
 * @property renderedAt ISO-8601 timestamp de rendu
 * @property renderDurationMs durée du rendu en millisecondes
 */
data class HyperframesParseContext(
    val plugin: String = "hyperframes-gradle",
    val version: String,
    val output: HyperframesVideoOutput,
    val source: HyperframesAsciidocSource,
    val renderedAt: String,
    val renderDurationMs: Long
) {
    companion object {
        const val PLUGIN_ID = "hyperframes-gradle"
    }
}

/**
 * Descripteur de la vidéo MP4 produite par le rendu HyperFrames.
 *
 * @property video nom du fichier MP4 (relatif à l'outputDir)
 * @property path chemin absolu du fichier MP4
 * @property sizeBytes taille du fichier en octets
 * @property durationSeconds durée en secondes (0.0 si inconnue)
 * @property width largeur en pixels
 * @property height hauteur en pixels
 * @property fps images par seconde
 * @property codec codec vidéo (par défaut "h264")
 */
data class HyperframesVideoOutput(
    val video: String,
    val path: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val fps: Int,
    val codec: String = "h264"
)

/**
 * Descripteur du document AsciiDoc source.
 *
 * @property asciidoc nom du fichier AsciiDoc source
 * @property compositions liste des identifiants de compositions détectées
 * @property tracks nombre de tracks détectés
 */
data class HyperframesAsciidocSource(
    val asciidoc: String,
    val compositions: List<String>,
    val tracks: Int
)