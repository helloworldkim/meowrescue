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

> **Design rationale**: Endless mode generates unlimited stages procedurally. Without a
> cap, a dedicated player could earn thousands of coins per day, inflating income far
> above the expansion sinks (17,350 total) and breaking the target 1.0–2.5× surplus
> ratio. The 150 coin/day cap (~5-7 clears) allows a meaningful daily reward session
> while bounding long-term inflation. At 150/day over 30 days = 4,500 coins — a
> significant but contained supplement to the ~22,290 progression-based income.
> Achievement coins earned during Endless play bypass the cap (see Edge Case 14).

#### Achievement Rewards

32 achievements with rewards ranging from 10 to 500 coins.

| Tier | Typical Reward | Examples |
|------|---------------|----------|
| Easy | 10-20 coins | powerup_1, coins_100, clear_1 |
| Medium | 30-50 coins | clear_10, star3_10, launch_1cat, cat_3 |
| Hard | 80-150 coins | clear_150, star3_100, speed_5s |
| Epic | 200-500 coins | clear_200, cat_all, star3_200 |

Total achievable achievement coins: **2,290** (sum of all 32 achievement rewards).

#### Cat Launch Stage Clear

Cat Launch clears award **base** star coins scaled by a difficulty multiplier:

| Star Rating | Base Coins | Easy (×1.0) | Normal (×1.5) | Hard (×2.0) |
|-------------|-----------|-------------|---------------|-------------|
| 3★ | 30 | 30 | 45 | 60 |
| 2★ | 20 | 20 | 30 | 40 |
| 1★ | 10 | 10 | 15 | 20 |

**First-clear bonus**: +50 coins (flat, regardless of difficulty) on first completion
of any Launch stage. Capped at **50 unique stages** — after 50 first clears, subsequent
new stages earn star coins only (no bonus).

> **Implemented** (2026-04-15): `GameRepository.saveLaunchProgress()` calls
> `addCoins(scaledStarCoins + firstClearBonus)` with `require(stars in 1..3)` guard.
> Difficulty multiplier applied via `LaunchDifficulty.coinMultiplier`.

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

### Variable Definitions

| Symbol | Type | Range | Description |
|--------|------|-------|-------------|
| `stars` | INT | [1, 3] | Star rating earned on clear |
| `starCoins(s)` | INT | {10, 20, 30} | Base coin reward for star rating `s` |
| `coinMult` | FLOAT | {1.0, 1.5, 2.0} | Cat Launch difficulty multiplier (Easy/Normal/Hard) |
| `firstClearBonus` | INT | 50 | Flat bonus on first completion of a stage |
| `isFirstClear` | BOOL | — | True if stage has no prior completed record |
| `dailyEarned` | INT | [0, 150] | Endless coins earned today (resets at midnight) |
| `DAILY_CAP` | INT | 150 | Maximum Endless coins per calendar day |

### Puzzle Earnings Per Stage

```
puzzleClearCoins(stars, isFirstClear) =
  starCoins(stars) + (isFirstClear ? 50 : 0)

where starCoins = { 1: 10, 2: 20, 3: 30 }
```

**Output range**: [10, 80]
- Minimum: replay, 1★ → 10
- Maximum: first clear, 3★ → 30 + 50 = 80

**Worked examples**:
- Stage 42, first clear, 2★ → 20 + 50 = **70 coins**
- Stage 42, replay, 3★ → 30 + 0 = **30 coins**
- Stage 1, first clear, 1★ → 10 + 50 = **60 coins**

### Cat Launch Earnings Per Stage

```
launchClearCoins(stars, coinMult, isFirstClear) =
  round(starCoins(stars) * coinMult) + (isFirstClear ? 50 : 0)

where starCoins = { 1: 10, 2: 20, 3: 30 }
      coinMult  = { Easy: 1.0, Normal: 1.5, Hard: 2.0 }
      firstClearBonus = 50 (flat — NOT multiplied by coinMult)
```

**Output range**: [10, 110]
- Minimum: replay, Easy, 1★ → round(10 × 1.0) = 10
- Maximum: first clear, Hard, 3★ → round(30 × 2.0) + 50 = 110

