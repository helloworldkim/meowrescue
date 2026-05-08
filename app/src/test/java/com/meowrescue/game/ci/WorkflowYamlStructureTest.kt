package com.meowrescue.game.ci

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowYamlStructureTest {

    private val yaml: String by lazy {
        // Working directory in Gradle tests is app/; workflow is at repo root.
        val repoRoot = java.io.File(".").canonicalFile.parentFile
        java.io.File(repoRoot, ".github/workflows/layer-checks.yml").readText()
    }

    @Test fun `workflow name is Layer Boundaries`() {
        assertTrue("Expected 'name: Layer Boundaries'", "name: Layer Boundaries" in yaml)
    }

    @Test fun `triggers on push to main`() {
        assertTrue("Expected push trigger on main", yaml.contains("push") && yaml.contains("[main]"))
    }

    @Test fun `triggers on pull_request targeting main`() {
        assertTrue("Expected pull_request trigger", "pull_request" in yaml)
    }

    @Test fun `job runs on ubuntu-latest`() {
        assertTrue("Expected runs-on: ubuntu-latest", "runs-on: ubuntu-latest" in yaml)
    }

    @Test fun `checkout step uses actions checkout v4`() {
        assertTrue("Expected actions/checkout@v4", "actions/checkout@v4" in yaml)
    }

    @Test fun `script invocation step is present`() {
        assertTrue(
            "Expected 'bash tools/ci/check-layer-boundaries.sh'",
            "bash tools/ci/check-layer-boundaries.sh" in yaml
        )
    }

    @Test fun `header references ADR-0006`() {
        assertTrue("Expected ADR-0006 reference in workflow header", "ADR-0006" in yaml)
    }
}
