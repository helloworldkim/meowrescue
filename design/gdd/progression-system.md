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

### Achievement System

30 achievements across 7 categories. Each has a coin reward.

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

#### Collection (3)

| ID | Title | Condition | Reward |
|----|-------|-----------|--------|
| cat_3 | 고양이 친구 | Unlock 3 cats | 20 |
| cat_7 | 고양이 대가족 | Unlock 7 cats | 50 |
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
- Set after stage 1 tutorial dismiss (or stage 3 tutorial dismiss).
- Once set, no tutorials appear on any stage.

---

## D. Formulas

### Cat Unlock Check

```
newlyUnlockedCat = CAT_DEFINITIONS.firstOrNull { it.requiredStage == clearedStage }
```

Only triggers on first-time clear of a stage that exceeds `prevMaxCompletedLevel`.

### Achievement Coin Reward

```
on unlock(id):
  reward = AchievementDefs.get(id).coinReward
  addCoins(reward)
```

### Endless Stage Generation

```
endlessStage = Random.nextInt(131, 10000)
```

This ensures advanced features (walls, linked blocks, portals, multi-cat) are always possible.

---

## E. Edge Cases

1. **Replaying an already-cleared stage**: Stars and score use `max(new, existing)`. Completion flag stays true. First-clear bonus is NOT re-awarded.
2. **Achievement re-checking**: All progress achievements are checked every clear. The `unlock()` call is idempotent — already-unlocked achievements return false and don't re-award coins.
3. **Cat unlock on replay**: Cat unlocks only trigger when `currentStage > prevMaxCompletedLevel`. Replaying stage 30 after already clearing stage 60 does NOT re-show the unlock dialog.
4. **Endless count persistence**: If the app crashes mid-endless-run, the count up to the last clear is preserved.
5. **Database migration**: v1→v2 adds `launch_progress` table. v2→v3 adds `bestScore` column, `player_stats` table, and `achievements` table. Migrations are additive-only.
6. **Three-star count query**: Counts ALL progress entries with `stars >= 3`, including stages beyond 200 (if any from endless were saved — though endless uses a separate table).

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Puzzle System** | Writes completion data; reads for stage gating |
| **Cat Launch System** | Writes completion data; reads unlocked cats |
| **Economy System** | Awards coins on clear and achievement unlock |
| **UI System** | Stage select reads progress for display; Collection reads unlocked cats |
| **Ad System** | Stage clear triggers ad check |

---

## G. Tuning Knobs

| Parameter | Current Value | Location |
|-----------|--------------|----------|
| Cat unlock stages | 1,15,30,45,60,75,90,110,130,150,170,185,200 | `GameRepository.CAT_DEFINITIONS` |
| Achievement coin rewards | See `economy-system.md` Section G (Economy owns reward values) | `AchievementDefs.ALL` |
| Achievement thresholds | Various (see tables) | `AchievementDefs.ALL` |
| World theme boundaries | 30/60/90/120/150/180/200 | `WorldTheme.forStage()` |
| Endless stage range | 131-10000 | `PuzzleActivity.generateRandomStage()` |
| Tutorial stages | 1, 2, 3 | `PuzzleActivity.loadStage()` |
| Total stage count | 200 | Hard cap in navigation logic |

---

## H. Acceptance Criteria

### Persistence
1. **Stage completion persistence**: `UserProgress` records must survive app restart and process death (Room DB). Test: save progress, kill app, relaunch, verify `getProgress(levelId)` returns saved data.
2. **Best-ever stars**: `saveProgress(levelId, stars)` must store `max(new, existing)` for stars. Replaying with fewer stars must not downgrade. Test: save 3★, save 1★, verify stars == 3.
3. **Best-ever score**: `saveProgress(levelId, stars, score)` must store `max(new, existing)` for score. Test: save 1000, save 500, verify bestScore == 1000.
4. **Endless count persistence**: `endlessCount` (SharedPreferences, default 0) must persist across sessions. `endlessBest` must be the high watermark.
5. **Selected cat persistence**: `getSelectedCatId()` / `setSelectedCatId()` must persist via SharedPreferences across app restarts.

### Cat Collection
6. **Cat unlock timing**: `getNewlyUnlockedCat(clearedStage)` returns a cat only when `clearedStage == cat.requiredStage` AND `currentStage > prevMaxCompletedLevel`. Replaying an already-cleared milestone does NOT re-trigger unlock.
7. **Cat unlock dialog**: Congratulations dialog appears exactly once per cat, on first-time clear of the required stage.
8. **Cat-Puzzle dependency**: All 13 cats are gated behind puzzle stage milestones. Cat Launch progress does NOT unlock cats. This is intentional — Cat Launch uses whatever cats the player has unlocked via puzzle progression.

### Achievements
9. **Achievement idempotency**: `unlockAchievement(id)` awards `coinReward` exactly once. Subsequent calls return false and award 0 coins.
10. **Progress achievements cumulative**: Clearing stage 90 must trigger `clear_1` through `clear_90` (all applicable). Test: clear stage 90 on a fresh install, verify 6 progress achievements unlock.
11. **`two_move` achievement**: Triggers when `moves ≤ 2` on any stage clear. (Changed from `one_move` — all puzzles now guarantee optimalMoves ≥ 2.)
12. **`cat_all` achievement**: Triggers only when `getUnlockedCats().size >= 13` (all cats). Must not trigger at 12 or fewer.
13. **Speed achievements**: `speed_10s` / `speed_5s` use wall-clock time from `stageStartTime`. Time includes any pause/background time — this is a known limitation.
14. **Total achievement rewards**: 30 achievements sum to **2,180** coins. Any change to reward values must update this total in economy-system.md.

### Database
15. **Migration v1→v2**: Creates `launch_progress` table. Existing `user_progress` data preserved.
16. **Migration v2→v3**: Adds `bestScore` column (default 0), creates `player_stats` table (coins=0, totalCoinsEarned=0), creates `achievements` table. Existing data preserved.

### World Themes
17. **Theme mapping**: `WorldTheme.forStage(stage)` returns the correct worldIndex per the documented table. Boundary values: 30→0, 31→1, 60→1, 61→2, ..., 200→6. Endless: `stage % 7`.
