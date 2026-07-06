package hyperframes

import hyperframes.template.CodeDiffTemplate
import hyperframes.template.TitleCardTemplate

/**
 * Transforme le HTML généré par AsciidoctorJ en HTML enrichi pour HyperFrames.
 *
 * Pipeline :
 * 1. Injecte GSAP CDN dans `<head>`
 * 2. Ajoute `<div id="stage">` avec data-* attributes (width, height, fps, output)
 * 3. HF-5a : Étend les blocs `hyperframes-title-card` en compositions title-card animées
 * 4. HF-5b : Étend les blocs `hyperframes-code-diff` en compositions code-diff animées
 * 5. Convertit les blocs `hyperframes-composition` → `data-composition-id` (extrait du heading enfant)
 * 6. Convertit les blocs `hyperframes-track` → `data-track-index`, `data-start`, `data-duration`
 * 7. Convertit les blocs `hyperframes-animation` (listingblock) → `<script>` GSAP avec `__timelines`
 */
class HyperframesHtmlProcessor(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val outputName: String
) {
    /**
     * Point d'entrée unique : transforme le HTML AsciiDoc en HTML HyperFrames.
     */
    fun enhance(html: String): String {
        return html
            .let { injectGaspCdn(it) }
            .let { expandTitleCardBlocks(it) }
            .let { expandCodeDiffBlocks(it) }
            .let { wrapBodyContentInStage(it) }
            .let { enhanceCompositionBlocks(it) }
            .let { enhanceTrackBlocks(it) }
            .let { enhanceAnimationBlocks(it) }
    }

    // ──────────────────────────────────────────────
    // 1. GSAP CDN
    // ──────────────────────────────────────────────

    private fun injectGaspCdn(html: String): String {
        val gsapScript =
            """<script src="https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.5/gsap.min.js"></script>"""

        return when {
            "</head>" in html -> html.replace("</head>", "    $gsapScript\n</head>")
            else -> html
        }
    }

    // ──────────────────────────────────────────────
    // 2. Stage wrapper
    // ──────────────────────────────────────────────

    /**
     * Remplace le contenu du `<body>` par un stage div avec data-* attributes.
     */
    private fun wrapBodyContentInStage(html: String): String {
        val bodyOpenRegex = Regex("<body[^>]*>")
        val bodyMatch = bodyOpenRegex.find(html) ?: return html

        val bodyContentStart = bodyMatch.range.last + 1
        val bodyCloseIdx = html.indexOf("</body>")
        if (bodyCloseIdx == -1) return html

        val beforeBody = html.substring(0, bodyContentStart)
        val bodyContent = html.substring(bodyContentStart, bodyCloseIdx)
        val afterBody = html.substring(bodyCloseIdx)

        val stageDiv = buildString {
            appendLine("<div id=\"stage\"")
            appendLine("     data-width=\"$width\"")
            appendLine("     data-height=\"$height\"")
            appendLine("     data-fps=\"$fps\"")
            appendLine("     data-output=\"${outputName}.mp4\">")
            append(bodyContent.trimStart())
            appendLine()
            append("</div>")
        }

        return "$beforeBody\n$stageDiv\n$afterBody"
    }

    // ──────────────────────────────────────────────
    // 3. Title-card template (HF-5a)
    // ──────────────────────────────────────────────

    /**
     * HF-5a — Étend les blocs `[.hyperframes-title-card#id]` AsciiDoc
     * en compositions HyperFrames title-card animées (fade-in, titre, sous-titre).
     *
     * HTML AsciidoctorJ produit par :
     * ```
     * [.hyperframes-title-card#intro, duration=3]
     * == My Formation
     *
     * Module 01
     * ```
     * ```html
     * <div class="sect1 hyperframes-title-card">
     *   <h2 id="intro">My Formation</h2>
     *   <div class="sectionbody">
     *     <div class="paragraph"><p>Module 01</p></div>
     *   </div>
     * </div>
     * ```
     *
     * Le bloc entier est remplacé par [TitleCardTemplate.render].
     * Le sous-titre est le texte du premier paragraphe de la sectionbody.
     */
    private fun expandTitleCardBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-title-card[^"]*"[^>]*>\s*<h2[^>]*id="([^"]*)"[^>]*>([^<]*)</h2>\s*(<div class="sectionbody">.*?</div>)?\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val paragraphRegex = Regex("""<div class="paragraph">\s*<p>(.*?)</p>\s*</div>""", RegexOption.DOT_MATCHES_ALL)

        return html.replace(blockRegex) { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2].trim()
            val sectionBody = match.groupValues[3]
            val subtitle = paragraphRegex.find(sectionBody)?.groupValues?.get(1)?.trim()

            TitleCardTemplate(
                id = id,
                title = title,
                subtitle = subtitle
            ).render()
        }
    }

    // ──────────────────────────────────────────────
    // 3b. Code-diff template (HF-5b)
    // ──────────────────────────────────────────────

    /**
     * HF-5b — Étend les blocs `[.hyperframes-code-diff#id]` AsciiDoc
     * en compositions HyperFrames code-diff animées (before/after, fade).
     *
     * HTML AsciidoctorJ produit par :
     * ```
     * [.hyperframes-code-diff#refactor-demo, lang="kotlin"]
     * == Refactoring demo
     *
     * [source,kotlin]
     * ----
     * val x = oldFunc()
     * ----
     *
     * [source,kotlin]
     * ----
     * val x = newFunc()
     * ----
     * ```
     * ```html
     * <div class="sect1 hyperframes-code-diff">
     *   <h2 id="refactor-demo">Refactoring demo</h2>
     *   <div class="sectionbody">
     *     <div class="listingblock"><div class="content">
     *       <pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = oldFunc()</code></pre>
     *     </div></div>
     *     <div class="listingblock"><div class="content">
     *       <pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = newFunc()</code></pre>
     *     </div></div>
     *   </div>
     * </div>
     * ```
     *
     * Le bloc entier est remplacé par [CodeDiffTemplate.render].
     * Le langage est extrait du `data-lang` du premier bloc de code.
     * Les deux premiers blocs `listingblock` deviennent before / after.
     */
    private fun expandCodeDiffBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-code-diff[^"]*"[^>]*>\s*<h2[^>]*id="([^"]*)"[^>]*>[^<]*</h2>\s*<div class="sectionbody">\s*(<div class="listingblock">.*?</div>\s*</div>\s*<div class="listingblock">.*?</div>\s*</div>)\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val listingRegex = Regex(
            """<div class="listingblock">\s*<div class="content">\s*<pre[^>]*><code[^>]*data-lang="([^"]*)"[^>]*>(.*?)</code></pre>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )

        return html.replace(blockRegex) { match ->
            val id = match.groupValues[1]
            val listingsHtml = match.groupValues[2]
            val listings = listingRegex.findAll(listingsHtml).toList()
            if (listings.size < 2) return@replace match.value

            val lang = listings[0].groupValues[1]
            val beforeCode = listings[0].groupValues[2].trim()
            val afterCode = listings[1].groupValues[2].trim()

            CodeDiffTemplate(
                id = id,
                beforeCode = beforeCode,
                afterCode = afterCode,
                lang = lang
            ).render()
        }
    }

    /**
     * Pour les blocs `hyperframes-composition`, ajoute `data-composition-id`
     * basé sur l'`id` du premier heading enfant.
     *
     * HTML AsciidoctorJ :
     * ```html
     * <div class="sect1 hyperframes-composition">
     *   <h2 id="intro">Introduction</h2>
     * ```
     */
    private fun enhanceCompositionBlocks(html: String): String {
        val regex = Regex("""<div([^>]*class="[^"]*hyperframes-composition[^"]*"[^>]*)>""")
        val idRegex = Regex("""id="([^"]*)"""")

        val sb = StringBuilder()
        var cursor = 0

        for (match in regex.findAll(html)) {
            // Texte avant ce match
            sb.append(html.substring(cursor, match.range.first))

            val openTag = match.groupValues[1]
            val openTagEnd = match.range.last + 1

            // Chercher l'id du heading dans le contenu après la balise ouvrante
            val afterOpen = html.substring(openTagEnd)
            val headingMatch = idRegex.find(afterOpen)

            if (headingMatch != null) {
                val id = headingMatch.groupValues[1]
                sb.append("<div$openTag data-composition-id=\"$id\">")
            } else {
                sb.append(match.value)
            }

            cursor = openTagEnd
        }

        sb.append(html.substring(cursor))
        return sb.toString()
    }

    // ──────────────────────────────────────────────
    // 4. Tracks
    // ──────────────────────────────────────────────

    /**
     * Pour les blocs `hyperframes-track`, ajoute les data-* attributes
     * de timing : `data-track-index`, `data-start`, `data-duration`.
     * Valeurs par défaut : index=0, start=0, duration=6.
     *
     * HTML AsciidoctorJ :
     * ```html
     * <div class="paragraph hyperframes-track">
     *   <p>...</p>
     * </div>
     * ```
     */
    private fun enhanceTrackBlocks(html: String): String {
        val regex = Regex("""<div([^>]*class="[^"]*hyperframes-track[^"]*"[^>]*)>""")
        val attrRegex = Regex("""(data-track-index|data-start|data-duration)="([^"]*)"""")

        var trackIndex = 0
        val sb = StringBuilder()
        var cursor = 0

        for (match in regex.findAll(html)) {
            sb.append(html.substring(cursor, match.range.first))

            val attrs = match.groupValues[1]

            // Extraire les attributs déjà présents, avec défauts
            val existing = attrRegex.findAll(attrs).associate { it.groupValues[1] to it.groupValues[2] }
            val index = existing["data-track-index"] ?: trackIndex.toString()
            val start = existing["data-start"] ?: "0"
            val duration = existing["data-duration"] ?: "6"

            sb.append("<div$attrs data-track-index=\"$index\" data-start=\"$start\" data-duration=\"$duration\">")
            trackIndex++
            cursor = match.range.last + 1
        }

        sb.append(html.substring(cursor))
        return sb.toString()
    }

    // ──────────────────────────────────────────────
    // 5. Animations
    // ──────────────────────────────────────────────

    /**
     * Pour les blocs `hyperframes-animation` (listingblock AsciiDoc),
     * transforme le contenu `<pre>code</pre>` en balise `<script>` GSAP.
     *
     * HTML AsciidoctorJ :
     * ```html
     * <div class="listingblock hyperframes-animation">
     *   <div class="content">
     *     <pre>gsap code</pre>
     *   </div>
     * </div>
     * ```
     */
    private fun enhanceAnimationBlocks(html: String): String {
        val regex = Regex(
            """<div[^>]*class="[^"]*hyperframes-animation[^"]*"[^>]*>\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        return html.replace(regex) { match ->
            val code = match.groupValues[1].trim()
            buildString {
                appendLine("<script type=\"text/javascript\">")
                appendLine("window.__timelines = window.__timelines || {};")
                appendLine(code)
                append("</script>")
            }
        }
    }
}
