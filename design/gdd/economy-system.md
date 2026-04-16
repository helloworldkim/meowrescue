---
status: reverse-documented
source: app/src/main/java/com/meowrescue/game/data/
date: 2026-04-15
verified-by: User
---

# Economy System

> **Note**: This document was reverse-engineered from the existing implementation.
> It captures current behavior and clarified design intent. The economy is
> **intentionally incomplete** — additional coin sinks are planned beyond the
> current power-up system.

---

## A. Overview

The Economy System manages the in-game coin currency. Coins are earned through
puzzle clears, Cat Launch clears, achievement unlocks, and endless mode. The
primary spending mechanism is power-ups in Puzzle mode. The system is designed
for expansion — additional sinks (cosmetics, cat upgrades, etc.) are planned.
A `player_stats` table tracks both current balance and lifetime earnings.

---

## B. Player Fantasy

Coins serve as a **reward signal** and **strategic resource**. Players accumulate
coins as a visible measure of their progression, then spend them tactically on
power-ups when stuck on difficult puzzles. The economy should feel generous enough
to never block progress but scarce enough that power-up usage requires thought.
Future sinks will add long-term spending goals.

---

## C. Detailed Rules

### Coin Sources

#### Puzzle Stage Clear

| Star Rating | Coins Earned |
|-------------|-------------|
| 3★ | 30 |
| 2★ | 20 |
| 1★ | 10 |

**First-clear bonus**: +50 coins on the first completion of any stage (not awarded on replay).

Total per first clear: 60-80 coins. Replay: 10-30 coins.

#### Endless Mode Clear

Same star-based rewards as Puzzle (10/20/30). No first-clear bonus (every clear is "new").
**Daily coin cap**: Maximum **150 coins per day** from Endless mode. Resets at midnight
(local time). After the cap, play continues but coins are not awarded. Stars, endlessCount,
and endlessBest continue to track normally.

#### Achievement Rewards

30 achievements with rewards ranging from 10 to 500 coins.

| Tier | Typical Reward | Examples |
|------|---------------|----------|
| Easy | 10-20 coins | powerup_1, coins_100, clear_1 |
| Medium | 30-50 coins | clear_10, star3_10, launch_1cat, cat_3 |
| Hard | 80-150 coins | clear_150, star3_100, speed_5s |
| Epic | 200-500 coins | clear_200, cat_all, star3_200 |

Total achievable achievement coins: **2,180** (sum of all 30 achievement rewards).

#### Cat Launch Stage Clear

Cat Launch clears award coins at the same star-based rates as Puzzle mode:

| Star Rating | Coins Earned |
|-------------|-------------|
| 3★ | 30 |
| 2★ | 20 |
| 1★ | 10 |

**First-clear bonus**: +50 coins on first completion of any Launch stage (parity with Puzzle).

> **Implemented** (2026-04-15): `GameRepository.saveLaunchProgress()` calls
> `addCoins(starCoins + firstClearBonus)` with `require(stars in 1..3)` guard.

### Coin Sinks

#### Power-Ups (Puzzle Mode)

| Power-Up | Cost | Effect |
|----------|------|--------|
| Magnet | 30 coins | Highlights all movable blocks (toggle on/off) |
| Ice | 40 coins | Removes one random non-cat block |
| Shuffle | 50 coins | Randomly repositions non-cat blocks |

- Costs are deducted **before** the effect is applied.
- If Ice fails to find a removable block, the cost is **refunded** via `refundCoins()`.
- `refundCoins()` adds coins without inflating `totalCoinsEarned`.
- Each power-up use increments `powerup_use_count` (SharedPreferences).

#### Planned Sinks (Not Yet Implemented)

The user has confirmed additional coin sinks are planned. Potential categories:
- **Cosmetics**: Cat skins, trail effects, grid themes
- **Cat upgrades**: Enhanced abilities in Cat Launch mode
- **Puzzle modifiers**: Extra hints, time bonuses, undo insurance
- **Collection**: Purchasable items in the Collection screen

> These are not yet designed — use `/design-system economy-expansion` when ready.

### Storage

#### player_stats Table

| Column | Type | Default | Description |
|--------|------|---------|-------------|
| id | INT (PK) | 1 | Singleton row |
| coins | INT | 0 | Current balance |
| totalCoinsEarned | INT | 0 | Lifetime earnings (never decremented by spending) |

- `addCoins(amount)`: Increases both `coins` and `totalCoinsEarned`.
- `spendCoins(amount)`: Decreases `coins` only. Returns 0 if balance insufficient.
- `refundCoins(amount)`: Increases `coins` only (does NOT touch `totalCoinsEarned`).

### Display

- Current coin count is shown in the puzzle HUD (`puzzleView.displayCoins`).
- Updated after each purchase and each clear.

---

## D. Formulas

