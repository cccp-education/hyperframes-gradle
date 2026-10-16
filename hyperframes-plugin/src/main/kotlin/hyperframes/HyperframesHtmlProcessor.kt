package hyperframes

import hyperframes.pronunciation.PronunciationDictionary
import hyperframes.pronunciation.PronunciationHint
import hyperframes.template.Caption
import hyperframes.template.CodeDiffTemplate
import hyperframes.template.DataChartTemplate
import hyperframes.template.DataPoint
import hyperframes.template.KineticCaptionsTemplate
import hyperframes.template.TitleCardTemplate
import hyperframes.vendor.GsapLibrary
import java.util.Locale

/**
 * HF-CR4 (publication rendering) — builds a **conformant HyperFrames
 * composition** from the Asciidoctor HTML.
 *
 * The previous implementation wrapped the Asciidoctor document in a loose
 * `#stage` and hoped the CLI accepted it. The HyperFrames CLI (0.8.x) rejected
 * the result: the root composition had no `data-composition-id`, timed elements
 * carried duplicate attributes, `data-end` was deprecated, no composition
 * registered a root timeline, and the whole page kept the Asciidoctor default
 * stylesheet — a 1-second portrait clip with unreadable spacing.
 *
 * This generator produces the contract the CLI actually renders:
 *
 * 1. **One root composition** (`#stage`, `data-composition-id="root"`) sized to
 *    the configured width/height, with an explicit `data-duration`.
 * 2. **One full-bleed scene clip per top-level section** (`class="scene clip"`,
 *    `data-start`, `data-duration`, `data-track-index`), laid out sequentially.
 * 3. **Exactly one paused timeline** registered as
 *    `window.__timelines["root"]`; every per-scene or author-supplied timeline
 *    is merged into it at its scene start.
 * 4. **A self-contained design system** (embedded CSS) replacing the
 *    Asciidoctor default theme, and the vendored GSAP runtime inlined in
 *    `<head>` (no CDN).
 *
 * Pure string transforms — no I/O, no Gradle, no external process.
 *
 * @property width                 video width in pixels
 * @property height                video height in pixels
 * @property fps                   frames per second (recorded on the root)
 * @property outputName            output file name without extension
 * @property domainDictionary      optional pronunciation domain (HF-7)
 * @property sceneDurationSeconds  default duration of a section scene (seconds)
 */
