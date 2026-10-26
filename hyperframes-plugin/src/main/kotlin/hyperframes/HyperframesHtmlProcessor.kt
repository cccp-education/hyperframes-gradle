package hyperframes

import hyperframes.chrome.ChromeConfig
import hyperframes.chrome.ChromeRenderer
import hyperframes.chrome.TypographyConfig
import hyperframes.entrance.EntranceConfig
import hyperframes.entrance.SceneEntrancePlanner
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
    private val sceneDurationSeconds: Double,
    private val chrome: ChromeConfig = ChromeConfig(),
    private val typography: TypographyConfig = TypographyConfig(),
    private val entrances: EntranceConfig = EntranceConfig()
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
        HyperframesHtmlProcessor(
            width, height, fps, outputName, domain, sceneDurationSeconds, chrome, typography, entrances
        )

    /**
     * HF-DEMO-QUALITY US-4 — returns a copy of this processor wired with the
     * studio [chrome] (disabled by default, so the historical composition is
     * preserved unless a consumer opts in).
     */
    fun withChrome(chrome: ChromeConfig): HyperframesHtmlProcessor =
        HyperframesHtmlProcessor(
            width, height, fps, outputName, domainDictionary, sceneDurationSeconds, chrome, typography, entrances
        )

    /**
     * HF-DEMO-QUALITY US-4 — returns a copy of this processor wired with
     * configurable [typography] (display + monospace font stacks).
     */
    fun withTypography(typography: TypographyConfig): HyperframesHtmlProcessor =
        HyperframesHtmlProcessor(
            width, height, fps, outputName, domainDictionary, sceneDurationSeconds, chrome, typography, entrances
        )

    /**
     * HF-DEMO-QUALITY US-5 — returns a copy of this processor wired with
     * choreographed per-scene [entrances] (opt-in; the historical grouped
     * entrance is preserved by default).
     */
    fun withEntrances(entrances: EntranceConfig): HyperframesHtmlProcessor =
        HyperframesHtmlProcessor(
            width, height, fps, outputName, domainDictionary, sceneDurationSeconds, chrome, typography, entrances
        )

    /**
     * Single entry point: transforms AsciiDoc HTML into a conformant
     * HyperFrames composition document.
     */
    fun enhance(html: String): String {
        val inlineDictionary = extractInlineHints(html)
        val blockDictionary = PronunciationDictionary()
        val audioCues = mutableListOf<AudioCue>()
        val expanded = html
            .let { expandTitleCardBlocks(it) }
            .let { expandCodeDiffBlocks(it) }
            .let { expandDataChartBlocks(it) }
            .let { expandKineticCaptionsBlocks(it) }
            .let { expandPronunciationBlocks(it, blockDictionary) }
            .let { expandAudioBlocks(it, audioCues) }
            .let { stripInlineHintsFromTracks(it) }
            .let { enhanceAnimationBlocks(it) }
        val composition = buildComposition(expanded, audioCues)
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
        val scripts: List<String>,
        val trackIndex: Int,
        val transition: Transition? = null,
        val allowOverlap: Boolean = false
    )

    /** A scene before its start time is assigned (overlap depends on the next scene). */
    private data class RawScene(
        val id: String,
        val duration: Double,
        val inner: String,
        val scripts: List<String>,
        val transition: Transition?
    )

    /**
     * Scene transitions supported by the DSL.
     *
     * [GRID_DISSOLVE], [SHUTTER] and [FADE] are **cover** transitions: the two
     * scenes do not overlap, the overlay owns the handoff. [BLUR_CROSSFADE] and
     * [VERTICAL_PUSH] are **overlap** transitions: the incoming scene starts
     * before the outgoing one ends and both clips are visible on their tracks
     * (HF-DEMO-QUALITY US-2b).
     */
    private enum class TransitionKind {
        GRID_DISSOLVE,
        SHUTTER,
        FADE,
        BLUR_CROSSFADE,
        VERTICAL_PUSH
    }

    private data class Transition(
        val kind: TransitionKind,
        val durationSeconds: Double =
            if (kind == TransitionKind.BLUR_CROSSFADE || kind == TransitionKind.VERTICAL_PUSH) {
                DEFAULT_OVERLAP_SECONDS
            } else {
                DEFAULT_TRANSITION_SECONDS
            }
    ) {
        val isOverlap: Boolean
            get() = kind == TransitionKind.BLUR_CROSSFADE || kind == TransitionKind.VERTICAL_PUSH

        val overlapSeconds: Double
            get() = if (isOverlap) durationSeconds else 0.0
    }

    /** A cover-transition boundary carrying the outgoing scene's transition. */
    private data class Boundary(
        val overlayId: String,
        val outgoingId: String,
        val incomingId: String,
        val time: Double,
        val transition: Transition
    )

    /** An overlap-transition boundary: the incoming clip starts before the outgoing ends. */
    private data class OverlapBoundary(
        val outgoingId: String,
        val incomingId: String,
        val start: Double,
        val overlapSeconds: Double,
        val kind: TransitionKind
    )

    /** A BGM/SFX cue declared by an `[.hyperframes-audio]` block (US-3). */
    private data class AudioCue(
        val file: String,
        val start: Double,
        val volume: Double,
        val kind: String
    )

    private fun buildComposition(html: String, audioCues: List<AudioCue> = emptyList()): String {
        val contentInner = extractContentInner(html) ?: html
        val headerTitle = extractHeaderTitle(html)

        val rawScenes = mutableListOf<RawScene>()
        if (headerTitle != null) {
            rawScenes.add(
                RawScene(
                    id = "hf-title",
                    duration = sceneDurationSeconds,
                    inner = "<h1 class=\"hf-doc-title\">$headerTitle</h1>",
                    scripts = emptyList(),
                    transition = null,
                )
            )
        }

        var auto = 0
        for (section in splitTopLevelSections(contentInner)) {
            auto++
            val id = sceneIdOf(section) ?: "scene-$auto"
            val duration = sceneDurationOf(section) ?: sceneDurationSeconds
            val scripts = extractScriptBodies(section)
            val inner = sanitizeSceneInner(stripSceneWrapper(stripScripts(section))).trim()
            rawScenes.add(RawScene(id, duration, inner, scripts, transitionOf(section)))
        }

        // HF-DEMO-QUALITY US-2b — an overlap transition pulls the next scene
        // earlier: both clips are visible on their own tracks for `durationSeconds`.
        // The last scene's transition is ignored (nothing to hand off to).
        var cursor = 0.0
        val scenes =
            rawScenes.mapIndexed { index, raw ->
                val hasNext = index < rawScenes.lastIndex
                val allowOverlap =
                    (hasNext && raw.transition?.isOverlap == true) ||
                        (index > 0 && rawScenes[index - 1].transition?.isOverlap == true)
                val scene =
                    Scene(
                        raw.id,
                        cursor,
                        raw.duration,
                        raw.inner,
                        raw.scripts,
                        index,
                        raw.transition,
                        allowOverlap,
                    )
                val overlap = if (hasNext) raw.transition?.overlapSeconds ?: 0.0 else 0.0
                cursor += raw.duration - overlap
                scene
            }
        val total =
            if (scenes.isNotEmpty()) scenes.last().start + scenes.last().duration else sceneDurationSeconds
        val scenesHtml = scenes.mapIndexed { index, scene ->
            renderScene(scene, index + 1, scenes.size)
        }.joinToString("\n")
        // A transition belongs to the **outgoing** scene and is only meaningful
        // when another scene follows (the last scene's role is ignored).
        val coverBoundaries = scenes.mapIndexedNotNull { index, scene ->
            val transition = scene.transition ?: return@mapIndexedNotNull null
            if (transition.isOverlap) return@mapIndexedNotNull null
            scenes.getOrNull(index + 1) ?: return@mapIndexedNotNull null
            Boundary(
                overlayId = "hf-transition-$index",
                outgoingId = scene.id,
                incomingId = scenes[index + 1].id,
                time = scene.start + scene.duration,
                transition = transition,
            )
        }
        val overlapBoundaries = scenes.mapIndexedNotNull { index, scene ->
            val transition = scene.transition ?: return@mapIndexedNotNull null
            if (!transition.isOverlap) return@mapIndexedNotNull null
            val incoming = scenes.getOrNull(index + 1) ?: return@mapIndexedNotNull null
            OverlapBoundary(
                outgoingId = scene.id,
                incomingId = incoming.id,
                start = incoming.start,
                overlapSeconds = transition.durationSeconds,
                kind = transition.kind,
            )
        }
        val transitionsHtml = coverBoundaries.joinToString("\n") { renderTransitionOverlay(it) }
        val audioHtml = renderAudio(audioCues, total)
        val progressHtml = ChromeRenderer.progressBar(chrome)
        val progressTween = ChromeRenderer.rootProgressTween(chrome, total)
        // HF-CR4-suite — every scene is a **sub-composition host**: it carries
        // `data-composition-id`, so the CLI no longer flags it as a nested timeline
        // element (`nested_structure_needs_subcomposition`) and the runtime
        // auto-nests its dedicated `window.__timelines[id]` timeline. One scene per
        // track also removes `timeline_track_too_dense`.
        val collectedScripts = scenes.flatMap { it.scripts }
            .joinToString("\n") { script -> "(function(){\n$script\n})();" }
        val sceneScripts = scenes.joinToString("\n") { sceneTimelineScript(it) }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=$width, height=$height\">")
            appendLine("<title>${escapeText(outputName)}</title>")
            appendLine(GsapLibrary.externalScript())
            appendLine("<style>")
            appendLine(
                DESIGN_CSS
                    .replace("__W__", width.toString())
                    .replace("__H__", height.toString())
                    .replace("__DISPLAY_FONT__", typography.displayFont)
                    .replace("__MONO_FONT__", typography.monoFont)
            )
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine(
                "<div id=\"stage\" data-composition-id=\"root\" data-start=\"0\" " +
                    "data-duration=\"${number(total)}\" data-width=\"$width\" data-height=\"$height\" " +
                    "data-fps=\"$fps\" data-output=\"${outputName}.mp4\">"
            )
            if (audioHtml.isNotBlank()) {
                appendLine(audioHtml)
            }
            appendLine(scenesHtml)
            if (transitionsHtml.isNotBlank()) {
                appendLine(transitionsHtml)
            }
            if (progressHtml.isNotBlank()) {
                appendLine(progressHtml)
            }
            appendLine("</div>")
            appendLine("<script>window.__timelines = window.__timelines || {};</script>")
            if (collectedScripts.isNotBlank()) {
                appendLine("<script>")
                appendLine(collectedScripts)
                appendLine("</script>")
            }
            appendLine("<script>")
            appendLine(sceneScripts)
            if (coverBoundaries.isEmpty() && overlapBoundaries.isEmpty() && progressTween.isBlank()) {
                // Historical, transition-free composition: one empty root timeline.
                appendLine("  window.__timelines[\"root\"] = gsap.timeline({ paused: true });")
            } else {
                appendLine(rootTransitionScript(coverBoundaries, overlapBoundaries, progressTween))
            }
            appendLine("</script>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    /** Renders one scene as a full-bleed sub-composition host with a background child. */
    private fun renderScene(scene: Scene, sceneNumber: Int, sceneTotal: Int): String = buildString {
        appendLine(
            "<div class=\"scene clip\" id=\"${scene.id}\" data-composition-id=\"${scene.id}\" " +
                "data-start=\"${number(scene.start)}\" data-duration=\"${number(scene.duration)}\" " +
                "data-track-index=\"${scene.trackIndex}\">"
        )
        appendLine("  <div class=\"scene-bg\"></div>")
        // US-2b — an overlap transition intentionally puts two scenes on screen;
        // mark the inner container (a marker on the scene root is ignored by the
        // HyperFrames layout check) so a designed crossfade/push is not reported
        // as an accidental text collision or canvas overflow.
        val overlapAttrs =
            if (scene.allowOverlap) " data-layout-allow-overlap data-layout-allow-overflow" else ""
        appendLine("  <div class=\"scene-inner\"$overlapAttrs>")
        appendLine(scene.inner)
        appendLine("  </div>")
        val chromeHtml = ChromeRenderer.sceneChrome(chrome, scene.start, fps, sceneNumber, sceneTotal)
        if (chromeHtml.isNotBlank()) {
            appendLine(chromeHtml)
        }
        appendLine("</div>")
    }

    /**
     * Per-scene timeline: reuses the `window.__timelines[id]` created by the
     * template/author script (or creates it) and adds the entrance/exit tweens.
     * The timeline stays `paused` — the runtime drives it as a sub-composition.
     */
    private fun sceneTimelineScript(scene: Scene): String = buildString {
        val id = scene.id
        val dur = scene.duration
        appendLine("  (function () {")
        appendLine("    var t = window.__timelines[\"$id\"] || gsap.timeline({ paused: true });")
        appendLine("    t.pause();")
        if (entrances.enabled) {
            // US-5 — hide the atoms at load, then reveal them in document order.
            appendLine(SceneEntrancePlanner.setupScript(id, entrances))
            appendLine(SceneEntrancePlanner.tweenScript(id, entrances))
        } else {
            appendLine(
                "    t.fromTo(\"#$id .scene-inner\", { opacity: 0, y: 30 }, " +
                    "{ opacity: 1, y: 0, duration: 0.6, ease: \"power3.out\" }, 0.1);"
            )
        }
        // A scene handing off to a transition must stay fully visible until the
        // boundary: the cover overlay (root timeline) owns the handoff, so the
        // historical pre-boundary fade would show the empty background too early.
        if (scene.transition == null && dur - 0.45 > 0.7) {
            appendLine("    t.to(\"#$id\", { opacity: 0, duration: 0.4, ease: \"power1.in\" }, ${number(dur - 0.45)});")
        }
        appendLine("    window.__timelines[\"$id\"] = t;")
        appendLine("    t.seek(0);")
        append("  })();")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scene transitions (HF-DEMO-QUALITY US-2 v1 — cover transitions)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Reads the outgoing-scene transition from its **role classes**.
     *
     * Asciidoctor drops non-standard block attributes, but it preserves roles.
     * The DSL therefore encodes the transition as a class on the composition
     * section:
     *
     * ```
     * [#intro.hyperframes-composition.hf-transition-grid]
     * == Introduction
     * ```
     *
     * Supported roles: `hf-transition-grid` (4×3 grid dissolve),
     * `hf-transition-shutter` (two halves close/open) and `hf-transition-fade`
     * (dip to the background colour) are **cover** transitions.
     * `hf-transition-blur-crossfade` and `hf-transition-vertical-push` are
     * **overlap** transitions (US-2b): the incoming scene starts before the
     * outgoing one ends, so both clips are visible during the handoff.
     * Unknown roles are ignored (backward compatible, no transition).
     */
    private fun transitionOf(section: String): Transition? {
        val openTag = section.substringBefore('>')
        val classes = CLASS_ATTR.find(openTag)?.groupValues?.get(1) ?: return null
        val kind = when {
            ROLE_GRID.containsMatchIn(classes) -> TransitionKind.GRID_DISSOLVE
            ROLE_SHUTTER.containsMatchIn(classes) -> TransitionKind.SHUTTER
            ROLE_FADE.containsMatchIn(classes) -> TransitionKind.FADE
            ROLE_BLUR.containsMatchIn(classes) -> TransitionKind.BLUR_CROSSFADE
            ROLE_PUSH.containsMatchIn(classes) -> TransitionKind.VERTICAL_PUSH
            else -> return null
        }
        return Transition(kind)
    }

    /** Renders the overlay a cover transition drives on the root timeline. */
    private fun renderTransitionOverlay(boundary: Boundary): String = buildString {
        when (boundary.transition.kind) {
            TransitionKind.GRID_DISSOLVE -> {
                appendLine("<div class=\"hf-transition hf-transition-grid\" id=\"${boundary.overlayId}\" data-layout-allow-occlusion>")
                repeat(GRID_CELLS) { i ->
                    appendLine(
                        "  <div class=\"hf-transition-cell\" " +
                            "style=\"background:${GRID_PALETTE[i % GRID_PALETTE.size]}\"></div>"
                    )
                }
                append("</div>")
            }
            TransitionKind.SHUTTER -> {
                appendLine("<div class=\"hf-transition hf-transition-shutter\" id=\"${boundary.overlayId}\" data-layout-allow-occlusion>")
                appendLine("  <div class=\"hf-shutter-top\" data-layout-allow-overflow></div>")
                appendLine("  <div class=\"hf-shutter-bottom\" data-layout-allow-overflow></div>")
                append("</div>")
            }
            TransitionKind.FADE -> {
                append("<div class=\"hf-transition hf-transition-fade\" id=\"${boundary.overlayId}\" data-layout-allow-occlusion></div>")
            }
            // Overlap transitions have no overlay: the two scene clips are both
            // visible and the root timeline animates their wrappers.
            TransitionKind.BLUR_CROSSFADE,
            TransitionKind.VERTICAL_PUSH,
            -> Unit
        }
    }

    /**
     * Root-timeline script: the **only** timeline that drives the transitions.
     *
     * Cover transitions ([Boundary]) are centred on the scene boundary `T`: the
     * cover grows for `duration/2`, the clip swap happens at `T` underneath it,
     * then the cover recedes. Overlap transitions ([OverlapBoundary]) animate
     * both scene wrappers during the shared window: blur/fade or vertical push.
     */
    private fun rootTransitionScript(
        coverBoundaries: List<Boundary>,
        overlapBoundaries: List<OverlapBoundary>,
        progressTween: String,
    ): String = buildString {
        appendLine("  (function () {")
        appendLine("    var rootTl = gsap.timeline({ paused: true });")
        if (progressTween.isNotBlank()) {
            appendLine(progressTween)
        }
        coverBoundaries.forEach { boundary ->
            val id = boundary.overlayId
            val time = boundary.time
            val half = boundary.transition.durationSeconds / 2
            when (boundary.transition.kind) {
                TransitionKind.GRID_DISSOLVE -> {
                    // Initial hidden state via gsap.set (immediate, outside the
                    // timeline): the lint rejects a CSS `transform: scale(0)` that a
                    // GSAP scale tween would overwrite.
                    appendLine("    gsap.set(\"#$id .hf-transition-cell\", { scale: 0 });")
                    appendLine(
                        "    rootTl.to(\"#$id .hf-transition-cell\", { scale: 1, duration: ${number(half)}, " +
                            "ease: \"power3.out\", stagger: 0.015 }, ${number(time - half)});"
                    )
                    appendLine(
                        "    rootTl.to(\"#$id .hf-transition-cell\", { scale: 0, duration: ${number(half)}, " +
                            "ease: \"power3.in\", stagger: 0.015 }, ${number(time + 0.02)});"
                    )
                }
                TransitionKind.SHUTTER -> {
                    // Panels rest OUTSIDE the frame (open shutter). gsap.set (not a
                    // CSS transform) keeps the lint happy and the frame uncovered
                    // outside the transition window.
                    appendLine("    gsap.set(\"#$id .hf-shutter-top\", { yPercent: -100 });")
                    appendLine("    gsap.set(\"#$id .hf-shutter-bottom\", { yPercent: 100 });")
                    appendLine(
                        "    rootTl.to(\"#$id .hf-shutter-top\", { yPercent: 0, duration: ${number(half)}, " +
                            "ease: \"power3.in\" }, ${number(time - half)});"
                    )
                    appendLine(
                        "    rootTl.to(\"#$id .hf-shutter-bottom\", { yPercent: 0, duration: ${number(half)}, " +
                            "ease: \"power3.in\" }, ${number(time - half)});"
                    )
                    appendLine(
                        "    rootTl.to(\"#$id .hf-shutter-top\", { yPercent: -100, duration: ${number(half)}, " +
                            "ease: \"power3.out\" }, ${number(time + 0.02)});"
                    )
                    appendLine(
                        "    rootTl.to(\"#$id .hf-shutter-bottom\", { yPercent: 100, duration: ${number(half)}, " +
                            "ease: \"power3.out\" }, ${number(time + 0.02)});"
                    )
                }
                TransitionKind.FADE -> {
                    appendLine(
                        "    rootTl.fromTo(\"#$id\", { opacity: 0 }, { opacity: 1, duration: ${number(half)}, " +
                            "ease: \"power2.in\" }, ${number(time - half)});"
                    )
                    appendLine(
                        "    rootTl.to(\"#$id\", { opacity: 0, duration: ${number(half)}, " +
                            "ease: \"power2.out\" }, ${number(time + 0.02)});"
                    )
                }
                // Overlap transitions are rendered by the loop below.
                TransitionKind.BLUR_CROSSFADE,
                TransitionKind.VERTICAL_PUSH,
                -> Unit
            }
        }
        overlapBoundaries.forEach { boundary ->
            val start = number(boundary.start)
            val duration = number(boundary.overlapSeconds)
            when (boundary.kind) {
                TransitionKind.BLUR_CROSSFADE -> {
                    // The incoming scene is transparent and blurred at its start;
                    // the outgoing scene blurs/fades out over the shared window.
                    appendLine("    gsap.set(\"#${boundary.incomingId}\", { opacity: 0, filter: \"blur(18px)\" });")
                    appendLine(
                        "    rootTl.to(\"#${boundary.outgoingId}\", { opacity: 0, filter: \"blur(18px)\", " +
                            "duration: $duration, ease: \"power2.inOut\" }, $start);"
                    )
                    appendLine(
                        "    rootTl.to(\"#${boundary.incomingId}\", { opacity: 1, filter: \"blur(0px)\", " +
                            "duration: $duration, ease: \"power2.inOut\" }, $start);"
                    )
                }
                TransitionKind.VERTICAL_PUSH -> {
                    // The incoming scene starts below the frame; both wrappers
                    // translate together over the shared window.
                    appendLine("    gsap.set(\"#${boundary.incomingId}\", { yPercent: 100 });")
                    appendLine(
                        "    rootTl.to(\"#${boundary.outgoingId}\", { yPercent: -100, " +
                            "duration: $duration, ease: \"power2.inOut\" }, $start);"
                    )
                    appendLine(
                        "    rootTl.to(\"#${boundary.incomingId}\", { yPercent: 0, " +
                            "duration: $duration, ease: \"power2.inOut\" }, $start);"
                    )
                }
                else -> Unit // cover transitions are handled above
            }
        }
        appendLine("    window.__timelines[\"root\"] = rootTl;")
        append("  })();")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Audio cues (HF-DEMO-QUALITY US-3)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Extracts `[.hyperframes-audio]` listing blocks and removes them from the
     * document body.
     *
     * DSL (one cue per line):
     *
     * ```
     * [.hyperframes-audio]
     * ----
     * bgm: assets/audio/bgm.mp3 volume=0.2
     * sfx: 6.0 assets/audio/whoosh.mp3 volume=0.25
     * ----
     * ```
     *
     * Lines starting with `#` and blank lines are ignored. An invalid cue is
     * skipped (defensive: a typo never breaks the build).
     */
    private fun expandAudioBlocks(html: String, cues: MutableList<AudioCue>): String {
        val block = Regex(
            """<div[^>]*class="[^"]*hyperframes-audio[^"]*"[^>]*>\s*<div class="content">\s*<pre>(.*?)</pre>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        return block.replace(html) { match ->
            match.groupValues[1].lineSequence().forEach { line ->
                parseAudioCue(line)?.let(cues::add)
            }
            ""
        }
    }

    private fun parseAudioCue(line: String): AudioCue? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val kind = when {
            trimmed.startsWith("bgm:") -> "bgm"
            trimmed.startsWith("sfx:") -> "sfx"
            else -> return null
        }
        val tokens = trimmed.substringAfter(':').trim().split(Regex("""\s+"""))
        val volume = tokens.firstOrNull { it.startsWith("volume=") }
            ?.substringAfter('=')?.toDoubleOrNull() ?: AUDIO_DEFAULT_VOLUME
        val args = tokens.filterNot { it.startsWith("volume=") }
        return if (kind == "bgm") {
            val file = args.firstOrNull() ?: return null
            AudioCue(file, 0.0, volume, kind)
        } else {
            val start = args.getOrNull(0)?.toDoubleOrNull() ?: return null
            val file = args.getOrNull(1) ?: return null
            AudioCue(file, start, volume, kind)
        }
    }

    /**
     * Emits one `<audio>` per cue as a DIRECT child of `#stage` (the runtime only
     * drives direct root children). BGM is bounded to the composition duration;
     * SFX use their intrinsic length.
     */
    private fun renderAudio(cues: List<AudioCue>, total: Double): String =
        cues.mapIndexed { index, cue ->
            val duration = if (cue.kind == "bgm") " data-duration=\"${number(total)}\"" else ""
            "<audio id=\"hf-audio-$index\" src=\"${escapeText(cue.file)}\" " +
                "data-start=\"${number(cue.start)}\"$duration " +
                "data-track-index=\"${AUDIO_TRACK_INDEX + index}\" " +
                "data-volume=\"${number(cue.volume)}\"></audio>"
        }.joinToString("\n")

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
        const val DEFAULT_TRANSITION_SECONDS = 0.5
        const val DEFAULT_OVERLAP_SECONDS = 0.6
        const val GRID_CELLS = 12
        const val AUDIO_DEFAULT_VOLUME = 0.2
        const val AUDIO_TRACK_INDEX = 90
        const val CLOSE_DIV = "</div>"

        val CONTENT_OPEN = Regex("""<div id="content"[^>]*>""")
        val HEADER_TITLE = Regex("""<div id="header">\s*<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
        val COMPOSITION_ID = Regex("""data-composition-id="([^"]*)"""")
        val HEADING_ID = Regex("""<h2[^>]*id="([^"]*)"""")
        val DURATION_ATTR = Regex("""data-duration="([0-9.]+)"""")
        val SCRIPT_TAG = Regex("""<script\b[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val RESERVED_TIMING_ATTR = Regex("""\s+(?:data-track-index|data-start|data-duration|data-end)="[^"]*"""")
        val CLASS_ATTR = Regex("""class="([^"]*)"""")
        val ROLE_GRID = Regex("""(?:^|\s)hf-transition-grid(?:\s|$)""")
        val ROLE_SHUTTER = Regex("""(?:^|\s)hf-transition-shutter(?:\s|$)""")
        val ROLE_FADE = Regex("""(?:^|\s)hf-transition-fade(?:\s|$)""")
        val ROLE_BLUR = Regex("""(?:^|\s)hf-transition-blur-crossfade(?:\s|$)""")
        val ROLE_PUSH = Regex("""(?:^|\s)hf-transition-vertical-push(?:\s|$)""")
        val GRID_PALETTE = listOf("#f59e0b", "#38bdf8", "#f8fafc", "#1e293b", "#b45309")

        val DESIGN_CSS = """
:root{--bg:#0f172a;--fg:#f8fafc;--muted:#94a3b8;--accent:#f59e0b;--card:#16213a;--line:#1e293b;
  --hf-display-font:__DISPLAY_FONT__;--hf-mono-font:__MONO_FONT__}
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
.scene-inner h1,.hf-doc-title{font-family:var(--hf-display-font);font-size:128px;line-height:1;font-weight:700;letter-spacing:-.03em}
.scene-inner h2{font-family:var(--hf-display-font);font-size:82px;line-height:1.05;font-weight:700;letter-spacing:-.02em;max-width:1500px}
.scene-inner h3{font-family:var(--hf-display-font);font-size:56px;font-weight:600;color:var(--accent)}
.scene-inner p{font-size:38px;line-height:1.55;color:#dbe4f0;max-width:1400px}
.scene-inner .paragraph{margin:0}
.scene-inner ul,.scene-inner ol{margin:8px 0 0 56px}
.scene-inner li{font-size:40px;line-height:1.5;color:#e2e8f0;margin:14px 0}
.scene-inner li::marker{color:var(--accent)}
.scene-inner a{color:var(--accent);text-decoration:none}
.scene-inner code{font-family:var(--hf-mono-font);font-size:.9em;
  background:var(--card);border:1px solid var(--line);border-radius:8px;padding:2px 10px}
.scene-inner pre{font-family:var(--hf-mono-font);font-size:30px;line-height:1.5;
  background:var(--card);border:1px solid var(--line);border-radius:16px;padding:36px 44px;overflow:hidden;color:#e2e8f0}
.scene-inner img{max-width:900px;max-height:620px;border-radius:16px}
.scene-inner blockquote{border-left:8px solid var(--accent);padding-left:36px;
  font-size:52px;font-style:italic;color:#e2e8f0;max-width:1400px}
.hf-title-card-title{display:block;font-family:var(--hf-display-font);font-size:140px;font-weight:700;letter-spacing:-.03em;line-height:1}
.hf-title-card-subtitle{display:block;margin-top:28px;font-size:46px;color:var(--muted);font-weight:400}
.hf-data-chart{display:flex;flex-direction:column;gap:40px}
.hf-data-chart-title{font-family:var(--hf-display-font);font-size:56px;font-weight:600;color:var(--accent)}
.hf-data-chart-plot{display:flex;align-items:flex-end;gap:48px;height:460px}
.hf-data-chart-col{display:flex;flex-direction:column;justify-content:flex-end;align-items:center;height:100%;width:150px}
.hf-data-chart-bar{width:100%;max-height:calc(100% - 56px);margin-bottom:16px;
  background:linear-gradient(180deg,#f59e0b 0%,#b45309 100%);border-radius:14px 14px 0 0}
.hf-data-chart-label{font-size:30px;font-weight:600;color:#e2e8f0}
.hf-kinetic-captions{position:relative;height:100%}
.hf-kinetic-caption{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;
  font-family:var(--hf-display-font);font-size:84px;font-weight:700;text-align:center;letter-spacing:-.02em;padding:0 120px}
.hf-code-diff{display:flex;gap:40px;justify-content:center;flex-wrap:wrap}
.hf-code-diff pre{flex:1;min-width:640px}
.hf-kw{color:#f59e0b;font-weight:600}
/* HF-DEMO-QUALITY US-2 v1 — cover transitions (driven by the root timeline) */
.hf-transition{position:absolute;inset:0;z-index:50;pointer-events:none;overflow:hidden}
.hf-transition-grid{display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:repeat(3,1fr)}
.hf-transition-cell{transform-origin:center center}
.hf-shutter-top,.hf-shutter-bottom{position:absolute;left:0;width:100%;height:50%;background:var(--bg)}
.hf-shutter-top{top:0}
.hf-shutter-bottom{top:50%}
.hf-transition-fade{background:var(--bg);opacity:0}
/* HF-DEMO-QUALITY US-4 — studio chrome (timecode, marks, progress) */
.hf-chrome{position:absolute;inset:0;z-index:40;pointer-events:none}
.hf-chrome-marks span{position:absolute;width:34px;height:34px;border:2px solid rgba(248,250,252,.28)}
.hf-chrome-marks span:nth-child(1){left:34px;top:34px;border-right:0;border-bottom:0}
.hf-chrome-marks span:nth-child(2){right:34px;top:34px;border-left:0;border-bottom:0}
.hf-chrome-marks span:nth-child(3){left:34px;bottom:34px;border-right:0;border-top:0}
.hf-chrome-marks span:nth-child(4){right:34px;bottom:34px;border-left:0;border-top:0}
.hf-chrome-topbar{position:absolute;top:38px;left:88px;right:88px;display:flex;
  justify-content:space-between;align-items:center;padding:9px 14px;font-family:var(--hf-mono-font);
  font-size:19px;letter-spacing:.22em;color:#c9d1d4;background:rgba(9,12,14,.9);
  border:1px solid rgba(248,250,252,.14)}
.hf-chrome-timecode{color:var(--accent)}
.hf-chrome-progress{position:absolute;left:0;right:0;bottom:0;height:6px;z-index:60;
  pointer-events:none;background:rgba(248,250,252,.10)}
.hf-chrome-progress span{display:block;height:100%;width:100%;background:var(--accent);
  transform-origin:left center}
"""
    }
}
