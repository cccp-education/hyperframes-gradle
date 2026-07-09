package hyperframes

import hyperframes.pronunciation.PronunciationDictionary
import hyperframes.pronunciation.PronunciationHint
import hyperframes.template.CodeDiffTemplate
import hyperframes.template.TitleCardTemplate

/**
 * Transforms the HTML produced by AsciidoctorJ into enriched HTML for HyperFrames.
 *
 * Pipeline:
 * 1. Inject GSAP CDN into `<head>`
 * 2. Add `<div id="stage">` with data-* attributes (width, height, fps, output)
 * 3. HF-5a: Expand `hyperframes-title-card` blocks into animated title-card compositions
 * 4. HF-5b: Expand `hyperframes-code-diff` blocks into animated code-diff compositions
 * 5. HF-7c: Expand `hyperframes-pronunciation` blocks into a TTS hints JSON island
 * 6. Convert `hyperframes-composition` blocks -> `data-composition-id` (from child heading)
 * 7. Convert `hyperframes-track` blocks -> `data-track-index`, `data-start`, `data-duration`
 * 8. Convert `hyperframes-animation` (listingblock) -> `<script>` GSAP with `__timelines`
 */
class HyperframesHtmlProcessor(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val outputName: String
) {
    /**
     * Single entry point: transforms AsciiDoc HTML into HyperFrames HTML.
     */
    fun enhance(html: String): String {
        val inlineDictionary = extractInlineHints(html)
        return html
            .let { injectGaspCdn(it) }
            .let { expandTitleCardBlocks(it) }
            .let { expandCodeDiffBlocks(it) }
            .let { expandPronunciationBlocks(it) }
            .let { stripInlineHintsFromTracks(it) }
            .let { wrapBodyContentInStage(it) }
            .let { enhanceCompositionBlocks(it) }
            .let { enhanceTrackBlocks(it) }
            .let { enhanceAnimationBlocks(it) }
            .let { injectInlinePronunciationIsland(it, inlineDictionary) }
    }

    // ──────────────────────────────────────────────
    // 3d. Inline pronunciation hints in narration (HF-7e)
    // ──────────────────────────────────────────────

    /**
     * HF-7e — Extracts inline `hf:pron[word, phonetic]` hints from track paragraphs.
     *
     * The hints are collected into a [PronunciationDictionary] and later
     * injected as a JSON island by [injectInlinePronunciationIsland]. The
     * word stays visible in the rendered text; the phonetic is consumed by
     * the TTS engine, not displayed on screen.
     *
     * Only hints inside `hyperframes-track` paragraphs are extracted — hints
     * outside tracks are ignored (they are not narration).
     */
    private fun extractInlineHints(html: String): PronunciationDictionary {
        val dictionary = PronunciationDictionary()
        val trackBlockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-track[^"]*"[^>]*>.*?</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val inlineHintRegex = Regex("""hf:pron\[([^,\]]+?),\s*([^\]]+?)\]""")

        trackBlockRegex.findAll(html).forEach { trackMatch ->
            inlineHintRegex.findAll(trackMatch.value).forEach { hintMatch ->
                val word = hintMatch.groupValues[1]
                val phonetic = hintMatch.groupValues[2]
                dictionary.add(PronunciationHint.of(word, phonetic))
            }
        }
        return dictionary
    }

    /**
     * Strips the `hf:pron[word, phonetic]` syntax from track paragraphs, keeping
     * only the word visible in the rendered text.
     */
    private fun stripInlineHintsFromTracks(html: String): String {
        val trackBlockRegex = Regex(
            """(<div[^>]*class="[^"]*hyperframes-track[^"]*"[^>]*>)(.*?)(</div>)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val inlineHintRegex = Regex("""hf:pron\[([^,\]]+?),\s*([^\]]+?)\]""")

        return trackBlockRegex.replace(html) { match ->
            val open = match.groupValues[1]
            val content = match.groupValues[2]
            val close = match.groupValues[3]
            val stripped = inlineHintRegex.replace(content) { it.groupValues[1] }
            "$open$stripped$close"
        }
    }

    /**
     * Injects the inline-hints JSON island before `</body>` when the
     * dictionary is non-empty and no island already exists.
     */
    private fun injectInlinePronunciationIsland(html: String, dictionary: PronunciationDictionary): String {
        if (dictionary.size() == 0) return html
        if (html.contains("id=\"hf-pronunciation\"")) return html
        val island = dictionary.render()
        return when {
            "</body>" in html -> html.replace("</body>", "    $island\n</body>")
            else -> html + island
        }
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
     * HF-5a — Expands `[.hyperframes-title-card#id]` AsciiDoc blocks
     * into animated HyperFrames title-card compositions (fade-in, title, subtitle).
     *
     * AsciidoctorJ produces the following HTML:
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
     * The whole block is replaced by [TitleCardTemplate.render].
     * The subtitle is the text of the first paragraph in the sectionbody.
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
     * HF-5b — Expands `[.hyperframes-code-diff#id]` AsciiDoc blocks
     * into animated HyperFrames code-diff compositions (before/after, fade).
     *
     * AsciidoctorJ produces the following HTML:
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
     * The whole block is replaced by [CodeDiffTemplate.render].
     * The language is extracted from the `data-lang` of the first code block.
     * The first two `listingblock` blocks become before / after.
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

    // ──────────────────────────────────────────────
    // 3c. Pronunciation dictionary (HF-7c)
    // ──────────────────────────────────────────────

    /**
     * HF-7c — Expands `[hyperframes-pronunciation]` AsciiDoc blocks
     * into a JSON island consumed by the HyperFrames CLI to correct TTS phonetics.
     *
     * AsciidoctorJ produces the following HTML:
     * ```
     * [hyperframes-pronunciation]
     * ----
     * dos: do
     * kubernetes: koobernetayz
     * ----
     * ```
     * ```html
     * <div class="listingblock hyperframes-pronunciation">
     *   <div class="content">
     *     <pre>dos: do
     * kubernetes: koobernetayz</pre>
     *   </div>
     * </div>
     * ```
     *
     * The whole block is replaced by the JSON island rendered by
     * [PronunciationDictionary.render]. Each `word: phonetic` line becomes
     * a [PronunciationHint]. Malformed lines (no colon) are silently
     * skipped for robustness.
     */
    private fun expandPronunciationBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-pronunciation[^"]*"[^>]*>\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val lineRegex = Regex("""^\s*([^:]+?)\s*:\s*(.+?)\s*$""")

        return html.replace(blockRegex) { match ->
            val content = match.groupValues[1]
            val dictionary = PronunciationDictionary()
            content.lineSequence().forEach { line ->
                val parsed = lineRegex.find(line)
                if (parsed != null) {
                    val word = parsed.groupValues[1]
                    val phonetic = parsed.groupValues[2]
                    dictionary.add(PronunciationHint.of(word, phonetic))
                }
            }
            dictionary.render()
        }
    }

    /**
     * For `hyperframes-composition` blocks, adds `data-composition-id`
     * based on the `id` of the first child heading.
     *
     * AsciidoctorJ HTML:
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
     * For `hyperframes-track` blocks, adds the timing data-* attributes:
     * `data-track-index`, `data-start`, `data-duration`.
     * Defaults: index=0, start=0, duration=6.
     *
     * AsciidoctorJ HTML:
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

            // Extract existing attributes, with defaults
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
     * For `hyperframes-animation` (AsciiDoc listingblock) blocks,
     * transforms the `<pre>code</pre>` content into a `<script>` GSAP tag.
     *
     * AsciidoctorJ HTML:
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
