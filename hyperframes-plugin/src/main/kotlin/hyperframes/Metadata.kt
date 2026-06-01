package hyperframes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.time.Instant

/**
 * Metadata descriptor for hyperframes pipeline artifacts.
 *
 * Carries provenance information (source borough, model version, dependencies)
 * attached to every generated output — HTML, MP4 videos.
 *
 * @property source originating borough name (e.g. "watts")
 * @property type pipeline stage type (e.g. "pipeline", "render")
 * @property sessions number of processing sessions
 * @property generatedAt ISO-8601 timestamp of generation
 * @property model embedding model name used (e.g. "onnx-local")
 * @property version artifact version string
 * @property dependencies list of upstream boroughs this artifact depends on
 */
data class Metadata(
    val source: String,
    val type: String,
    val sessions: Int,
    val generatedAt: String,
    val model: String,
    val version: String,
    val dependencies: List<String>
) {
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Writes metadata as a pretty-printed JSON file to the given directory.
         *
         * @param dir target directory (created if missing)
         * @param metadata the metadata to persist
         * @return the created [File] handle
         */
        fun writeTo(dir: File, metadata: Metadata): File {
            dir.mkdirs()
            val file = File(dir, "metadata.json")
            file.writeText(mapper.writeValueAsString(metadata))
            return file
        }

        /**
         * Creates a standard Metadata instance for the Watts borough.
         *
         * @param type pipeline stage type (default: "pipeline")
         * @param model embedding model name (default: "onnx-local")
         * @param sessions number of processing sessions
         * @param dependencies upstream borough dependencies (default: ["brooklyn"])
         * @return a new [Metadata] with source set to "watts"
         */
        fun forWatts(
            type: String = "pipeline",
            model: String = "onnx-local",
            sessions: Int = 0,
            dependencies: List<String> = listOf("brooklyn")
        ): Metadata = Metadata(
            source = "watts",
            type = type,
            sessions = sessions,
            generatedAt = Instant.now().toString(),
            model = model,
            version = "1.0",
            dependencies = dependencies
        )
    }
}