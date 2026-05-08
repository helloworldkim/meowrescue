# Story 002: `LifecycleScope` ownership + `GlobalScope` ban

> **Epic**: foundation-threading
> **Status**: Complete
> **Layer**: Foundation
> **Type**: Logic
> **Manifest Version**: 2026-04-18

## Context

**GDD**: cross-cutting (no single GDD — coroutine lifecycle policy)
**Requirement**: Policy story (no direct TR-ID). Derived from ADR-0004 § Decision rule: "All Activity-launched coroutines use `lifecycleScope`, never `GlobalScope`." Enables correct cancellation for every downstream epic that launches coroutines from Activities or ViewModels.

**ADR Governing Implementation**: ADR-0004 (Threading & Coroutine Model)
**ADR Decision Summary**: Activity-scoped coroutines use `lifecycleScope`; ViewModel-scoped coroutines use `viewModelScope`; `GlobalScope` is forbidden project-wide because it survives Activity destruction and leaks state. Validation Criterion #5 requires cancellation proof.

**Engine**: Native Android (Kotlin) · `androidx.lifecycle` 2.6+ | **Risk**: LOW
**Engine Notes**: `lifecycleScope` + `repeatOnLifecycle(STARTED)` are stable since `lifecycle-runtime-ktx` 2.4 (2021). No post-cutoff APIs.

**Control Manifest Rules (Foundation — threading)**:
- Required: Activity coroutines use `lifecycleScope`; ViewModel coroutines use `viewModelScope`.
- Forbidden: `GlobalScope.launch(...)` anywhere in `src/` (no exception).
- Required (consumed by layer-boundaries epic): CI lint rule that fails on `GlobalScope` import.

---

## Acceptance Criteria

*From ADR-0004 § Decision + Validation Criterion #5:*

- [ ] `CODING_STANDARDS.md` section or `DispatcherPolicy.kt` KDoc explicitly lists `GlobalScope` as forbidden and names `lifecycleScope` / `viewModelScope` as the sanctioned alternatives.
- [ ] A unit test `GlobalScopeBanTest` asserts zero occurrences of `import kotlinx.coroutines.GlobalScope` or `GlobalScope.launch(` or `GlobalScope.async(` in `src/` (excluding generated code and test sources). Implemented by walking the source tree + regex match; fails with the offending file list if any match.
- [ ] An integration-style test `LifecycleScopeCancellationTest` launches a coroutine from a fake Activity's `lifecycleScope` that suspends indefinitely (`delay(Long.MAX_VALUE)`), destroys the Activity, and asserts the coroutine's `Job` is cancelled within 100ms. Uses `Robolectric` or `androidx.test` lifecycle fake.
- [ ] KDoc example snippet in `DispatcherPolicy.kt` shows the canonical Activity coroutine launch pattern:
  ```kotlin
  lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
          viewModel.flow.collect { ... }
      }
  }
  ```

---

## Implementation Notes

*From ADR-0004 § Decision + Project coding standards:*

```kotlin
// Sanctioned
class PuzzleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                economy.coins.collect { coinChip.setBalance(it) }
            }
        }
    }
}

// Forbidden — detected by GlobalScopeBanTest
class BadActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        GlobalScope.launch { /* leaks beyond Activity */ }  // ❌
    }
}
```

`GlobalScopeBanTest` implementation hint:

```kotlin
@Test fun `no source file imports or uses GlobalScope`() {
    val offenders = Files.walk(Paths.get("src/main"))
        .filter { it.toString().endsWith(".kt") }
        .filter { path ->
            val text = Files.readString(path)
            "GlobalScope" in text && "// allowed" !in text
        }
        .toList()
    assertTrue(offenders.isEmpty(),
        "GlobalScope found in: ${offenders.joinToString("\n")}")
}
```

- Keep the "allowed" escape hatch string in case a future build-script or instrumentation requires it; default project answer is still "don't use GlobalScope".
- Cancellation test leverages `ActivityScenario.moveToState(DESTROYED)` or equivalent Robolectric lifecycle helper. The test asserts the job reaches `isCancelled == true` inside a timeout coroutine.

---

## Out of Scope

*Handled by neighbouring stories — do not implement here:*

- Story 001: `DispatcherPolicy` object and `assertDispatcher` helper
- Story 003: Render thread template (`lifecycleScope` does not apply to manual threads)
- `foundation-layer-boundaries` epic: Gradle-task-level CI lint rule (this story provides a unit-test-level check; the Gradle lint rule is a harder enforcement layer added by the layer-boundaries epic)

---

## QA Test Cases

**AC-1 — Convention documented**
  - Setup: Open `DispatcherPolicy.kt` KDoc.
  - Verify: `lifecycleScope` and `viewModelScope` are named; `GlobalScope` is explicitly marked forbidden with a one-line reason.
  - Pass condition: All three scope names present; forbidden reason given.

**AC-2 — Zero `GlobalScope` usages in `src/main/`**
  - Given: The full Kotlin source tree under `src/main/`.
  - When: `GlobalScopeBanTest` walks every `.kt` file.
  - Then: No file (excluding `// allowed` escape-hatch) contains `GlobalScope`.
  - Edge cases: Test also scans for `import kotlinx.coroutines.GlobalScope` separately (case-sensitive); fails with an explicit file:line list.

**AC-3 — `lifecycleScope` cancels on Activity destroy**
  - Given: A fake Activity with a coroutine launched from `lifecycleScope { delay(Long.MAX_VALUE) }`.
  - When: Activity is moved to `DESTROYED` state.
  - Then: The coroutine's `Job.isCancelled` is `true` within 100ms.
  - Edge cases: Repeat with a `repeatOnLifecycle(STARTED)` wrapper — job must still cancel on DESTROYED.

**AC-4 — KDoc example present**
  - Setup: Open `DispatcherPolicy.kt`.
  - Verify: At least one example snippet showing `lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }`.
  - Pass condition: Snippet present and matches the canonical pattern.

---

## Test Evidence

**Story Type**: Logic (enforcement test + lifecycle behaviour test)
**Required evidence**:
- `tests/unit/threading/global_scope_ban_test.kt` — source-tree walker. Must pass.
- `tests/integration/threading/lifecycle_scope_cancellation_test.kt` — Robolectric-based Activity lifecycle proof. Must pass.

**Status**: [ ] Not yet created

---

## Dependencies

- Depends on: Story 001 (documentation lives in `DispatcherPolicy.kt` KDoc).
- Unlocks: Every Presentation-layer story that launches coroutines from an Activity.