### Puzzle Earnings Per Stage

```
firstClearCoins(stars) = starCoins(stars) + 50
replayCoins(stars) = starCoins(stars)

where starCoins = { 3: 30, 2: 20, 1: 10 }
```

### Cat Launch Earnings Per Stage

```
launchFirstClearCoins(stars) = starCoins(stars) + 50
launchReplayCoins(stars) = starCoins(stars)

where starCoins = { 3: 30, 2: 20, 1: 10 }
```

### Maximum Possible Coins (Full Completion — Best Case, All 3★)

```
puzzleFirstClears = 200 * 30 (star coins) + 200 * 50 (first-clear) = 16,000
launchIncome = varies (procedural, uncapped)
achievementRewards = 2,180
totalMaxCoins ≈ 18,180 (puzzle + achievements, excluding replays, endless, and Launch)
```

Note: `avg(30)` in the original was misleading — 30 is the 3★ maximum, not the average.
A realistic average across mixed star ratings is ~20, yielding ~14,180 for puzzle alone.

### Economy Balance Snapshot

```
Income (first 200 puzzle stages, best case):
  - 200 * 30 star coins (all 3★) = 6,000
  - 200 * 50 first-clear bonus = 10,000
  - Achievement rewards = 2,180
  Puzzle subtotal: ~18,180 coins

Cat Launch income (capped at 50 unique first-clears):
  - 50 * 30 star coins (Easy, all 3★) = 1,500
  - 50 * 50 first-clear bonus = 2,500
  Launch first-clear subtotal: 4,000 coins
  Additional replays (uncapped, no bonus): ~1,000 est.
  Launch subtotal: ~5,000 coins
  Note: Difficulty multiplier (Easy ×1.0, Normal ×1.5, Hard ×2.0) can increase
  star coins but first-clear bonus is flat 50 regardless of difficulty.

Current sinks:
  - Power-up usage: 30-50 per use
  - Estimated: 3 uses per stuck stage, ~20 stuck stages: ~2,400 coins

Surplus: ~23,180 coins (puzzle+launch+achievements) vs ~2,400 spending
= 9.7x income-to-sink ratio (pre-expansion)
```

> **Balance Concern — MITIGATED**: Pre-expansion surplus is ~9.7x (power-ups only).
> Economy Expansion (economy-expansion.md) adds 12,950 coins of sink capacity,
> bringing the post-expansion ratio to ~23,180/12,950 ≈ **1.8x** (within 2-3x target).
> Cat Launch first-clear is capped at 50 unique stages to prevent unbounded income.

---

## E. Edge Cases

1. **Insufficient coins for power-up**: `spendCoins()` returns false; the power-up is not applied. A toast message informs the player ("코인이 부족합니다!").
2. **Ice refund**: If no non-cat block exists to remove, coins are refunded via `refundCoins()` which does NOT inflate `totalCoinsEarned`.
3. **Achievement double-award**: `unlockAchievement()` is idempotent — the `unlock()` DAO query only updates if `unlocked = false`. Coin reward is only added on actual state change.
4. **Negative coin balance**: Not possible — `spendCoins()` uses a conditional UPDATE (`WHERE coins >= amount`) and returns affected row count. 0 rows = insufficient funds.
5. **Concurrent coin operations**: All coin operations use `withContext(Dispatchers.IO)` and Room's built-in thread safety. No race conditions possible.
6. **Migration from v2**: `player_stats` table is created with a default row (id=1, coins=0, totalCoinsEarned=0). Existing players start with 0 coins upon upgrading.
7. **Stars always 1-3**: The star rating is always in the range [1, 3]. `require(stars in 1..3)` guards are enforced in `saveProgress()` and `saveLaunchProgress()`. Verified 2026-04-15.
8. **Negative amount guard**: `addCoins()`, `spendCoins()`, and `refundCoins()` all enforce `require(amount > 0)`. Verified 2026-04-15.
9. **`totalCoinsEarned` semantics**: This column measures "coins added via `addCoins()` only." It is NOT a spend-volume metric. Refunded coins can be re-spent without incrementing `totalCoinsEarned`.
10. **Endless count default**: `getEndlessCount()` defaults to 0 in SharedPreferences. Verified 2026-04-15.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Puzzle System** | Primary coin source (stage clears) and primary coin sink (power-ups) |
| **Progression System** | Achievement unlocks trigger coin rewards |
| **Cat Launch System** | Shares coin pool; awards coins on stage clears (10/20/30 + 50 first-clear) |
| **Ad System** | No direct monetization tie — ads gate hints/solve, not coins |
| **UI System** | Displays coin balance in HUD; power-up buttons show costs |

---

## G. Tuning Knobs

> **Ownership**: Economy GDD is the single source of truth for all coin/reward values.
> Other GDDs (Puzzle, Cat Launch, Progression) reference these values but do not redefine them.

