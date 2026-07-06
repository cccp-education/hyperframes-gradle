package hyperframes.template

/**
 * HF-5a — Template "title-card" : fondu d'entrée, logo, titre et sous-titre.
 *
 * Value object DDD pur — produit un fragment HTML HyperFrames valide,
 * composé d'un bloc `hyperframes-composition` + un track portant une
 * timeline GSAP fade-in. Aucun effet de bord.
 *
 * @property id identifiant de composition (rendu dans `data-composition-id`)
 * @property title texte principal affiché
 * @property subtitle texte secondaire affiché sous le titre
 * @property logoPath chemin optionnel d'un logo (rendu en `<img src=...>`)
 * @property durationSeconds durée du track en secondes (défaut : 2.0)
 */
data class TitleCardTemplate(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val logoPath: String? = null,
    val durationSeconds: Double = 2.0
) {

    /**
     * Rend le fragment HTML HyperFrames.
     *
     * Retourne un bloc `.hyperframes-composition` contenant un track unique
     * dont la durée est [durationSeconds] (arrondie à l'entier le plus proche
     * pour `data-duration`), un visuel optionnel, le titre, le sous-titre,
     * et une timeline GSAP fade-in injectée via `window.__timelines`.
     */
    fun render(): String = buildString {
        appendLine("<div class=\"sect1 hyperframes-composition\" data-composition-id=\"$id\">")
        appendLine("  <div class=\"sectionbody\">")
        appendLine("    <div class=\"paragraph hyperframes-track\" data-track-index=\"0\" data-start=\"0\" data-duration=\"${durationSeconds.toInt()}\">")
        appendLine("      <p>")
        if (logoPath != null) {
            appendLine("        <img src=\"$logoPath\" alt=\"logo\" class=\"hf-title-card-logo\" />")
        }
        appendLine("        <span class=\"hf-title-card-title\">$title</span>")
        if (subtitle != null) {
            appendLine("        <span class=\"hf-title-card-subtitle\">$subtitle</span>")
        }
        appendLine("      </p>")
        appendLine("    </div>")
        appendLine("    <script type=\"text/javascript\">")
        appendLine("      window.__timelines = window.__timelines || {};")
        appendLine("      (function () {")
        appendLine("        const tl = gsap.timeline({ paused: true });")
        if (logoPath != null) {
            appendLine("        tl.from(\"#$id .hf-title-card-logo\", { opacity: 0, y: 30, duration: 0.4 }, 0);")
        }
        appendLine("        tl.from(\"#$id .hf-title-card-title\", { opacity: 0, y: 40, duration: 0.6 }, 0.2);")
        if (subtitle != null) {
            appendLine("        tl.from(\"#$id .hf-title-card-subtitle\", { opacity: 0, y: 20, duration: 0.5 }, 0.5);")
        }
        appendLine("        window.__timelines[\"$id\"] = tl;")
        appendLine("      })();")
        appendLine("    </script>")
        appendLine("  </div>")
        append("</div>")
    }
}