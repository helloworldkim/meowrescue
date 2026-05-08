# ADR-0017: Sequential Stage Unlock

## Status
Accepted (2026-05-08)

## Date
2026-05-08

## Engine Compatibility

| Field | Value |
|-------|-------|
| **Engine** | Native Android (Kotlin) — no game engine |
| **Domain** | Progression |
| **Knowledge Risk** | LOW — Room query + integer comparison |
| **References Consulted** | `design/gdd/progression-system.md`, ADR-0001, ADR-0011 |
| **Post-Cutoff APIs Used** | None |
| **Verification Required** | None |

## ADR Dependencies

| Field | Value |
|-------|-------|
| **Depends On** | ADR-0001 (Repository Pattern — unlock state read via GameRepository), ADR-0011 (ProgressionManager.completeStage — writes maxCompletedLevel) |
| **Enables** | T-4-5 (Core Progression 006 implementation) |
| **Blocks** | story-006-first-clear-stage-unlock.md |
| **GDD Requirements Addressed** | TR-prog-004 |

## Context

### Problem Statement

Stage select must show which stages are playable and which are locked.
Without a defined rule, each screen could implement its own unlock logic,
causing inconsistency and making it possible to access stages out of order.

### Requirements (TR-prog-004)

- Stages unlock sequentially: completing stage N unlocks stage N+1.
- Stage 1 is always unlocked (no prior clear required).
- Unlock state persists across sessions.
- Replaying an already-cleared stage does not change unlock state.
- Stage unlock is determined by `maxCompletedLevel` — the highest stage
  ever cleared by the player.

## Decision

### Unlock Predicate

```kotlin
fun isStageUnlocked(stageId: Int, maxCompletedLevel: Int): Boolean =
    stageId == 1 || stageId <= maxCompletedLevel + 1
```

`maxCompletedLevel` is read from `GameRepository.getMaxCompletedLevel(): Int`
(returns 0 on fresh install). The unlock predicate is a pure function — no
side effects, no coroutines required at call site.

### Write Path

`maxCompletedLevel` is updated inside `ProgressionManager.completeStage(result)`
per ADR-0011 ordering invariant:

```
star reward + first-clear bonus
→ checkAchievements
→ maxCompletedLevel = max(current, clearedStage)   ← written here
→ cat unlock check
```

The `max(current, clearedStage)` guard means replays never decrease
`maxCompletedLevel`, and replaying an old stage does not unlock new stages
beyond the player's existing high-water mark.

### Read Path

Stage select screen (Presentation layer) calls:

```kotlin
// In ViewModel or Activity, on Dispatchers.IO via GameRepository
val maxCleared = repository.getMaxCompletedLevel()
// Per-cell in stage select:
val unlocked = isStageUnlocked(stageId, maxCleared)
```

`getMaxCompletedLevel()` is a single Room `SELECT maxCompletedLevel FROM
player_stats WHERE id = 1` — negligible cost.

### Constants

| Constant | Value | Location |
|----------|-------|----------|
| `STAGE_1_ID` | 1 | `PuzzleConstants.kt` |
| Fresh-install `maxCompletedLevel` | 0 | Room default via migration |

## Consequences

**Positive**
- Unlock logic is a pure function — trivially unit-testable with no DB dependency.
- Single write path (`completeStage`) ensures unlock state is always consistent
  with clear history.
- Stage select reads one integer and applies the predicate locally — no per-stage
  DB queries.

**Negative / Trade-offs**
- No skip-ahead unlock mechanism exists (e.g., admin unlock for QA). If needed
  in future, a `debugUnlockAll()` override in `GameRepository` can bypass the
  predicate — do not add it now.
- Stage 200 has no stage 201 to unlock. The UI must guard against rendering a
  "stage 201 locked" cell. Stage select should render only stages 1–200.

## GDD Requirements Addressed

| GDD Document | System | Requirement | How This ADR Satisfies It |
|-------------|--------|-------------|--------------------------|
| `design/gdd/progression-system.md` | Progression | TR-prog-004 — "Sequential stage unlock — completing stage N unlocks stage N+1" | `isStageUnlocked(stageId, maxCompletedLevel)` predicate; `maxCompletedLevel` written by `completeStage()` via ADR-0011 |
