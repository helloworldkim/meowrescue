---
status: revised
source: app/src/main/java/com/meowrescue/game/data/
date: 2026-04-15
revised: 2026-04-15
verified-by: User
---

# Progression System

> **Note**: This document was reverse-engineered from the existing implementation.
> It captures current behavior and clarified design intent. Some sections may be
> incomplete where implementation is partial or intent was unclear.

---

## A. Overview

The Progression System tracks the player's advancement across both core game modes
(Puzzle and Cat Launch), manages cat collection unlocks, and awards achievements.
It uses Room database for persistent storage with a migration path (v1→v2→v3) and
SharedPreferences for lightweight state. The system spans 200 puzzle stages across
7 themed worlds and an unlimited number of Cat Launch stages.

---

## B. Player Fantasy

The player is building a **cat rescue career** — advancing through increasingly
difficult worlds, collecting a family of 13 unique cats, and earning achievements
that recognize mastery. The world themes (Garden → Beach → Forest → Snow → Volcano →
Space → Castle) create a journey narrative where each new world feels like a new
chapter with fresh visual identity.

---

## C. Detailed Rules

### Stage Progression (Puzzle)

- 200 fixed stages, numbered 1-200.
- Stages unlock sequentially: completing stage N unlocks stage N+1.
- Each stage tracks: completion status, star rating (1-3), best score.
- Stars are "best ever" — replaying a stage keeps the highest star count.
- Best score is also "best ever" — only updates if the new score is higher.

### World Themes

| World | Name | Stages | BGM Key |
|-------|------|--------|---------|
| 1 | 정원 (Garden) | 1-30 | beginner |
| 2 | 해변 (Beach) | 31-60 | beginner |
| 3 | 숲 (Forest) | 61-90 | intermediate |
| 4 | 눈 (Snow) | 91-120 | intermediate |
| 5 | 화산 (Volcano) | 121-150 | advanced |
| 6 | 우주 (Space) | 151-180 | advanced |
| 7 | 성 (Castle) | 181-200 | advanced |

Each world has a distinct color palette: background gradient, grid background,
cell lines, and 6 block colors. In Endless mode, themes cycle via `stage % 7`.

### Stage Progression (Cat Launch)

- Unlimited stages (procedurally generated).
- Independent progress table (`launch_progress`).
- Tracks: completion, stars, best score per stage.
- No sequential gating — any stage can be attempted (UI may restrict).

### Cat Collection

13 cats, unlocked at specific puzzle stage milestones:

| Cat ID | Name | Required Stage | Ability (Launch) |
|--------|------|---------------|-----------------|
| 1 | 나비 | 1 | Normal |
| 2 | 봄이 | 15 | Normal |
| 3 | 여름 | 30 | Normal |
| 4 | 가을 | 45 | Redirect |
| 5 | 겨울 | 60 | Redirect |
| 6 | 솜이 | 75 | Split |
| 7 | 꽃이 | 90 | Split |
| 8 | 하늘 | 110 | Explosive |
| 9 | 바다 | 130 | Explosive |
| 10 | 무지개 | 150 | Charge |
| 11 | 보석 | 170 | Charge |
| 12 | 왕자 | 185 | Explosive |
| 13 | 공주 | 200 | Charge |

- Each cat has a unique drawable resource.
- Cats are unlocked on **first clear** of the required stage.
- A congratulations dialog appears on unlock.
- The player can select any unlocked cat as their "active" cat (cosmetic in puzzle mode, functional in Cat Launch).

> **Pacing rationale**: Cat unlock spacing is intentionally wider for higher-rarity
> tiers. Common cats (1-3) unlock every 14-15 stages for rapid early momentum.
> Uncommon cats (4-7) maintain 15-stage gaps. Rare cats (8-11) use 20-stage gaps —
> these are harder-earned, more powerful abilities (Explosive, Charge) that serve
> as aspirational milestones in the mid-to-late game. Legendary cats (12-13) return
> to 15-stage gaps for the final sprint. Collection achievements at cat_5 and cat_10
> provide reward signals between the cat_3 and cat_7/cat_all milestones.

### Achievement System

32 achievements across 7 categories. Each has a coin reward.