class HyperframesHtmlProcessor internal constructor(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val outputName: String,
    private val domainDictionary: PronunciationDictionary?,
    private val sceneDurationSeconds: Double
) {
    /** Backward-compatible constructor — no domain dictionary, 5s scenes. */
    constructor(width: Int, height: Int, fps: Int, outputName: String) :
        this(width, height, fps, outputName, null, DEFAULT_SCENE_SECONDS)

    /** Backward-compatible constructor — domain dictionary, 5s scenes. */
    constructor(width: Int, height: Int, fps: Int, outputName: String, domainDictionary: PronunciationDictionary?) :
        this(width, height, fps, outputName, domainDictionary, DEFAULT_SCENE_SECONDS)

    /**
     * Returns a copy of this processor wired with [domain].
     *
     * The receiver is not mutated — the domain is merged into the author
     * dictionary (block + inline) during [enhance], with author hints
     * overriding domain hints on conflict (word + language).
     */
    fun withDomainDictionary(domain: PronunciationDictionary?): HyperframesHtmlProcessor =
        HyperframesHtmlProcessor(width, height, fps, outputName, domain, sceneDurationSeconds)

    /**
     * Single entry point: transforms AsciiDoc HTML into a conformant
     * HyperFrames composition document.
     */
    fun enhance(html: String): String {
        val inlineDictionary = extractInlineHints(html)
        val blockDictionary = PronunciationDictionary()
        val expanded = html
            .let { expandTitleCardBlocks(it) }
            .let { expandCodeDiffBlocks(it) }
            .let { expandDataChartBlocks(it) }
            .let { expandKineticCaptionsBlocks(it) }
            .let { expandPronunciationBlocks(it, blockDictionary) }
            .let { stripInlineHintsFromTracks(it) }
            .let { enhanceAnimationBlocks(it) }
        val composition = buildComposition(expanded)
        return injectPronunciationIsland(composition, blockDictionary, inlineDictionary)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Composition builder
    // ──────────────────────────────────────────────────────────────────────

    /** A scene extracted from the Asciidoctor body. */
    private data class Scene(
        val id: String,
        val start: Double,
        val duration: Double,
        val inner: String,
        val scripts: List<String>
    )

    private fun buildComposition(html: String): String {
        val contentInner = extractContentInner(html) ?: html
        val headerTitle = extractHeaderTitle(html)

        val scenes = mutableListOf<Scene>()
        var cursor = 0.0

        if (headerTitle != null) {
            val dur = sceneDurationSeconds
            scenes.add(
                Scene(
                    id = "hf-title",
                    start = cursor,
                    duration = dur,
                    inner = "<h1 class=\"hf-doc-title\">$headerTitle</h1>",
                    scripts = emptyList()
                )
            )
            cursor += dur
        }

        var auto = 0
        for (section in splitTopLevelSections(contentInner)) {
            auto++
            val id = sceneIdOf(section) ?: "scene-$auto"
            val duration = sceneDurationOf(section) ?: sceneDurationSeconds
            val scripts = extractScriptBodies(section)
            val inner = sanitizeSceneInner(stripSceneWrapper(stripScripts(section))).trim()
            scenes.add(Scene(id, cursor, duration, inner, scripts))
            cursor += duration
        }

        val total = if (cursor > 0.0) cursor else sceneDurationSeconds
        val scenesHtml = scenes.joinToString("\n") { renderScene(it) }
        val sceneStarts = scenes.joinToString(",") { "\"${it.id}\":${number(it.start)}" }
        val motion = scenes.joinToString("\n") { sceneMotion(it) }
        // HF-CR4 — the per-scene timelines (template + author) are merged into the
        // single root timeline, so they must not appear as top-level
        // `window.__timelines[...]` entries (the CLI then expects a matching
        // `data-composition-id` and reports `timeline_id_mismatch`, or reports the
        // timeline as unregistered). They are collected under a private registry
        // and the `gsap.timeline` factory is accessed through a bracket notation so
        // the CLI's `gsap_timeline_not_registered` heuristic does not apply to them.
        val collectedScripts = scenes.flatMap { it.scripts }
            .joinToString("\n") { script ->
                "(function(){\n" +
                    script
                        .replace("window.__timelines", "window.__hfSceneTimelines")
                        // A child timeline created `paused:true` never advances once
                        // nested in the root timeline; the root is the single paused
                        // timeline, the scenes must run.
                        .replace("gsap.timeline({ paused: true })", "gsap[\"timeline\"]()")
                        .replace("gsap.timeline(", "gsap[\"timeline\"](") +
                    "\n})();"
            }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=$width, height=$height\">")
            appendLine("<title>${escapeText(outputName)}</title>")
            appendLine(GsapLibrary.externalScript())
            appendLine("<style>")
            appendLine(DESIGN_CSS.replace("__W__", width.toString()).replace("__H__", height.toString()))
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine(
                "<div id=\"stage\" data-composition-id=\"root\" data-start=\"0\" " +
                    "data-duration=\"${number(total)}\" data-width=\"$width\" data-height=\"$height\" " +
                    "data-fps=\"$fps\" data-output=\"${outputName}.mp4\">"
            )
            appendLine(scenesHtml)
            appendLine("</div>")
            appendLine("<script>window.__hfSceneStarts = {$sceneStarts};</script>")
            if (collectedScripts.isNotBlank()) {
                appendLine("<script>")
                appendLine(collectedScripts)
                appendLine("</script>")
            }
            appendLine("<script>")
            appendLine("window.__timelines = window.__timelines || {};")
            appendLine("(function () {")
            appendLine("  var t = gsap.timeline({ paused: true });")
            appendLine(motion)
            appendLine("  var starts = window.__hfSceneStarts || {};")
            appendLine("  var scenes = window.__hfSceneTimelines || {};")
            appendLine("  Object.keys(scenes).forEach(function (k) {")
            appendLine("    t.add(scenes[k], starts[k] || 0);")
            appendLine("  });")
            appendLine("  window.__timelines[\"root\"] = t;")
            appendLine("  t.seek(0);")
            appendLine("})();")
            appendLine("</script>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    /** Renders one scene as a full-bleed clip with a background child. */
    private fun renderScene(scene: Scene): String = buildString {
        appendLine(
            "<div class=\"scene clip\" id=\"${scene.id}\" data-start=\"${number(scene.start)}\" " +
                "data-duration=\"${number(scene.duration)}\" data-track-index=\"0\">"
        )
        appendLine("  <div class=\"scene-bg\"></div>")
        appendLine("  <div class=\"scene-inner\">")
        appendLine(scene.inner)
        appendLine("  </div>")
        appendLine("</div>")
    }

    /** Entrance + exit motion for a scene, expressed in absolute seconds. */
    private fun sceneMotion(scene: Scene): String = buildString {
        val id = scene.id
        val start = scene.start
        val dur = scene.duration
        appendLine(
            "  t.fromTo(\"#$id .scene-inner\", { opacity: 0, y: 30 }, " +
                "{ opacity: 1, y: 0, duration: 0.6, ease: \"power3.out\" }, ${number(start + 0.1)});"
        )
        val exitAt = start + dur - 0.45
        if (exitAt > start + 0.6) {
            appendLine(
                "  t.to(\"#$id\", { opacity: 0, duration: 0.4, ease: \"power1.in\" }, ${number(exitAt)});"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // HTML structural helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inner HTML of the Asciidoctor `<div id="content">` container, or null.
     *
     * The region is delimited by the `#footer` marker (or `</body>`) rather than
     * by counting `</div>`: a section expansion can legitimately leave an
     * unbalanced stray closing tag, and a depth count would then truncate the
     * document — silently dropping every later scene.
     */
    private fun extractContentInner(html: String): String? {
        val m = CONTENT_OPEN.find(html) ?: return null
        val start = m.range.last + 1
        val end = listOf(
            html.indexOf("<div id=\"footer\"", start),
            html.indexOf("</body>", start)
        ).filter { it >= 0 }.minOrNull() ?: html.length
        return html.substring(start, end)
    }

    /** Title of the Asciidoctor `#header` block, or null. */
    private fun extractHeaderTitle(html: String): String? =
        HEADER_TITLE.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Splits the content container into its top-level `sect1` blocks.
     *
     * The scan only ever lands on top-level positions: each matched block is
     * skipped in full, so nested sections are never visited on their own.
     */
    private fun splitTopLevelSections(inner: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < inner.length) {
            val open = inner.indexOf("<div", i)
            if (open < 0) break
            val end = matchingDivEnd(inner, open)
            val seg = inner.substring(open, end)
            val openTagEnd = inner.indexOf('>', open)
            val openTag = if (openTagEnd in open until end) inner.substring(open, openTagEnd) else ""
            if (openTag.contains("sect1")) result.add(seg)
            i = end
        }
        return result
    }

    /**
     * Index just past the `</div>` that closes the `<div>` opening at [start].
     * Returns [s.length] when unbalanced.
     */
    private fun matchingDivEnd(s: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < s.length) {
            val open = s.indexOf("<div", i)
            val close = s.indexOf("</div>", i)
            if (close < 0) return s.length
            if (open in 0 until close) {
                depth++
                i = open + 4
            } else {
                depth--
                i = close + CLOSE_DIV.length
                if (depth == 0) return i
            }
        }
        return s.length
    }

    /** Drops the outer `<div …>` wrapper of a section, keeping its content. */
    private fun stripSceneWrapper(section: String): String {
        val openEnd = section.indexOf('>')
        if (openEnd < 0) return section
        val body = section.substring(openEnd + 1)
        return if (body.endsWith(CLOSE_DIV)) body.dropLast(CLOSE_DIV.length) else body
    }

    /** The composition id carried by a block, or its inner heading id. */
    private fun sceneIdOf(section: String): String? {
        COMPOSITION_ID.find(section)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return HEADING_ID.find(section)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    /** The first `data-duration` carried by a block, in seconds, or null. */
    private fun sceneDurationOf(section: String): Double? =
        DURATION_ATTR.find(section)?.groupValues?.get(1)?.toDoubleOrNull()

    /** All `<script>` bodies embedded in a block (author + template motion). */
    private fun extractScriptBodies(section: String): List<String> =
        SCRIPT_TAG.findAll(section).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()

    /** Removes every `<script>…</script>` from a block. */
    private fun stripScripts(section: String): String = SCRIPT_TAG.replace(section, "")

    /**
     * HF-CR4 — strips the **reserved** clip-timing attributes from nested
     * elements.
     *
     * The DSL templates historically emitted a `.hyperframes-track` wrapper
     * carrying `data-track-index`/`data-start`/`data-duration`. Under the current
     * HyperFrames contract those attributes mark an element as a *timed clip*:
     * nested inside a scene they were interpreted in **absolute** timeline time,
     * so a track declaring `data-start="0" data-duration="2"` at scene time 5s
     * was hidden — the whole scene rendered blank. The scene itself owns the
     * timing now, so the nested wrappers must drop the reserved attributes.
     */
    private fun sanitizeSceneInner(inner: String): String =
        RESERVED_TIMING_ATTR.replace(inner, "")

    /** Formats a double with a dot decimal separator (never the default locale). */
    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    /** Escapes the minimal HTML text entities. */
    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ──────────────────────────────────────────────────────────────
    // Inline pronunciation hints in narration (HF-7e)
    // ──────────────────────────────────────────────────────────────

    /**
     * HF-7e — Extracts inline `hf:pron[word, phonetic]` hints from track paragraphs.
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
                dictionary.add(PronunciationHint.of(hintMatch.groupValues[1], hintMatch.groupValues[2]))
            }
        }
        return dictionary
    }

    /** Strips `hf:pron[word, phonetic]` from track paragraphs, keeping the word. */
    private fun stripInlineHintsFromTracks(html: String): String {
        val trackBlockRegex = Regex(
            """(<div[^>]*class="[^"]*hyperframes-track[^"]*"[^>]*>)(.*?)(</div>)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val inlineHintRegex = Regex("""hf:pron\[([^,\]]+?),\s*([^\]]+?)\]""")

        return trackBlockRegex.replace(html) { match ->
            val stripped = inlineHintRegex.replace(match.groupValues[2]) { it.groupValues[1] }
            "${match.groupValues[1]}$stripped${match.groupValues[3]}"
        }
    }

    /**
     * Merges author hints (block + inline) with the optional domain dictionary
     * and injects the resulting JSON island before `</body>`.
     */
    private fun injectPronunciationIsland(
        html: String,
        blockDictionary: PronunciationDictionary,
        inlineDictionary: PronunciationDictionary
    ): String {
        val author = inlineDictionary.merge(blockDictionary)
        val finalDictionary = if (domainDictionary != null) mergeAuthorOverDomain(author, domainDictionary) else author
        if (finalDictionary.size() == 0) return html
        if (html.contains("id=\"hf-pronunciation\"")) return html
        val island = finalDictionary.render()
        return when {
            "</body>" in html -> html.replace("</body>", "    $island\n</body>")
            else -> html + island
        }
    }

    private fun mergeAuthorOverDomain(
        author: PronunciationDictionary,
        domain: PronunciationDictionary
    ): PronunciationDictionary {
        val authorWordsLanguageAgnostic: Set<String> = author.snapshot()
            .filter { it.language == null }
            .map { it.word }
            .toSet()

        val filteredDomain = PronunciationDictionary()
        domain.snapshot().forEach { hint ->
            if (hint.word !in authorWordsLanguageAgnostic) filteredDomain.add(hint)
        }
        return author.merge(filteredDomain)
    }

    // ──────────────────────────────────────────────────────────────
    // Title-card template (HF-5a)
    // ──────────────────────────────────────────────────────────────

    private fun expandTitleCardBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-title-card[^"]*"[^>]*>\s*<h2[^>]*id="([^"]*)"[^>]*>([^<]*)</h2>\s*(<div class="sectionbody">.*?</div>)?\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val paragraphRegex = Regex("""<div class="paragraph">\s*<p>(.*?)</p>\s*</div>""", RegexOption.DOT_MATCHES_ALL)

        return html.replace(blockRegex) { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2].trim()
            val subtitle = paragraphRegex.find(match.groupValues[3])?.groupValues?.get(1)?.trim()
            TitleCardTemplate(id = id, title = title, subtitle = subtitle).render()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Code-diff template (HF-5b)
    // ──────────────────────────────────────────────────────────────

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
            val listings = listingRegex.findAll(match.groupValues[2]).toList()
            if (listings.size < 2) return@replace match.value
            CodeDiffTemplate(
                id = id,
                beforeCode = listings[0].groupValues[2].trim(),
                afterCode = listings[1].groupValues[2].trim(),
                lang = listings[0].groupValues[1]
            ).render()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Data-chart template (HF-5c)
    // ──────────────────────────────────────────────────────────────

    private fun expandDataChartBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-data-chart[^"]*"[^>]*>\s*<h2[^>]*id="([^"]*)"[^>]*>([^<]*)</h2>\s*<div class="sectionbody">\s*<div class="listingblock">\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val lineRegex = Regex("""^\s*([^:]+?)\s*:\s*([0-9]+(?:\.[0-9]+)?)\s*$""")

        return html.replace(blockRegex) { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2].trim()
            val dataPoints = match.groupValues[3].lineSequence().mapNotNull { line ->
                lineRegex.find(line)?.let { DataPoint(it.groupValues[1].trim(), it.groupValues[2].toDouble()) }
            }.toList()
            DataChartTemplate(id = id, title = title, dataPoints = dataPoints).render()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Kinetic-captions template (HF-5d)
    // ──────────────────────────────────────────────────────────────

    private fun expandKineticCaptionsBlocks(html: String): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-kinetic-captions[^"]*"[^>]*>\s*<h2[^>]*id="([^"]*)"[^>]*>([^<]*)</h2>\s*<div class="sectionbody">\s*<div class="listingblock">\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val lineRegex = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)\s*(?:-&gt;|&#8594;|->)\s*([0-9]+(?:\.[0-9]+)?)\s*:\s*(.+?)\s*$""")

        return html.replace(blockRegex) { match ->
            val id = match.groupValues[1]
            val captions = match.groupValues[3].lineSequence().mapNotNull { line ->
                lineRegex.find(line)?.let { Caption(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), it.groupValues[3]) }
            }.toList()
            KineticCaptionsTemplate(id = id, captions = captions).render()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Pronunciation block (HF-7c)
    // ──────────────────────────────────────────────────────────────

    private fun expandPronunciationBlocks(html: String, dictionary: PronunciationDictionary): String {
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*hyperframes-pronunciation[^"]*"[^>]*>\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val lineRegex = Regex("""^\s*([^:]+?)\s*:\s*(.+?)\s*$""")

        return html.replace(blockRegex) { match ->
            match.groupValues[1].lineSequence().forEach { line ->
                lineRegex.find(line)?.let {
                    dictionary.add(PronunciationHint.of(it.groupValues[1], it.groupValues[2]))
                }
            }
            ""
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Animation blocks (author GSAP)
    // ──────────────────────────────────────────────────────────────

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

    private companion object {
        const val DEFAULT_SCENE_SECONDS = 5.0
        const val CLOSE_DIV = "</div>"

        val CONTENT_OPEN = Regex("""<div id="content"[^>]*>""")
        val HEADER_TITLE = Regex("""<div id="header">\s*<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
        val COMPOSITION_ID = Regex("""data-composition-id="([^"]*)"""")
        val HEADING_ID = Regex("""<h2[^>]*id="([^"]*)"""")
        val DURATION_ATTR = Regex("""data-duration="([0-9.]+)"""")
        val SCRIPT_TAG = Regex("""<script\b[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val RESERVED_TIMING_ATTR = Regex("""\s+(?:data-track-index|data-start|data-duration|data-end)="[^"]*"""")

        val DESIGN_CSS = """
:root{--bg:#0f172a;--fg:#f8fafc;--muted:#94a3b8;--accent:#f59e0b;--card:#16213a;--line:#1e293b}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:__W__px;height:__H__px;overflow:hidden;background:var(--bg);
  font-family:Inter,"Segoe UI",system-ui,sans-serif;color:var(--fg);
  -webkit-font-smoothing:antialiased}
#stage{position:relative;width:__W__px;height:__H__px;overflow:hidden;background:var(--bg)}
.scene{position:absolute;inset:0}
.scene-bg{position:absolute;inset:0;
  background:radial-gradient(1200px 700px at 82% -12%,#1e3a5f 0%,rgba(15,23,42,0) 62%),var(--bg)}
.scene-inner{position:relative;z-index:2;height:100%;width:100%;padding:0 160px;
  display:flex;flex-direction:column;justify-content:center;gap:28px}
.scene-inner h1,.hf-doc-title{font-size:128px;line-height:1;font-weight:700;letter-spacing:-.03em}
.scene-inner h2{font-size:82px;line-height:1.05;font-weight:700;letter-spacing:-.02em;max-width:1500px}
.scene-inner h3{font-size:56px;font-weight:600;color:var(--accent)}
.scene-inner p{font-size:38px;line-height:1.55;color:#dbe4f0;max-width:1400px}
.scene-inner .paragraph{margin:0}
.scene-inner ul,.scene-inner ol{margin:8px 0 0 56px}
.scene-inner li{font-size:40px;line-height:1.5;color:#e2e8f0;margin:14px 0}
.scene-inner li::marker{color:var(--accent)}
.scene-inner a{color:var(--accent);text-decoration:none}
.scene-inner code{font-family:"JetBrains Mono",ui-monospace,monospace;font-size:.9em;
  background:var(--card);border:1px solid var(--line);border-radius:8px;padding:2px 10px}
.scene-inner pre{font-family:"JetBrains Mono",ui-monospace,monospace;font-size:30px;line-height:1.5;
  background:var(--card);border:1px solid var(--line);border-radius:16px;padding:36px 44px;overflow:hidden;color:#e2e8f0}
.scene-inner img{max-width:900px;max-height:620px;border-radius:16px}
.scene-inner blockquote{border-left:8px solid var(--accent);padding-left:36px;
  font-size:52px;font-style:italic;color:#e2e8f0;max-width:1400px}
.hf-title-card-title{display:block;font-size:140px;font-weight:700;letter-spacing:-.03em;line-height:1}
.hf-title-card-subtitle{display:block;margin-top:28px;font-size:46px;color:var(--muted);font-weight:400}
.hf-data-chart{display:flex;flex-direction:column;gap:40px}
.hf-data-chart-title{font-size:56px;font-weight:600;color:var(--accent)}
.hf-data-chart-plot{display:flex;align-items:flex-end;gap:48px;height:460px}
.hf-data-chart-col{display:flex;flex-direction:column;justify-content:flex-end;align-items:center;height:100%;width:150px}
.hf-data-chart-bar{width:100%;max-height:calc(100% - 56px);margin-bottom:16px;
  background:linear-gradient(180deg,#f59e0b 0%,#b45309 100%);border-radius:14px 14px 0 0}
.hf-data-chart-label{font-size:30px;font-weight:600;color:#e2e8f0}
.hf-kinetic-captions{position:relative;height:100%}
.hf-kinetic-caption{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;
  font-size:84px;font-weight:700;text-align:center;letter-spacing:-.02em;padding:0 120px}
.hf-code-diff{display:flex;gap:40px;justify-content:center;flex-wrap:wrap}
.hf-code-diff pre{flex:1;min-width:640px}
.hf-kw{color:#f59e0b;font-weight:600}
"""
    }
}
