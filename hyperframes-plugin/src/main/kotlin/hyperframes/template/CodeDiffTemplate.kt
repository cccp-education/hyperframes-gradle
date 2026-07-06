package hyperframes.template

/**
 * HF-5b — Template "code-diff" : animation diff de code avec coloration syntaxique.
 *
 * Value object DDD pur — produit un fragment HTML HyperFrames valide,
 * composé d'un bloc `hyperframes-composition` + un track portant deux
 * blocs de code côte à côte (before / after) et une timeline GSAP qui
 * fondu le bloc before puis bascule vers after. Aucun effet de bord.
 *
 * La coloration syntaxique est lexicale et déterministe : les mots-clés
 * du langage [lang] sont wrappés dans des `<span class="hf-kw">`. Aucune
 * dépendance externe (Prism.js / highlight.js) — la coloration riche
 * reste une évolution future via une option `highlight`.
 *
 * @property id identifiant de composition (rendu dans `data-composition-id`)
 * @property beforeCode source affiché en premier (état initial)
 * @property afterCode source affiché en second (état final, après refactor)
 * @property lang langage de coloration syntaxique (défaut : "kotlin")
 * @property durationSeconds durée du track en secondes (défaut : 3.0)
 */
data class CodeDiffTemplate(
    val id: String,
    val beforeCode: String,
    val afterCode: String,
    val lang: String = "kotlin",
    val durationSeconds: Double = 3.0
) {

    /**
     * Rend le fragment HTML HyperFrames.
     *
     * Retourne un bloc `.hyperframes-composition` contenant un track unique
     * dont la durée est [durationSeconds] (arrondie à l'entier le plus proche
     * pour `data-duration`), deux blocs `<pre>` côte à côte (before / after)
     * avec coloration syntaxique lexicale, et une timeline GSAP qui :
     * 1. affiche le bloc before (fade-in)
     * 2. après 40% de la durée, bascule vers after (fade-out before + fade-in after)
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
     * Coloration syntaxique lexicale — wrap les mots-clés du langage [lang]
     * dans des `<span class="hf-kw">`. Déterministe, sans dépendance externe.
     *
     * Le HTML du code source est échappé (`<`, `>`, `&`) avant la coloration
     * pour éviter toute injection ou cassure de balise.
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
     * Mots-clés par langage. Retourne null si le langage est inconnu
     * (auquel cas aucun wrapping n'est appliqué — code brut échappé).
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