| Parameter | Current Value | Location |
|-----------|--------------|----------|
| Star coin rewards | 10 / 20 / 30 | `GameRepository.saveProgress()` |
| First-clear bonus | 50 | `GameRepository.saveProgress()` |
| Magnet cost | 30 | `PuzzleActivity.handlePowerUp()` |
| Ice cost | 40 | `PuzzleActivity.handlePowerUp()` |
| Shuffle cost | 50 | `PuzzleActivity.handlePowerUp()` |
| Achievement rewards | 10-500 | `AchievementDefs.ALL` |
| Endless star coins | 10 / 20 / 30 | `PuzzleActivity.handleStageClear()` |
| Endless daily coin cap | 150 | `GameRepository.ENDLESS_DAILY_COIN_CAP` |
| Launch star coins | 10 / 20 / 30 | `GameRepository.saveLaunchProgress()` |
| Launch first-clear bonus | 50 | `GameRepository.saveLaunchProgress()` |

---

## H. Acceptance Criteria

### Puzzle Coin Sources (3)

1. **Star coin mapping**: `starCoins` must return exactly `{3: 30, 2: 20, 1: 10}`. No other star values are valid — `require(stars in 1..3)` enforced at call site.
2. **Puzzle first-clear bonus**: Given a stage with no prior `UserProgress` record (or `completed = false`), `saveProgress(stageId, stars, ...)` must award `starCoins(stars) + 50` coins. Replays of the same stage must award `starCoins(stars)` only, with 0 bonus.
3. **Endless coin awards**: Endless mode clears must award `starCoins(stars)` via `addCoins()` with no first-clear bonus. `totalCoinsEarned` must increase by the awarded amount.

### Cat Launch Coin Sources (2)

4. **Launch star coins**: `saveLaunchProgress(stageId, stars)` must award `starCoins(stars)` on every clear. Star-to-coin mapping is identical to Puzzle: `{3: 30, 2: 20, 1: 10}`.
5. **Launch first-clear bonus**: First completion of a Launch stage awards +50 bonus coins (parity with Puzzle). Subsequent clears of the same stage award 0 bonus. `totalCoinsEarned` must increase by `starCoins + bonus`.

### Achievement Coin Sources (2)

6. **Achievement idempotency**: `unlockAchievement(id)` must award `AchievementDefs.get(id).coinReward` via `addCoins()` on the first call. All subsequent calls for the same `id` must award 0 coins and return `false`.
7. **Achievement reward total**: The 30 achievements in `AchievementDefs.ALL` must sum to exactly **2,180** coins. Any change to individual rewards must update this total and `economy-system.md` Section D.

### Coin Sinks — Power-Ups (3)

8. **Deduction-before-effect**: `handlePowerUp(index)` must call `spendCoins(cost)` and confirm success before applying the power-up effect. If `spendCoins()` returns `false`, no effect is applied.
9. **Ice refund on failure**: Given a grid with only non-removable blocks (cat/wall/key), `handlePowerUp(1)` [Ice, 40 coins] must deduct 40, call `applyIcePowerUp()` → returns `false`, then call `refundCoins(40)`. Net balance: unchanged. `totalCoinsEarned`: unchanged.
10. **Insufficient balance rejection**: Given balance `B` and `spendCoins(amount)` where `amount > B`: (a) returns `false`, (b) `getCoins()` still returns `B`, (c) `totalCoinsEarned` unchanged, (d) no power-up effect applied.

### Balance Integrity (3)

11. **Non-negative balance**: `spendCoins()` uses a conditional `UPDATE ... WHERE coins >= amount`. If the row-update count is 0, the spend fails. `getCoins()` can never return a value < 0.
12. **totalCoinsEarned monotonicity**: `totalCoinsEarned` increases by exactly `amount` on `addCoins(amount)`. It must remain unchanged on `spendCoins()` and `refundCoins()`. It never decreases.
13. **Input validation**: `addCoins(amount)`, `spendCoins(amount)`, and `refundCoins(amount)` must throw on `amount ≤ 0`. `stars` parameter in `saveProgress()` and `saveLaunchProgress()` must satisfy `require(stars in 1..3)`.

### Persistence & Display (3)

14. **Database migration v2→v3**: Must create `player_stats` table with `id=1, coins=0, totalCoinsEarned=0`. Must add `user_progress.bestScore` column with default 0. Existing rows preserved.
15. **Coin display sync**: After any `spendCoins()` or `addCoins()` call completes, `puzzleView.displayCoins` must be updated to equal `repository.getCoins()` within the same coroutine continuation.
16. **refundCoins semantics**: `refundCoins(amount)` must increase `coins` by `amount` without changing `totalCoinsEarned`. This prevents Ice refunds from inflating lifetime earnings.