**First-clear cap**: After 50 unique Launch stage IDs cleared, `isFirstClear` is
forced false regardless of stage history. Total capped first-clear income:
50 × 50 = 2,500 coins (bonus only).

**Worked examples**:
- Easy, 3★, first clear → round(30 × 1.0) + 50 = **80 coins**
- Normal, 2★, replay → round(20 × 1.5) + 0 = **30 coins**
- Hard, 3★, first clear → round(30 × 2.0) + 50 = **110 coins**
- Hard, 1★, replay → round(10 × 2.0) + 0 = **20 coins**

Note: All products of {10, 20, 30} × {1.0, 1.5, 2.0} produce integers; rounding
has no practical effect at current values but guards against future multiplier changes.

### Endless Earnings Per Clear

```
endlessClearCoins(stars, dailyEarned) =
  min(starCoins(stars), DAILY_CAP - dailyEarned)

where DAILY_CAP = 150
      starCoins = { 1: 10, 2: 20, 3: 30 }
```

**Output range**: [0, 30]
- Awards 0 when `dailyEarned ≥ 150` (cap reached)
- Partial award when `dailyEarned + starCoins(stars) > 150`

**Worked examples**:
- dailyEarned = 0, 3★ → min(30, 150) = **30 coins**
- dailyEarned = 140, 3★ → min(30, 10) = **10 coins** (partial)
- dailyEarned = 150, 3★ → min(30, 0) = **0 coins** (capped)

### Lifetime Income Estimates

```
Puzzle first-clears (200 stages, all 3★):
  200 × (30 + 50) = 16,000
Puzzle first-clears (200 stages, avg 2★):
  200 × (20 + 50) = 14,000

Achievement rewards: 2,290 (sum of 32 achievements)

Puzzle + achievements subtotal: 18,290 (best case) / 16,290 (avg)

Cat Launch first-clears (50 stages, Easy, all 3★):
  50 × (30 + 50) = 4,000
Cat Launch first-clears (50 stages, Hard, all 3★):
  50 × (60 + 50) = 5,500

Income variance by difficulty: Easy 4,000 → Hard 5,500 (+37.5%)
```

**Canonical income base**: **22,290 coins** (puzzle 18,290 + launch 4,000, Easy 3★
first-clears only). Used for all surplus ratio calculations. Excludes replays, Endless,
and difficulty multiplier variance — these add income above the baseline.

### Economy Balance — Milestone Pacing

Income/sink balance at key progression checkpoints. Assumptions: average 2★ per stage,
Easy Cat Launch, consumable spending (power-ups + hints) estimated at ~10 coins/stage.

| Milestone | Cumul. Income | Unlocked One-Time Sinks | Ratio | Assessment |
|-----------|--------------|------------------------|-------|------------|
| Stage 30 | ~2,600 | 3,300 (Common cosmetics 900 + themes 2,400) | 0.79x | Constrained — cannot buy everything |
| Stage 60 | ~5,400 | 4,500 (+Uncommon cosmetics for cats 4-5) | 1.20x | Near break-even — real spending decisions |
| Stage 100 | ~9,200 | 5,700 (+Uncommon cosmetics for cats 6-7) | 1.61x | Surplus building — themes compete with cosmetics |
| Stage 150 | ~14,200 | 8,400 (+Rare cosmetics for cats 8-10) | 1.69x | Stable — Rare items absorb surplus |
| Stage 200 | ~22,290 | 17,350 (all expansion sinks) | 1.28x | Tight — Legendary sinks close the loop |

**Target range**: 1.0–2.5× at each checkpoint. All milestones fall within target.
Early game (stages 1-60) is genuinely constrained; mid-game (stages 60-150) has
moderate surplus with real spending decisions between cosmetics and themes;
endgame (stages 150-200) tightens as Legendary sinks become available.