#### Progress (9)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| clear_1 | 첫 발걸음 | Clear stage 1 | 20 |
| clear_10 | 견습 구조대원 | Clear stage 10 | 30 |
| clear_30 | 정원 졸업 | Clear stage 30 | 50 |
| clear_60 | 해변 졸업 | Clear stage 60 | 50 |
| clear_90 | 숲 졸업 | Clear stage 90 | 50 |
| clear_120 | 눈 졸업 | Clear stage 120 | 50 |
| clear_150 | 화산 졸업 | Clear stage 150 | 80 |
| clear_180 | 우주 졸업 | Clear stage 180 | 80 |
| clear_200 | 전설의 구조대원 | Clear all stages | 200 |

#### Mastery (4)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| star3_10 | 별 수집가 | 10 three-star clears | 30 |
| star3_50 | 스타 마스터 | 50 three-star clears | 80 |
| star3_100 | 퍼펙트 플레이어 | 100 three-star clears | 150 |
| star3_200 | 완벽주의자 | All 200 stages three-starred | 500 |

#### Gameplay (5)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| optimal_clear | 최적 경로 | Clear in ≤ optimal moves | 20 |
| two_move | 최소 클리어 | Clear in ≤ 2 moves (optimal play on simplest puzzles) | 50 |
| no_undo | 한 번에 OK | 3★ clear without using undo | 20 |
| speed_10s | 번개손 | Clear within 10 seconds | 30 |
| speed_5s | 찰나의 구출 | Clear within 5 seconds | 80 |

#### Launch (3)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| launch_10 | 런처 루키 | Clear 10 Cat Launch stages | 30 |
| launch_1cat | 원샷 원킬 | Clear with 1 cat | 50 |
| launch_tnt | 폭파 전문가 | 3+ TNT chain explosions | 30 |

#### Collection (5)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| cat_3 | 고양이 친구 | Unlock 3 cats | 20 |
| cat_5 | 새로운 능력 | Unlock 5 cats | 30 |
| cat_7 | 고양이 대가족 | Unlock 7 cats | 50 |
| cat_10 | 구조 베테랑 | Unlock 10 cats | 80 |
| cat_all | 모든 고양이! | Unlock all 13 cats | 200 |

#### Economy (4)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| coins_100 | 용돈 모으기 | Earn 100 total coins | 10 |
| coins_1000 | 부자 고양이 | Earn 1000 total coins | 50 |
| powerup_1 | 파워업 입문 | Use a power-up once | 10 |
| powerup_10 | 파워업 마니아 | Use power-ups 10 times | 30 |

#### Endless (2)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| endless_10 | 끝없는 도전 | Clear 10 endless stages | 30 |
| endless_50 | 무한 구조대원 | Clear 50 endless stages | 100 |

### Achievement Rules

- Achievements are checked after every stage clear (both modes).
- Each achievement can only be unlocked once.
- Unlocking awards the coin reward immediately.
- Unlock timestamp is stored for display purposes.
- Achievement checks are cumulative — clearing stage 90 also triggers clear_1 through clear_90.

### Endless Mode Progression

- Tracked via SharedPreferences (`endless_count`, `endless_best`).
- `endlessCount` starts at 0 (default) and increments per clear. Persists across sessions.
- `endlessBest` is the high watermark.
- Generates stages from pool 131-10000 (advanced features always active).

### Tutorial Tracking

- Single boolean: `tutorial_completed`.
- Set to `true` after the stage 3 tutorial auto-dismisses (stages 1-3 each show
  a tutorial overlay; stage 1 is interactive, stages 2-3 are auto-dismissing tips).
- Once set, the **initial tutorial sequence** (stages 1-3 overlays) does not replay
  on revisit. This flag does NOT suppress future per-mechanic tutorials (e.g.,
  wall introduction, portal introduction) — those are a separate planned feature
  not yet implemented.

---

## D. Formulas

### Variable Definitions

| Symbol | Type | Range | Description |
|--------|------|-------|-------------|
| `clearedStage` | INT | [1, 200] | Stage ID just completed by the player |
| `prevMaxCompletedLevel` | INT | [0, 200] | Highest stage ID cleared before this clear (0 = fresh install) |
| `optimalMoves` | INT | [2, 45] | BFS-computed minimum moves to solve (from Puzzle System generator) |
| `totalCoinsEarned` | INT | [0, ∞) | Lifetime coins earned via `addCoins()` (from Economy System) |

### Cat Unlock Check

```
newlyUnlockedCat(clearedStage, prevMaxCompletedLevel) =
  if (clearedStage > prevMaxCompletedLevel)
    CAT_DEFINITIONS.firstOrNull { it.requiredStage == clearedStage }
  else null
```

