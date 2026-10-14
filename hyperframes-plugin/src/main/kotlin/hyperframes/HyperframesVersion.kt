package hyperframes

/**
 * S-022 (dogfooding) — the plugin's own artefact version.
 *
 * Read from the jar manifest `Implementation-Version` (set in `build.gradle.kts`),
 * so the N3 contract reports the *plugin* version, not the consumer project's
 * `version` (which is `unspecified` on a typical consumer build).
 *
 * Falls back to `"unspecified"` when the manifest attribute is absent (e.g. running
 * from a classes directory under TestKit).
 */
object HyperframesVersion {
    /** The plugin artefact version, or `"unspecified"` when unknown. */
    val value: String =
        HyperframesVersion::class.java.`package`?.implementationVersion ?: "unspecified"
}