> **Balance Note**: Pre-expansion surplus is ~9.7x (power-ups only). The expansion
> sinks are **required** for the economy to function as intended — without them the
> Player Fantasy of "scarce enough for thought" cannot be delivered. Endless mode
> income (capped at 150 coins/day) is excluded as time-based rather than
> progression-based; it adds income above the baseline over extended play.

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
11. **Endless daily cap partial award**: If `dailyEarned = 140` and the player earns 3★ (30 coins), only `min(30, 150-140) = 10` coins are awarded. `totalCoinsEarned` increases by 10, not 30.
12. **Endless daily cap reset**: The daily cap resets at midnight local time. The reset date is stored in SharedPreferences as `endless_coin_date` (ISO date string). If the current date differs from the stored date, `dailyEarned` resets to 0 and the new date is stored.
13. **Endless cap UI feedback**: When the daily cap is reached, Endless clears continue to track stars, `endlessCount`, and `endlessBest` normally. The player is informed that coin rewards are exhausted for today.
14. **Endless cap vs. achievement coins**: Achievement coins earned during Endless play (via `unlockAchievement()`) are awarded through `addCoins()` independently — they are NOT counted against the Endless daily cap. The cap applies only to clear rewards.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Puzzle System** | Primary coin source (stage clears) and primary coin sink (power-ups) |
| **Progression System** | Achievement unlocks trigger coin rewards |
| **Cat Launch System** | Shares coin pool; awards coins on stage clears (10/20/30 + 50 first-clear) |
| **Ad System** | No direct monetization tie — ads gate hints/solve, not coins |
| **Economy Expansion** | Extends coin sinks: paid hints, cat cosmetics, stage skip, grid themes |
| **UI System** | Displays coin balance in HUD; power-up buttons show costs |

---

## G. Tuning Knobs

> **Ownership**: Economy GDD is the single source of truth for all coin/reward values.
> Other GDDs (Puzzle, Cat Launch, Progression) reference these values but do not redefine them.

| Parameter | Current Value | Safe Range | Affects | Location |
|-----------|--------------|------------|---------|----------|
| Star coin rewards (1★/2★/3★) | 10 / 20 / 30 | 5-50 per tier | Session income rate; surplus ratio | `GameRepository.saveProgress()` |
| First-clear bonus | 50 | 20-100 | Income front-loading; pacing curve shape | `GameRepository.saveProgress()` |
| Magnet cost | 30 | 15-60 | Lowest spending threshold; power-up frequency | `PuzzleActivity.handlePowerUp()` |
| Ice cost | 40 | 20-80 | Mid-tier power-up accessibility | `PuzzleActivity.handlePowerUp()` |
| Shuffle cost | 50 | 25-100 | Premium power-up gate; highest base spend | `PuzzleActivity.handlePowerUp()` |
| Achievement rewards | 10-500 | 5-1,000 | One-time income spikes; pacing bumps at milestones | `AchievementDefs.ALL` |
| Endless star coins | 10 / 20 / 30 | 5-50 | Endgame income rate (within daily cap) | `PuzzleActivity.handleStageClear()` |
| Endless daily coin cap | 150 | 50-300 | Long-term inflation rate; endgame income ceiling | `GameRepository.ENDLESS_DAILY_COIN_CAP` |
| Launch base star coins | 10 / 20 / 30 | 5-50 | Cat Launch income (before difficulty multiplier) | `GameRepository.saveLaunchProgress()` |
| Launch first-clear bonus | 50 | 20-100 | Launch mode incentive; matches Puzzle parity | `GameRepository.saveLaunchProgress()` |

---

## H. Acceptance Criteria

### Puzzle Coin Sources (3)

1. **Star coin mapping**: `starCoins` must return exactly `{3: 30, 2: 20, 1: 10}`. No other star values are valid — `require(stars in 1..3)` enforced at call site.
2. **Puzzle first-clear bonus**: Given a stage with no prior `UserProgress` record (or `completed = false`), `saveProgress(stageId, stars, ...)` must award `starCoins(stars) + 50` coins. Replays of the same stage must award `starCoins(stars)` only, with 0 bonus.
3. **Endless coin awards (under cap)**: Given `dailyEarned < 150`, an Endless clear with stars `S` awards exactly `starCoins(S)` coins. `totalCoinsEarned` increases by `starCoins(S)`. No first-clear bonus is awarded.
3a. **Endless daily cap enforcement**: Given `dailyEarned >= 150`, an Endless clear awards **0 coins**. `totalCoinsEarned` is unchanged. Stars, `endlessCount`, and `endlessBest` continue to update normally.
3b. **Endless partial cap award**: Given `dailyEarned = N` where `N + starCoins(S) > 150`, the clear awards exactly `150 - N` coins (not the full `starCoins(S)`). `totalCoinsEarned` increases by `150 - N`.
3c. **Endless cap reset**: Given the cap was reached on calendar date D, an Endless clear on date D+1 awards the full `starCoins(S)` coins (assuming new `dailyEarned` = 0).