**Output**: A `CatDefinition` object or `null`.
The `prevMaxCompletedLevel` guard ensures replays of already-cleared stages do not
re-trigger unlock dialogs. `prevMaxCompletedLevel` is the player's highest cleared
stage ID **before** the current clear is saved.

**Worked example**: Player's highest cleared = 29. Clears stage 30.
`30 > 29` → true. `CAT_DEFINITIONS.firstOrNull { 30 == 30 }` → returns cat 여름 (#3).

### Achievement Coin Reward

```
on unlock(id):
  reward = AchievementDefs.get(id).coinReward
  addCoins(reward)  // increases both coins and totalCoinsEarned
```

**Output range**: [10, 500] (min: powerup_1 = 10, max: star3_200 = 500).

### Economy Achievement Triggers

```
coins_100:  triggers when totalCoinsEarned >= 100
coins_1000: triggers when totalCoinsEarned >= 1000
```

Uses `totalCoinsEarned` (lifetime, never decremented by spending), not `coins`
(current balance). This prevents spending from blocking achievement progress.

### Endless Stage Generation

```
endlessStage = Random.nextInt(131, 10000)
```

**Output range**: [131, 9999] (Kotlin `nextInt` upper bound is exclusive).
Stage 131+ ensures advanced features (walls, linked blocks, portals, multi-cat)
are always available in the generation pool.

---

## E. Edge Cases

1. **Replaying an already-cleared stage**: Stars and score use `max(new, existing)`. Completion flag stays true. First-clear bonus is NOT re-awarded.
2. **Achievement re-checking**: All progress achievements are checked every clear. The `unlock()` call is idempotent — already-unlocked achievements return false and don't re-award coins.
3. **Cat unlock on replay**: Cat unlocks only trigger when `currentStage > prevMaxCompletedLevel`. Replaying stage 30 after already clearing stage 60 does NOT re-show the unlock dialog.
4. **Endless count persistence**: If the app crashes mid-endless-run, the count up to the last clear is preserved.
5. **Database migration**: v1→v2 adds `launch_progress` table. v2→v3 adds `bestScore` column, `player_stats` table, and `achievements` table. Migrations are additive-only.
6. **Three-star count query**: Counts `user_progress` entries with `stars >= 3`. Endless mode uses SharedPreferences (not `user_progress`), so only puzzle stages 1-200 contribute to this count.
7. **Speed achievement timer**: `speed_10s` and `speed_5s` use wall-clock time (`System.currentTimeMillis()`) from `stageStartTime`. Time includes pause/background time. This is a known design choice — the timer measures real elapsed time, not active play time.
8. **`optimal_clear` data source**: `optimalMoves` is the BFS-computed solution depth from `PuzzleGenerator.generateWithResult()`, stored in `PuzzleGrid.optimalMoves`. The achievement triggers when `moves <= optimalMoves` — functionally equivalent to earning 3★ on any stage.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Puzzle System** | Writes completion data; reads for stage gating |
| **Cat Launch System** | Writes completion data; reads unlocked cats |
| **Economy System** | Awards coins on clear and achievement unlock |
| **UI System** | Stage select reads progress for display; Collection reads unlocked cats |
| **Economy Expansion** | Cat unlock gates cosmetic purchases; stage skip writes 1★ completion |
| **Ad System** | Stage clear triggers ad check |

---

## G. Tuning Knobs

| Parameter | Current Value | Safe Range | Affects | Location |
|-----------|--------------|------------|---------|----------|
| Cat unlock stages | 1,15,30,...,200 | Gaps ≤ 20 stages | P1 pacing; cosmetic tier alignment | `GameRepository.CAT_DEFINITIONS` |
| Achievement coin rewards | See economy-system.md | See economy tuning | Economy surplus ratio; pacing | `AchievementDefs.ALL` |
| Achievement thresholds | Various (see tables) | Context-dependent | Achievement pacing; reward density | `AchievementDefs.ALL` |
| World theme boundaries | 30/60/90/120/150/180/200 | 20-40 per world | Visual pacing; BGM transitions | `WorldTheme.forStage()` |
| Endless stage range | 131-9999 | Lower ≥ 51 (ensure advanced features) | Endless difficulty floor | `PuzzleActivity.generateRandomStage()` |
| Tutorial stages | 1, 2, 3 | 1-5 | Onboarding window length | `PuzzleActivity.loadStage()` |
| Total stage count | 200 | 100-300 | Content volume; economy balance | Hard cap in navigation logic |

---

## H. Acceptance Criteria

### Persistence
1. **Stage completion persistence**: After `saveProgress(levelId, stars, score)` completes and the process is terminated (via `adb shell am kill`), a fresh app launch must return a `UserProgress` row where `stageId`, `stars`, `score`, and `completed = true` match the values at the time of kill. No fields may be null or reset to default.
2. **Best-ever stars**: `saveProgress(levelId, stars)` must store `max(new, existing)` for stars. Replaying with fewer stars must not downgrade. Test: save 3★, save 1★, verify stars == 3.
3. **Best-ever score**: `saveProgress(levelId, stars, score)` must store `max(new, existing)` for score. Test: save 1000, save 500, verify bestScore == 1000.
4a. **Endless count persistence**: `endlessCount` (SharedPreferences, default 0) increments by exactly 1 per Endless clear. Value persists across process restart (verified via `adb shell am kill`).
4b. **Endless best watermark**: `endlessBest` stores `max(newScore, existing)`. On fresh install, defaults to 0. A score equal to `endlessBest` does not change the stored value (tie = no-op).
5. **Selected cat persistence**: `getSelectedCatId()` / `setSelectedCatId()` must persist via SharedPreferences across app restarts.

### Cat Collection
6. **Cat unlock timing**: `getNewlyUnlockedCat(clearedStage)` returns a cat only when `clearedStage == cat.requiredStage` AND `currentStage > prevMaxCompletedLevel`. Replaying an already-cleared milestone does NOT re-trigger unlock.
7. **Cat unlock dialog**: Congratulations dialog appears exactly once per cat, on first-time clear of the required stage.
8. **Cat-Puzzle dependency**: All 13 cats are gated behind puzzle stage milestones. Cat Launch progress does NOT unlock cats. This is intentional — Cat Launch uses whatever cats the player has unlocked via puzzle progression.

### Achievements
9. **Achievement idempotency**: `unlockAchievement(id)` awards `coinReward` exactly once. Subsequent calls return false and award 0 coins.
10. **Progress achievements cumulative**: Clearing stage N for the first time on a fresh install triggers all `clear_X` achievements where `X <= N`. Test: clear stage 90 on a fresh install → triggers clear_1, clear_10, clear_30, clear_60, clear_90 = **5** progress achievements.
11. **`two_move` achievement**: Triggers when `moves ≤ 2` on any stage clear. (Changed from `one_move` — all puzzles now guarantee optimalMoves ≥ 2.)
12. **`cat_all` achievement**: Triggers only when `getUnlockedCats().size >= 13` (all cats). Must not trigger at 12 or fewer.
13. **Speed achievements**: `speed_10s` / `speed_5s` use wall-clock time from `stageStartTime`. Time includes any pause/background time — this is a known limitation.
14. **Total achievement rewards**: 32 achievements in `AchievementDefs.ALL` must sum to exactly **2,290** coins. Any change to reward values must update this total and `economy-system.md` Section D.
14a. **`coins_100`/`coins_1000` trigger**: These achievements check `totalCoinsEarned` (lifetime, from Economy System), not `coins` (current balance). `coins_100` triggers when `totalCoinsEarned >= 100`. `coins_1000` triggers when `totalCoinsEarned >= 1000`.
14b. **`launch_10` trigger**: Triggers when the player has cleared **10 unique** Cat Launch stage IDs (not 10 total clears of the same stage). Checked via `getCompletedLaunchCount()`.

### Database
15. **Migration v1→v2**: Creates `launch_progress` table. Existing `user_progress` data preserved.
16. **Migration v2→v3**: Adds `bestScore` column (default 0), creates `player_stats` table (coins=0, totalCoinsEarned=0), creates `achievements` table. Existing data preserved.

### World Themes
17. **Theme mapping**: `WorldTheme.forStage(stage)` returns `(stage - 1) / 30` (integer division, clamped to [0, 6]) for stages 1-200. Boundary values: 1→0, 30→0, 31→1, 60→1, 61→2, 180→5, 181→6, 200→6. Endless: `stage % 7` (where stage is the generated stage ID, e.g., 131 % 7 = 5). Invalid inputs: `forStage(0)` and `forStage(negative)` throw `IllegalArgumentException`.
