package com.meowrescue.game.threading

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GlobalScopeBanTest {

    @Test
    fun `no source file imports or uses GlobalScope`() {
        val root = Paths.get("src/main/java")
        val forbidden = listOf(
            "import kotlinx.coroutines.GlobalScope",
            "GlobalScope.launch(",
            "GlobalScope.async("
        )
        val offenders = Files.walk(root)
            .filter { it.toString().endsWith(".kt") }
            .filter { path ->
                val text = path.toFile().readText()
                "// allowed" !in text && forbidden.any { it in text }
            }
            .map { root.relativize(it).toString() }
            .sorted()
            .toList()
        assertTrue(
            "GlobalScope found in:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }
}
