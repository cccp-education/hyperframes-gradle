package hyperframes.template

/**
 * HF-5b — "code-diff" template: animated code diff with syntax highlighting.
 *
 * Pure DDD value object — produces a valid HyperFrames HTML fragment,
 * composed of a `hyperframes-composition` block + a track carrying two
 * side-by-side code blocks (before / after) and a GSAP timeline that
 * fades out the before block then switches to after. No side effects.
 *
 * Syntax highlighting is lexical and deterministic: the keywords of the
 * [lang] language are wrapped in `<span class="hf-kw">`. No external
 * dependency (Prism.js / highlight.js) — rich highlighting remains a
 * future evolution via a `highlight` option.
 *
 * @property id composition identifier (rendered as `data-composition-id`)
 * @property beforeCode source displayed first (initial state)
 * @property afterCode source displayed second (final state, after refactor)
 * @property lang syntax highlighting language (default: "kotlin")
 * @property durationSeconds track duration in seconds (default: 3.0)
 */
data class CodeDiffTemplate(
    val id: String,
    val beforeCode: String,
    val afterCode: String,
    val lang: String = "kotlin",
    val durationSeconds: Double = 3.0
) {

    /**
     * Renders the HyperFrames HTML fragment.
     *
     * Returns a `.hyperframes-composition` block containing a single track
     * whose duration is [durationSeconds] (rounded to the nearest integer
     * for `data-duration`), two side-by-side `<pre>` blocks (before / after)
     * with lexical syntax highlighting, and a GSAP timeline that:
     * 1. shows the before block (fade-in)
     * 2. after 40% of the duration, switches to after (fade-out before + fade-in after)
     */
    fun render(): String = buildString {
        val durationInt = durationSeconds.toInt()
        val switchAt = (durationInt * 0.4).toString()
        appendLine("<div class=\"sect1 hyperframes-composition\" data-composition-id=\"$id\">")
        appendLine("  <div class=\"sectionbody\">")
        appendLine("    <div class=\"paragraph hyperframes-track\" data-track-index=\"0\" data-start=\"0\" data-duration=\"$durationInt\">")
        appendLine("      <div class=\"hf-code-diff\">")
        appendLine("        <pre class=\"hf-code-diff-before hf-lang-$lang\">${highlight(beforeCode)}</pre>")
        appendLine("        <pre class=\"hf-code-diff-after hf-lang-$lang\" style=\"display:none\">${highlight(afterCode)}</pre>")
        appendLine("      </div>")
        appendLine("    </div>")
        appendLine("    <script type=\"text/javascript\">")
        appendLine("      window.__timelines = window.__timelines || {};")
        appendLine("      (function () {")
        appendLine("        const tl = gsap.timeline({ paused: true });")
        appendLine("        tl.from(\"#$id .hf-code-diff-before\", { opacity: 0, duration: 0.4 }, 0);")
        appendLine("        tl.to(\"#$id .hf-code-diff-before\", { opacity: 0, duration: 0.3 }, $switchAt);")
        appendLine("        tl.set(\"#$id .hf-code-diff-after\", { display: \"block\" }, $switchAt);")
        appendLine("        tl.from(\"#$id .hf-code-diff-after\", { opacity: 0, duration: 0.4 }, $switchAt);")
        appendLine("        window.__timelines[\"$id\"] = tl;")
        appendLine("      })();")
        appendLine("    </script>")
        appendLine("  </div>")
        append("</div>")
    }

    /**
     * Lexical syntax highlighting — wraps the keywords of the [lang] language
     * in `<span class="hf-kw">`. Deterministic, no external dependency.
     *
     * The source code HTML is escaped (`<`, `>`, `&`) before highlighting
     * to prevent injection or tag breaking.
     */
    private fun highlight(code: String): String {
        val escaped = code
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val keywords = keywordsFor(lang) ?: return escaped
        if (keywords.isEmpty()) return escaped
        val pattern = Regex("\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b")
        return pattern.replace(escaped) { match ->
            "<span class=\"hf-kw\">${match.value}</span>"
        }
    }

    /**
     * Keywords per language. Returns null when the language is unknown
     * (in which case no wrapping is applied — escaped raw code).
     */
    private fun keywordsFor(lang: String): List<String>? = when (lang) {
        "kotlin" -> listOf(
            "val", "var", "fun", "class", "object", "interface", "if", "else",
            "when", "return", "for", "while", "do", "in", "is", "as", "null",
            "true", "false", "private", "public", "internal", "protected",
            "override", "open", "abstract", "sealed", "data", "enum", "import",
            "package", "companion", "suspend", "lateinit", "init"
        )
        "java" -> listOf(
            "public", "private", "protected", "class", "interface", "enum",
            "if", "else", "for", "while", "do", "return", "new", "null",
            "true", "false", "static", "final", "void", "int", "long",
            "double", "float", "boolean", "char", "byte", "short", "import",
            "package", "extends", "implements", "throws", "throw", "try",
            "catch", "finally", "this", "super"
        )
        "python" -> listOf(
            "def", "class", "if", "elif", "else", "for", "while", "return",
            "import", "from", "as", "pass", "break", "continue", "in", "is",
            "not", "and", "or", "None", "True", "False", "lambda", "with",
            "try", "except", "finally", "raise", "yield", "global", "nonlocal",
            "assert", "del"
        )
        "javascript", "js", "typescript", "ts" -> listOf(
            "const", "let", "var", "function", "return", "if", "else", "for",
            "while", "do", "class", "extends", "import", "export", "from",
            "new", "null", "undefined", "true", "false", "this", "super",
            "async", "await", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "interface", "type", "enum", "public", "private",
            "protected", "readonly", "static"
        )
        else -> null
    }
}