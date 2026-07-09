package hyperframes.template

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD unit tests for [DataChartTemplate] — HF-5c.
 *
 * Baby-step (DDD red -> green): the value object renders a HyperFrames
 * composition with animated bars that rise from zero to their target
 * height. Bars are proportional to their value relative to the max.
 * The GSAP timeline uses a stagger so bars rise one after another.
 */
class DataChartTemplateTest {

    @Test
    fun `render produces a hyperframes-composition div with data-composition-id`() {
        val template = DataChartTemplate(
            id = "sales-chart",
            title = "Quarterly sales",
            dataPoints = listOf(DataPoint("Q1", 30.0), DataPoint("Q2", 50.0))
        )

        val html = template.render()

        assertTrue(html.contains("class=\"sect1 hyperframes-composition\""), "should expose composition role")
        assertContains(html, "data-composition-id=\"sales-chart\"")
    }

    @Test
    fun `render includes a hyperframes-track with start zero and configured duration`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("A", 10.0)),
            durationSeconds = 6.0
        )

        val html = template.render()

        assertContains(html, "hyperframes-track")
        assertContains(html, "data-start=\"0\"")
        assertContains(html, "data-duration=\"6\"")
    }

    @Test
    fun `default duration is four seconds`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("A", 10.0))
        )

        assertEquals(4.0, template.durationSeconds)
    }

    @Test
    fun `render contains a chart container div`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("A", 10.0))
        )

        val html = template.render()

        assertContains(html, "class=\"hf-data-chart\"")
    }

    @Test
    fun `render contains one bar div per data point with label and value`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("Q1", 30.0), DataPoint("Q2", 50.0), DataPoint("Q3", 75.0))
        )

        val html = template.render()

        assertContains(html, "data-label=\"Q1\"")
        assertContains(html, "data-value=\"30.0\"")
        assertContains(html, "data-label=\"Q2\"")
        assertContains(html, "data-value=\"50.0\"")
        assertContains(html, "data-label=\"Q3\"")
        assertContains(html, "data-value=\"75.0\"")
    }

    @Test
    fun `bar height is proportional to its value relative to the max`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("Small", 25.0), DataPoint("Max", 100.0))
        )

        val html = template.render()

        assertContains(html, "style=\"height:25%\"")
        assertContains(html, "style=\"height:100%\"")
    }

    @Test
    fun `render includes a GSAP timeline that animates bars from scaleY zero`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("A", 10.0))
        )

        val html = template.render()

        assertContains(html, "gsap.timeline")
        assertContains(html, "window.__timelines")
        assertContains(html, "scaleY: 0")
        assertContains(html, ".hf-data-chart-bar")
    }

    @Test
    fun `bars are staggered so they rise one after another`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("A", 10.0), DataPoint("B", 20.0))
        )

        val html = template.render()

        assertContains(html, "stagger")
    }

    @Test
    fun `render includes the chart title`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Quarterly sales",
            dataPoints = listOf(DataPoint("A", 10.0))
        )

        val html = template.render()

        assertContains(html, "Quarterly sales")
        assertContains(html, "hf-data-chart-title")
    }

    @Test
    fun `render includes each label as visible text under its bar`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = listOf(DataPoint("Q1", 30.0))
        )

        val html = template.render()

        assertContains(html, "class=\"hf-data-chart-label\"")
        assertContains(html, ">Q1<")
    }

    @Test
    fun `empty data points renders an empty chart container without bars`() {
        val template = DataChartTemplate(
            id = "demo",
            title = "Demo",
            dataPoints = emptyList()
        )

        val html = template.render()

        assertContains(html, "class=\"hf-data-chart\"")
        assertFalse(html.contains("hf-data-chart-bar"), "empty chart must not contain any bar")
    }

    @Test
    fun `DataPoint rejects a blank label`() {
        assertThrows<IllegalArgumentException> {
            DataPoint("  ", 10.0)
        }
    }

    @Test
    fun `DataPoint rejects a negative value`() {
        assertThrows<IllegalArgumentException> {
            DataPoint("Q1", -5.0)
        }
    }
}