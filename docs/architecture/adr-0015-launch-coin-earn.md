# ADR-0015: Cat Launch Coin Earn System

## Status
Accepted (2026-05-08)

## Date
2026-05-08

## Engine Compatibility

| Field | Value |
|-------|-------|
| **Engine** | Native Android (Kotlin) — no game engine |
| **Domain** | Economy / Cat Launch |
| **Knowledge Risk** | LOW — standard arithmetic + Room addCoins call |
| **References Consulted** | `design/gdd/economy-system.md`, `design/gdd/cat-launch-system.md`, ADR-0003 |
| **Post-Cutoff APIs Used** | None |
| **Verification Required** | None |

## ADR Dependencies

| Field | Value |
|-------|-------|
| **Depends On** | ADR-0003 (coin atomicity — addCoins is the only write path), ADR-0008 (JBox2D — stage completion event comes from physics world) |
| **Enables** | Cat Launch economy progression; Cosmetic purchase budget for players |
| **Blocks** | Any feature that reads `totalCoinsEarned` from Cat Launch sessions |
| **GDD Requirements Addressed** | TR-economy-006 |

## Context

### Problem Statement

Cat Launch stages complete with a score. That score must be translated into a
coin reward and written atomically to the player's balance. The conversion rule
must account for stage difficulty and reward first-time completions without
inflating long-term income.

### Requirements (TR-economy-006)

- Coin reward uses a difficulty multiplier:
  Easy × 1.0, Normal × 1.5, Hard × 2.0 (applied to base reward).
- First-clear bonus: +50 coins, awarded once per stage, capped at the first 50
  stages (stages 1–50 only).
- Base reward is defined by the economy GDD tuning table; the multiplier is
  integer-rounded (`(base * multiplier).toInt()`).
- All coin writes go through `EconomyManager.addCoins()` (ADR-0003 atomicity).

## Decision

### Earn Formula

```
reward = (baseReward * difficultyMultiplier).toInt()
if (isFirstClear && stageNumber <= 50) reward += FIRST_CLEAR_BONUS   // 50
EconomyManager.addCoins(reward)
```

Constants (defined in `EconomyManager` companion object or economy constants file):

| Constant | Value |
|----------|-------|
| `LAUNCH_BASE_REWARD` | Defined per-stage in GDD tuning table |
| `LAUNCH_MULTIPLIER_EASY` | 1.0f |
| `LAUNCH_MULTIPLIER_NORMAL` | 1.5f |
| `LAUNCH_MULTIPLIER_HARD` | 2.0f |
| `FIRST_CLEAR_BONUS` | 50 |
| `FIRST_CLEAR_STAGE_CAP` | 50 |

### Trigger Point

Coins are awarded in the stage-complete callback, after the physics world has
confirmed the win condition. The call site is the Cat Launch presentation layer
(`LaunchGameActivity` or equivalent), which invokes `EconomyManager.addCoins()`
via `LifecycleScope` on `Dispatchers.IO` (ADR-0004 threading contract).

### First-Clear Tracking

`isFirstClear` is determined by checking `GameRepository.getLaunchProgress(stageId)`
before awarding. The repository marks the stage as cleared after the coin write,
ensuring the bonus is never awarded twice even on retry.

## Consequences

**Positive**
- Difficulty creates real progression incentive: Hard stages pay 2× baseline.
- First-clear bonus front-loads early game reward without inflating late-game.
- All writes are atomic via ADR-0003; no partial-award risk.

**Negative / Trade-offs**
- Stage 51+ first-clears earn no bonus — late-game players discover this on a
  Hard clear with no extra coins. Acceptable per economy GDD design intent.
- Multiplier is applied as a float-to-int truncation; fractional cents are lost
  (by design — whole-coin economy).
