package hyperframes

import java.io.File

/**
 * Test-only stubs shared by the functional tests (TestKit).
 */
object FunctionalStubs {
    /**
     * Writes a stub HyperFrames CLI that emulates `node <cli> render …`
     * (parses `--output`, writes a marker MP4, exits 0).
     *
     * Used as both `cliScript` and `nodeExecutable` so the render is hermetic:
     * no Node.js, no Chromium, no ffmpeg.
     */
    fun writeStubHyperframesCli(projectDir: File): File {
        val script = projectDir.resolve("stub-hyperframes-cli.sh")
        val dollar = "$"
        script.writeText(
            """
            #!/bin/bash
            OUTPUT_FILE=""
            while [[ $dollar# -gt 0 ]]; do
                case ${dollar}1 in
                    --output) OUTPUT_FILE="${dollar}2"; shift 2 ;;
                    *) shift ;;
                esac
            done
            if [[ -n "${dollar}OUTPUT_FILE" ]]; then
                echo "MOCK-MP4" > "${dollar}OUTPUT_FILE"
            fi
            echo "Mock HyperFrames CLI: render complete"
            exit 0
            """.trimIndent(),
        )
        script.setExecutable(true)
        return script
    }
}
