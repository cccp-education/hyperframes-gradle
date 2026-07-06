package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue

class N3ContractSteps(private val world: HyperframesWorld) {

    @Given("a hyperframes-gradle plugin is applied to a project")
    fun pluginApplied() {
        world.createProject()
    }

    @Given("the build directory contains a rendered MP4 file")
    fun mp4Exists() {
        world.writeMp4("test-video.mp4", ByteArray(2048))
    }

    @Given("the build directory contains an enriched HyperFrames index.html")
    fun htmlExists() {
        world.writeHtml(compositions = listOf("intro"), tracks = 1)
        world.writeAsciidoc("ma-formation.adoc")
    }

    @Given("the build directory contains no MP4 file")
    fun noMp4() {
        world.buildDir.listFiles()?.filter { it.extension == "mp4" }?.forEach { it.delete() }
    }

    @When("I run the collectHyperframesRetrieve task")
    fun runCollectTask() {
        world.runTask("collectHyperframesRetrieve")
    }

    @Then("the composite-context.json file exists")
    fun contextFileExists() {
        assertThat(world.compositeContextFile()).exists()
    }

    @Then("the composite-context.json file does not exist")
    fun contextFileDoesNotExist() {
        assertThat(world.compositeContextFile()).doesNotExist()
    }

    @Then("the task succeeds")
    fun taskSucceeds() {
        val r = world.buildResult
        assertThat(r).isNotNull
        assertThat(r!!.task(":collectHyperframesRetrieve")?.outcome?.toString())
            .contains("SUCCESS")
    }

    @Then("the JSON contains the field {string} with value {string}")
    fun jsonFieldEquals(path: String, expected: String) {
        val node = world.compositeContextJson().path(path)
        assertThat(node.isMissingNode).`as`("field $path present").isFalse()
        assertThat(node.asText()).isEqualTo(expected)
    }

    @Then("the JSON contains the field {string}")
    fun jsonFieldPresent(path: String) {
        val node = world.compositeContextJson().path(path)
        assertThat(node.isMissingNode).`as`("field $path present").isFalse()
    }

    @Then("the JSON output field {string} contains {string} with the MP4 filename")
    fun outputVideoField(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.isMissingNode).isFalse()
        assertThat(node.asText()).isEqualTo("test-video.mp4")
    }

    @Then("the JSON output field {string} contains {string} with the stage width")
    fun outputWidth(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asInt()).isEqualTo(1920)
    }

    @Then("the JSON output field {string} contains {string} with the stage height")
    fun outputHeight(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asInt()).isEqualTo(1080)
    }

    @Then("the JSON output field {string} contains {string} with the stage fps")
    fun outputFps(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asInt()).isEqualTo(30)
    }

    @Then("the JSON output field {string} contains {string} with the MP4 size")
    fun outputSize(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asLong()).isEqualTo(2048L)
    }

    @Then("the JSON output field {string} contains {string} with value {string}")
    fun outputFieldEquals(parent: String, child: String, expected: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.isMissingNode).isFalse()
        assertThat(node.asText()).isEqualTo(expected)
    }

    @Then("the JSON output field {string} contains {string} with the source filename")
    fun sourceAsciidoc(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asText()).isEqualTo("ma-formation.adoc")
    }

    @Then("the JSON output field {string} contains {string} with the detected composition ids")
    fun sourceCompositions(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.isArray).isTrue()
        assertThat(node.size()).isEqualTo(1)
        assertThat(node[0].asText()).isEqualTo("intro")
    }

    @Then("the JSON output field {string} contains {string} with the number of distinct tracks")
    fun sourceTracks(parent: String, child: String) {
        val node = world.compositeContextJson().path(parent).path(child)
        assertThat(node.asInt()).isEqualTo(1)
    }
}