### Cat Launch Coin Sources (2)

4. **Launch star coins with difficulty multiplier**: `saveLaunchProgress(stageId, stars, coinMultiplier)` must award `round(starCoins(stars) * coinMultiplier)` on every clear. Easy (×1.0): 10/20/30. Normal (×1.5): 15/30/45. Hard (×2.0): 20/40/60. `totalCoinsEarned` increases by the scaled amount.
5. **Launch first-clear bonus**: First completion of a Launch stage awards +50 bonus coins (flat, not multiplied by difficulty). Subsequent clears of the same stage award 0 bonus. `totalCoinsEarned` must increase by `round(starCoins * coinMultiplier) + bonus`.
5a. **Launch first-clear cap**: After 50 unique Launch stage IDs have been first-cleared, subsequent new stages award scaled star coins only — no first-clear bonus. The 51st unique first clear must award 0 bonus. Checked via `getCompletedLaunchCount() >= LAUNCH_FIRST_CLEAR_CAP`.

### Achievement Coin Sources (2)

6. **Achievement idempotency**: `unlockAchievement(id)` must award `AchievementDefs.get(id).coinReward` via `addCoins()` on the first call. All subsequent calls for the same `id` must award 0 coins and return `false`.
7. **Achievement reward total**: The 32 achievements in `AchievementDefs.ALL` must sum to exactly **2,290** coins. Any change to individual rewards must update this total and `economy-system.md` Section D.

### Coin Sinks — Power-Ups (3)

8. **Deduction-before-effect**: `handlePowerUp(index)` must call `spendCoins(cost)` and confirm success before applying the power-up effect. If `spendCoins()` returns `false`, no effect is applied.
9. **Ice refund on failure**: Given a grid with only non-removable blocks (cat/wall/key), `handlePowerUp(1)` [Ice, 40 coins] must deduct 40, call `applyIcePowerUp()` → returns `false`, then call `refundCoins(40)`. Net balance: unchanged. `totalCoinsEarned`: unchanged.
9a. **Shuffle refund on failure**: Given 5 consecutive shuffle attempts all produce unsolvable states, `handlePowerUp(2)` [Shuffle, 50 coins] must deduct 50, attempt shuffles, restore original grid on failure, then call `refundCoins(50)`. Net balance: unchanged. `totalCoinsEarned`: unchanged. Mirrors Ice refund policy.
10. **Insufficient balance rejection**: Given balance `B` and `spendCoins(amount)` where `amount > B`: (a) returns `false`, (b) `getCoins()` still returns `B`, (c) `totalCoinsEarned` unchanged, (d) no power-up effect applied.

### Balance Integrity (3)

11. **Non-negative balance**: `spendCoins()` uses a conditional `UPDATE ... WHERE coins >= amount`. If the row-update count is 0, the spend fails. `getCoins()` can never return a value < 0.
12. **totalCoinsEarned monotonicity**: `totalCoinsEarned` increases by exactly `amount` on `addCoins(amount)`. It must remain unchanged on `spendCoins()` and `refundCoins()`. It never decreases.
13. **Input validation**: `addCoins(amount)`, `spendCoins(amount)`, and `refundCoins(amount)` must throw on `amount ≤ 0`. `stars` parameter in `saveProgress()` and `saveLaunchProgress()` must satisfy `require(stars in 1..3)`.

### Persistence & Display (3)

14. **Database migration v2→v3**: Must create `player_stats` table with `id=1, coins=0, totalCoinsEarned=0`. Must add `user_progress.bestScore` column with default 0. Existing rows preserved.
15. **Coin display sync**: After any `spendCoins()` or `addCoins()` call completes, `puzzleView.displayCoins` must be updated to equal `repository.getCoins()` within the same coroutine continuation.
16. **refundCoins semantics**: `refundCoins(amount)` must increase `coins` by `amount` without changing `totalCoinsEarned`. This prevents Ice refunds from inflating lifetime earnings.
