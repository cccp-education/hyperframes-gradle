package hyperframes.vendor

/**
 * HF-CR3-1 — vendored GSAP runtime.
 *
 * The plugin used to inject the external GSAP CDN (`cdnjs.cloudflare.com`) into
 * every generated page, so producing a video required network access at render
 * time. This object embeds the GSAP 3.12.5 minified build as a plugin classpath
 * resource and returns it as an inline `<script>` element, removing the runtime
 * CDN dependency.
 *
 * The full [source] is inlined verbatim, so the original GreenSock license banner
 * (`GSAP 3.12.5 ... GreenSock ...`) is preserved in the output — the
 * redistribution notice travels with every rendered page.
 *
 * Pure value provider: no I/O beyond a single classpath resource read, no network,
 * no Gradle coupling. Missing resource is a packaging bug and fails loudly rather
 * than silently degrading the render.
 */
object GsapLibrary {

    /** The vendored GSAP version. */
    const val VERSION = "3.12.5"

    /** File name the runtime is written to inside the composition directory. */
    const val FILE_NAME = "gsap-3.12.5.min.js"

    /** Classpath location of the vendored minified build. */
    private const val RESOURCE_PATH = "/vendor/gsap-3.12.5.min.js"

    /**
     * The vendored minified GSAP source, loaded once from the plugin classpath.
     * Includes the original GreenSock license banner.
     */
    val source: String by lazy { loadSource() }

    /**
     * Returns the vendored GSAP runtime as an inline `<script>` element, ready
     * to be inserted in the page `<head>`.
     *
     * The raw script cannot contain the `</script>` sequence (a JavaScript source
     * must not embed an unescaped closing script tag); this is asserted defensively
     * so a corrupted vendor file fails loudly instead of breaking the page.
     */
    fun inlineScript(): String {
        val js = source
        check(!js.contains("</script>")) {
            "Vendored GSAP resource contains an unescaped </script> sequence: $RESOURCE_PATH"
        }
        return "<script>\n$js\n</script>"
    }

    /**
     * Returns a `<script>` element that loads the vendored runtime from the
     * composition directory as an **external** file ([FILE_NAME]).
     *
     * HF-CR4 — the HyperFrames CLI lints the composition before rendering and
     * flags any inlined third-party runtime as `non_deterministic_code`
     * (GSAP's ticker uses `Date.now()`/`Math.random()` internally). Loading it
     * as a sibling file keeps the deterministic authoring surface clean while
     * remaining fully offline (no CDN): the producer task writes [source] next
     * to the generated HTML.
     */
    fun externalScript(): String = "<script src=\"$FILE_NAME\"></script>"

    private fun loadSource(): String =
        GsapLibrary::class.java.getResourceAsStream(RESOURCE_PATH)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Vendored GSAP resource not found on the classpath: $RESOURCE_PATH")
}
