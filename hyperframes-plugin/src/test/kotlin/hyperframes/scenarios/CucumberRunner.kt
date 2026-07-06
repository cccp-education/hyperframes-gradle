package hyperframes.scenarios

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
    key = "cucumber.junit-platform.naming-strategy",
    value = "long"
)
@ConfigurationParameter(
    key = "cucumber.glue",
    value = "hyperframes.scenarios"
)
class CucumberRunner