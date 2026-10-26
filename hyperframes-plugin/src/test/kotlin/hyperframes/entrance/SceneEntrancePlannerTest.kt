package hyperframes.entrance

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-5 — pure entrance planner contract.
 */
class SceneEntrancePlannerTest {

    @Test
    fun `disabled planner emits no script`() {
        assertEquals("", SceneEntrancePlanner.setupScript("scene", EntranceConfig()))
        assertEquals("", SceneEntrancePlanner.tweenScript("scene", EntranceConfig()))
    }

    @Test
    fun `setup hides the scene atoms at load time`() {
        val script = SceneEntrancePlanner.setupScript("intro", EntranceConfig(enabled = true))

        assertContains(script, """var items = gsap.utils.toArray("""")
        assertContains(script, "#intro .scene-inner h2")
        assertContains(script, "#intro .scene-inner pre")
        assertContains(script, "gsap.set(items, { opacity: 0, y: 30 });")
    }

    @Test
    fun `selector is scoped to one scene and covers the content atoms`() {
        val selector = SceneEntrancePlanner.selector("scene-2")

        ATOM_LIST.forEach { atom ->
            assertContains(selector, "#scene-2 .scene-inner $atom")
        }
        assertFalse(selector.contains("#scene-1"))
    }

    @Test
    fun `tween reveals the atoms with a deterministic stagger`() {
        val script = SceneEntrancePlanner.tweenScript(
            "intro",
            EntranceConfig(enabled = true, staggerSeconds = 0.08, durationSeconds = 0.4, offsetSeconds = 0.2),
        )

        assertContains(script, "opacity: 1, y: 0")
        assertContains(script, "duration: 0.40")
        assertContains(script, "stagger: 0.08")
        assertContains(script, "}, 0.20);")
    }

    @Test
    fun `config validates its timing values`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            EntranceConfig(staggerSeconds = -0.1)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            EntranceConfig(durationSeconds = 0.0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            EntranceConfig(offsetSeconds = -1.0)
        }
    }

    private companion object {
        val ATOM_LIST = listOf("h1", "h2", "p", "li", "pre", "blockquote", "img", "table", ".hf-title-card-title")
    }
}
