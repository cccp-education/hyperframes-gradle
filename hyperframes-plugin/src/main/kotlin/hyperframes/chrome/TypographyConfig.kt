package hyperframes.chrome

/**
 * HF-DEMO-QUALITY US-4 — configurable display and monospace typography.
 *
 * Fonts are plain CSS font stacks (no remote import): the render is offline and
 * deterministic, so a missing font falls back through the declared stack. The
 * defaults mirror the reference `demo-video/` direction (League Gothic display,
 * IBM Plex Mono code/chrome) with safe system fallbacks.
 *
 * @property displayFont CSS font stack used for titles and display headings
 * @property monoFont    CSS font stack used for code, captions and chrome
 */
data class TypographyConfig(
    val displayFont: String = DEFAULT_DISPLAY_FONT,
    val monoFont: String = DEFAULT_MONO_FONT,
) {
    init {
        require(displayFont.isNotBlank()) { "displayFont must not be blank" }
        require(monoFont.isNotBlank()) { "monoFont must not be blank" }
        require(SAFE_FONT_STACK.matches(displayFont)) {
            "displayFont must be a plain CSS font stack (no braces, semicolons or url())"
        }
        require(SAFE_FONT_STACK.matches(monoFont)) {
            "monoFont must be a plain CSS font stack (no braces, semicolons or url())"
        }
    }

    companion object {
        const val DEFAULT_DISPLAY_FONT = """"League Gothic", "Arial Narrow", Impact, sans-serif"""
        const val DEFAULT_MONO_FONT = """"IBM Plex Mono", "JetBrains Mono", ui-monospace, monospace"""

        /**
         * Font-family names, quotes, commas, spaces and harmless separators only.
         * Rejects `{}`, `;` and `url(` so a configuration value can never escape
         * the CSS declaration it is injected into.
         */
        private val SAFE_FONT_STACK = Regex("""^[A-Za-z0-9 ,"'_\-]+$""")
    }
}
