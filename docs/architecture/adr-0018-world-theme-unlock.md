# ADR-0018: World Theme Mapping

## Status
Accepted (2026-05-08)

## Date
2026-05-08

## Engine Compatibility

| Field | Value |
|-------|-------|
| **Engine** | Native Android (Kotlin) — no game engine |
| **Domain** | Progression / Rendering |
| **Knowledge Risk** | LOW — pure integer arithmetic, no platform APIs |
| **References Consulted** | `design/gdd/progression-system.md`, ADR-0009, ADR-0010 |
| **Post-Cutoff APIs Used** | None |
| **Verification Required** | None |

## ADR Dependencies

| Field | Value |
|-------|-------|
| **Depends On** | ADR-0010 (Presentation surfaces — theme injected via `setTheme(spec)`) |
| **Enables** | T-4-6 (Core Progression 007 implementation) |
| **Blocks** | story-007-world-theme-unlock.md |
| **GDD Requirements Addressed** | TR-prog-012 |

## Context

### Problem Statement

Seven distinct world themes (Garden → Beach → Forest → Snow → Volcano → Canyon →
Space) must be applied to both Puzzle and Cat Launch surfaces according to stage
progress. Without a canonical formula, each screen risks computing a different
theme for the same stage.

### Requirements (TR-prog-012)

- 7 themes, each spanning 30 consecutive stages (stages 1–200).
- Theme is determined purely by `stageId` — no explicit unlock gate.
- Endless mode cycles through all 7 themes via `stageId % 7`.
- `forStage(0)` and `forStage(negative)` are invalid inputs.
- Theme is injected once per stage load; it does not change mid-stage.

## Decision

### WorldTheme Enum

```kotlin
enum class WorldTheme(val index: Int) {
    GARDEN(0), BEACH(1), FOREST(2), SNOW(3),
    VOLCANO(4), CANYON(5), SPACE(6)
}
```

### Mapping Formula

```kotlin
object WorldTheme {
    fun forStage(stageId: Int): WorldTheme {
        require(stageId >= 1) { "stageId must be >= 1, got $stageId" }
        val index = ((stageId - 1) / 30).coerceAtMost(6)
        return entries[index]
    }

    fun forEndlessStage(generatedStageId: Int): WorldTheme {
        require(generatedStageId >= 1) { "stageId must be >= 1" }
        return entries[generatedStageId % 7]
    }
}
```

Boundary values (GDD acceptance criterion 17):

| Stage range | Index | Theme |
|-------------|-------|-------|
| 1–30 | 0 | GARDEN |
| 31–60 | 1 | BEACH |
| 61–90 | 2 | FOREST |
| 91–120 | 3 | SNOW |
| 121–150 | 4 | VOLCANO |
| 151–180 | 5 | CANYON |
| 181–200 | 6 | SPACE |

### Injection Point

Per ADR-0010, theme is injected by the Activity on stage load:

```kotlin
// In PuzzleActivity.onCreate / LaunchGameActivity.onCreate
val theme = WorldTheme.forStage(stageId)   // or forEndlessStage for Endless
puzzleView.setTheme(WorldThemeSpec.from(theme))
```

`WorldThemeSpec` is a data class owned by Presentation that maps a `WorldTheme`
to concrete drawable resources. `WorldTheme` itself lives in Core and has no
Android imports.

### Cosmetic Override

A player may purchase a theme override via `ThemePurchaser` (ADR-0009).
The override is stored in SharedPreferences as `selected_theme_id`.
`ThemeResolver.forStage(stageId, override)` applies the override when non-null:

```kotlin
fun forStage(stageId: Int, override: String?): WorldTheme =
    if (override != null) WorldTheme.valueOf(override)
    else WorldTheme.forStage(stageId)
```

The base `WorldTheme.forStage()` is always computed — the override only
replaces the result at the resolver tier, keeping the mapping formula testable
in isolation.

## Consequences

**Positive**
- Pure function with no I/O — boundary values are exhaustively unit-testable.
- `coerceAtMost(6)` makes stages 181–200 all SPACE without special-casing.
- Endless cycling is a single `% 7` — no theme table lookup needed.

**Negative / Trade-offs**
- Stage 200 is the only SPACE stage in Puzzle mode; players clearing 181–199
  see the same theme until stage 200. Acceptable per GDD design intent.
- `forEndlessStage(7)` = index 0 (GARDEN) — Endless wraps back to Garden every
  7 stages. Intentional.

## GDD Requirements Addressed

| GDD Document | System | Requirement | How This ADR Satisfies It |
|-------------|--------|-------------|--------------------------|
| `design/gdd/progression-system.md` | Progression | TR-prog-012 — "World theme mapping — 7 themes, 30-stage boundaries, Endless cycles via stage % 7" | `WorldTheme.forStage()` formula `(stageId - 1) / 30` clamped to [0,6]; `forEndlessStage()` via `% 7`; `ThemeResolver` applies cosmetic override |
