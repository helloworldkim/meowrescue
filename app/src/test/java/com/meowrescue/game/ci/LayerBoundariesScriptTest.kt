package com.meowrescue.game.ci

import org.junit.Assert.assertEquals
import org.junit.Test

class LayerBoundariesScriptTest {

    @Test
    fun `layer boundary self-test script exits 0`() {
        // Gradle unit test working directory is the module dir (app/).
        // Navigate up to repo root so script-internal paths (app/src/...) resolve.
        val repoRoot = java.io.File(".").canonicalFile.parentFile
        val scriptPath = "tools/ci/test-check-layer-boundaries.sh"
        // On Windows, "bash" may resolve to the WSL relay instead of Git Bash.
        // Search common Git Bash locations; fall back to "bash" on Unix CI.
        val bash = listOf(
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files\\Git\\usr\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
        ).firstOrNull { java.io.File(it).exists() } ?: "bash"
        val process = ProcessBuilder(bash, scriptPath)
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertEquals(
            "Layer boundary self-test failed (exit $exitCode):\n$output",
            0,
            exitCode
        )
    }
}
