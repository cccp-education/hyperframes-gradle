package hyperframes.scenarios

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * HF-CR3-3 — dedicated Cucumber suite for `cr3_real_render.feature`.
 *
 * Scoped via `@SelectClasspathResource` so the hermetic `cucumberTest` task
 * (scoped to the aggregate [CucumberRunner]) never runs the heavy real CLI
 * (pattern S-082). It is driven by the dedicated `cucumberTestCr3RealRender`
 * Gradle task, wired into the non-blocking `.github/workflows/real-render-hyperframes.yml`.
 *
 * The scenarios are tagged `@integration @real-render` and are skip-guarded
 * (`assumeTrue`): when Node.js / the `hyperframes` npm package / `ffprobe` are
 * unavailable the scenario is *skipped*, never a false success.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/cr3_real_render.feature")
@ConfigurationParameter(key = "cucumber.junit-platform.naming-strategy", value = "long")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "hyperframes.scenarios")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@real-render and not @wip")
class Cr3RealRenderCucumberRunner
