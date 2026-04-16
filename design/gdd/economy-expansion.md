---
status: in-design
source: design decision (economy review B-1)
date: 2026-04-16
---

# Economy Expansion — Coin Sinks

> **Status**: In Design
> **Implements**: Economy closed loop (resolves 9.2x surplus)
> **Prerequisite**: economy-system.md (base economy)

## A. Overview

The Economy Expansion adds 4 coin sink categories to close the economy's open loop
(currently 9.2x surplus). These sinks serve both core modes and create the first
aspirational purchases (items costing 150-500 coins vs. the current max of 50).
The target post-expansion surplus ratio is 1.5-2.5x — generous enough for casual mobile
but scarce enough for spending decisions to matter. The ratio varies by player type:
completionists who buy all hints approach 1.5x; casual players who skip hints land at ~2.4x.

### Sink Categories

| # | Sink | Mode | Price Range | Total Capacity |
|---|------|------|-------------|---------------|
| 1 | Additional Hints | Puzzle | 20 coins/hint | ~4,000 coins |
| 2 | Cat Cosmetics | Both | 100-1,000 coins/variant (tiered by rarity) | ~9,900 coins |
| 3 | Stage Skip | Puzzle | 100 coins/skip | ~700 coins |
| 4 | Grid Themes | Puzzle | 300-500 coins/theme | ~2,400 coins |
| | **Total sink capacity** | | | **~17,000 coins** |

Against ~22,180 total income (puzzle 18,180 + launch 4,000 assuming 50 stages, all 3★),
this yields a ~1.3x surplus ratio — within the 1.5-2.5x target for completionists.
Casual players who skip Legendary cosmetics land at ~1.8-2.0x.

## B. Player Fantasy

Coins transform from a meaningless score counter into a **personal expression budget**.
The player sees a cat cosmetic they want (300 coins), calculates "that's 5 more
stages," and feels motivated to keep playing. When stuck, they weigh "buy a hint (20)
or save for the theme I want (400)?" — the first real spending decision in the game.
The economy fantasy shifts from "number goes up" to "I'm choosing how to spend my
rewards."

## C. Detailed Rules

### Sink 1: Additional Hints (Puzzle Mode)

- 2 free hints per stage (existing behavior, unchanged).
- After free hints are used, additional hints cost **20 coins each**.
- Each additional hint shows the next optimal move from the BFS solver (same as free hints).
- No limit on purchased hints per stage.
- Hints purchased via coins bypass the rewarded ad flow.
- `hintsBought` counter tracked per stage for analytics.

### Sink 2: Cat Cosmetics (Both Modes)

- Each of the 13 cats gets **2 cosmetic variants** (26 total items).
- Variant types: **Color Palette** (recolor) and **Accessory** (hat/scarf overlay).
- Prices scale by cat rarity (unlock order = rarity tier).
- Cosmetics are purely visual — no gameplay effect.
- Unlocked cosmetics persist forever (one-time purchase).
- Cosmetics apply in both Puzzle mode (cat block visual) and Cat Launch mode (projectile sprite).
- A cat must be **unlocked** before its cosmetics can be purchased.
- Purchased via a new **Shop tab** in the Collection screen.

| Tier | Cats | Unlock Stages | Palette | Accessory |
|------|------|--------------|---------|-----------|
| Common | 나비, 봄이, 여름 (#1-3) | 1, 15, 30 | 100 | 200 |
| Uncommon | 가을, 겨울, 솜이, 꽃이 (#4-7) | 45, 60, 75, 90 | 200 | 400 |
| Rare | 하늘, 바다, 무지개, 보석 (#8-11) | 110, 130, 150, 170 | 300 | 600 |
| Legendary | 왕자, 공주 (#12-13) | 185, 200 | 500 | 1,000 |

| Tier | Count | Palette Subtotal | Accessory Subtotal | Tier Total |
|------|-------|------------------|--------------------|------------|
| Common (3) | 6 | 300 | 600 | 900 |
| Uncommon (4) | 8 | 800 | 1,600 | 2,400 |
| Rare (4) | 8 | 1,200 | 2,400 | 3,600 |
| Legendary (2) | 4 | 1,000 | 2,000 | 3,000 |
| **Total** | **26** | **3,300** | **6,600** | **9,900** |

### Sink 3: Stage Skip (Puzzle Mode)

- Player can skip one puzzle stage for **100 coins**.
- Skip awards 1★ completion (minimum) — no first-clear bonus, no score.
- Limit: **1 skip per world** (7 skips maximum across 200 stages).
- Skipped stages can be replayed later for proper stars.
- Skip button appears on the **stage fail** screen (not always visible — only when stuck).
- `skipUsed[worldIndex]` tracked in SharedPreferences.

#### Skip Interaction Rules

Skip is treated as a **real 1★ completion** — no special-case logic needed:

| Interaction | Result | Mechanism |
|-------------|--------|-----------|
| Cat unlock (e.g., skip stage 75 → cat #6) | **YES — triggers** | `saveProgress` writes `completed=true`; `getNewlyUnlockedCat(75)` finds matching cat |
| Progress achievements (clear_30, clear_60…) | **YES — triggers** | `checkAchievements` checks `maxCompletedLevel ≥ threshold`; skip advances this |
| Mastery achievements (star3_10…) | **No — naturally blocked** | Skip awards 1★; 3★ required for mastery achievements |
| Gameplay achievements (speed_5s, no_undo…) | **No — naturally blocked** | Skip has no play data (moves=0, time=0); conditions unmet |
| First-clear bonus (50 coins) | **No — forced blocked** | `isFirstClear` forced false in skip flow |
| Replaying skipped stage later | Full normal play (no first-clear bonus) | Player can earn 2★/3★ and proper score on replay, but first-clear bonus is permanently forfeited (see Edge Case 10) |

### Sink 4: Grid Themes (Puzzle Mode)

- 6 purchasable alternative grid themes beyond the 7 world defaults.
- Themes change: grid background, cell colors, block palette, grid lines.
- Purchased themes can be applied to **any world** (override the default).
- Themes persist as unlocked forever.

| Theme | Price | Description |
|-------|-------|-------------|
| Neon | 300 | Bright neon colors on dark background |
| Pastel | 300 | Soft pastel palette |
| Midnight | 400 | Dark mode with glowing accents |
| Sakura | 400 | Cherry blossom pink palette |
| Ocean | 500 | Deep blue gradient |
| Gold | 500 | Premium gold and black palette |
| **Total** | **2,400** | |

Purchased via a new **Themes** section in Settings or Collection screen.

### Revised Total Sink Capacity

| Sink | Capacity |
|------|----------|
| Hints (est. 200 purchases) | 4,000 |
| Cat Cosmetics (26 items, tiered) | 9,900 |
| Stage Skip (7 max, effective cost 150 each) | 1,050 |
| Grid Themes (6 items) | 2,400 |
| **Total** | **17,350** |

Post-expansion surplus: ~22,180 / 17,350 ≈ **1.28x** (completionist). Casual players
who buy only Common/Uncommon cosmetics: ~22,180 / 10,750 ≈ **2.1x**.

> **Note**: Income assumes 50 Cat Launch stages (all 3★). Launch stages are
> procedurally unlimited — a dedicated Launch player can exceed this. See
> cross-review D-1 for the income cap design decision.

## D. Formulas

```
hintCost = 20 (flat, per hint after 2 free)

cosmeticCost(tier, type) = {
  common:    { palette: 100, accessory: 200 },
  uncommon:  { palette: 200, accessory: 400 },
  rare:      { palette: 300, accessory: 600 },
  legendary: { palette: 500, accessory: 1000 }
}
catTier(catId) = {
  1-3: common, 4-7: uncommon, 8-11: rare, 12-13: legendary
}

skipCost = 100 (flat)
skipStarAward = 1 (minimum completion)
skipFirstClearBonus = 0

themeCost(tier) = { standard: 300, premium: 400, deluxe: 500 }

totalSinkCapacity = hints + cosmetics + skips + themes
  hints    = 200 * 20 = 4,000
  cosmetics = 3*(100+200) + 4*(200+400) + 4*(300+600) + 2*(500+1000) = 9,900
  skips    = 7 * 150 = 1,050  (100 paid + 50 lost first-clear per skip)
  themes   = 2*300 + 2*400 + 2*500 = 2,400
  total    = 4,000 + 9,900 + 1,050 + 2,400 = 17,350

postExpansionSurplusRatio = totalIncome / totalSinkCapacity
  completionist: 22,180 / 17,350 ≈ 1.28x
  casual (common+uncommon only): 22,180 / 10,750 ≈ 2.06x
```

---

## E. Edge Cases

1. **Hint on unsolvable state**: If `solveSteps()` returns null after a Shuffle, no hint is shown and no coins are charged.
2. **Cosmetic for locked cat**: Purchase button is disabled. If somehow called, `spendCoins()` succeeds but the cosmetic is not applied — refund via `refundCoins()`.
3. **Skip on already-cleared stage**: Skip button only appears on fail screen for uncompleted stages. If stage is already cleared, skip is not available.
4. **Skip limit reached**: If `skipUsed[worldIndex] == true`, skip button is hidden for all stages in that world.
5. **Theme applied to Endless**: Purchased themes override Endless mode's cycling themes. Player can revert to default in Settings.
6. **Cosmetic display in Launch**: Cosmetic overlay is applied to the cat's projectile bitmap. If bitmap fails to load, fall back to base cat sprite.
7. **Refund on failed hint**: Same as existing — if solver returns null, `refundCoins(20)` is called.
8. **Skip on cat unlock stage** (e.g., stage 75): Cat #6 unlocks immediately. Congratulations dialog shown. The cat is available in Cat Launch and Collection. Player can replay stage 75 later for proper stars — the cat stays unlocked regardless.
9. **Skip on stage 200**: `clear_200` ("전설의 구조대원") achievement triggers because it checks `maxCompletedLevel >= 200`. This is intentional — the player paid 100 coins and cleared all prior worlds. The achievement title is "Legendary Rescuer," not "Perfect Rescuer."
10. **Skip preserves first-clear opportunity on replay**: Skip writes `isFirstClear = false`, but since `completed` is now `true`, a later replay also gets `isFirstClear = false` (existing record exists with `completed = true`). Net cost of skip: 100 coins paid + 50 coins first-clear bonus permanently lost = **150 effective cost**. This is documented and intentional — it makes skips a meaningful trade-off.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Economy System** | Extends coin sinks; all purchases use `spendCoins()` |
| **Puzzle System** | Hints and skip integrate into PuzzleActivity UI |
| **Cat Launch System** | Cosmetics render on projectile sprites |
| **Progression System** | Cat unlock gates cosmetic purchases; skip writes 1★ completion |
| **UI System** | New Shop tab in Collection, Themes in Settings, skip button on fail screen |

---

## G. Tuning Knobs

| Parameter | Value | Safe Range | Affects |
|-----------|-------|------------|---------|
| Hint price | 20 | 10-50 | Spending frequency; <10 = no decision; >50 = avoidance |
| Common Palette | 100 | 50-150 | First purchase experience; must feel reachable early |
| Common Accessory | 200 | 100-300 | Early-game casual purchase |
| Uncommon Palette | 200 | 100-300 | Mid-game standard purchase |
| Uncommon Accessory | 400 | 200-600 | Mid-game "save up" target |
| Rare Palette | 300 | 200-500 | Late-mid aspiration |
| Rare Accessory | 600 | 400-800 | Late-game aspiration |
| Legendary Palette | 500 | 300-700 | End-game prestige purchase |
| Legendary Accessory | 1,000 | 600-1,500 | Ultimate aspiration target; "worth saving for" |
| Skip price | 100 | 50-200 | Frustration relief vs. devaluing puzzle mastery |
| Skip limit per world | 1 | 0-3 | Total skip capacity; >2 trivializes progression |
| Standard theme price | 300 | 150-400 | Collection completionism pacing |
| Premium theme price | 400 | 250-600 | Mid-tier aspiration |
| Deluxe theme price | 500 | 300-800 | Top-tier aspiration; must feel "worth saving for" |

---

## H. Acceptance Criteria

1. **Paid hint deduction**: Given 0 free hints remaining and balance ≥ 20, tapping hint must call `spendCoins(20)` then show the next optimal move. Balance decreases by 20.
2. **Paid hint insufficient balance**: Given balance < 20, tapping hint shows "코인이 부족합니다!" toast. No hint shown, no coins deducted.
3. **Cosmetic purchase persistence**: After purchasing a Common Color Palette (100 coins) for cat #1, the cosmetic must persist across app restarts. `getCoins()` decreases by 100. Same behavior for all tiers (Uncommon 200, Rare 300, Legendary 500).
4. **Cosmetic locked-cat guard**: Given cat ID 7 not yet unlocked, attempting to purchase its cosmetic must be blocked at the UI level (button disabled).
5. **Skip awards 1★ only**: Given the player taps Skip on an uncompleted stage with 100+ coins: (a) the stage tile in Stage Select shows 1★, (b) coin balance decreases by exactly 100, (c) no first-clear bonus (50 coins) is awarded, (d) no score is recorded for the stage.
6. **Skip limit enforcement**: After using 1 skip in World 3 (stages 61-90), no further skips are available for any stage in World 3.
7. **Theme unlock persistence**: After purchasing a theme, it appears in the theme selector and persists across restarts.
8. **Theme override in play**: Given the Neon theme is purchased and selected, launching any puzzle stage must display the Neon color palette (bright neon on dark background) for grid, cells, and blocks — regardless of which world the stage belongs to. The world's default colors must not appear.
9. **Surplus ratio**: Total first-clear income (22,180) / total sink capacity (17,350) = 1.28x (completionist) to 2.06x (casual). Must remain within 1.0-2.5× range for completionists. Verified by formula when any income source or sink price changes, not at runtime.
10. **No gameplay advantage from cosmetics**: Given cat #1 with Color Palette cosmetic active in Cat Launch: (a) trajectory and collision behavior must be identical to cat #1 without cosmetic on the same stage, (b) no ability stats change. Verified by code review of cosmetic rendering path confirming it is render-only.
11. **Skip triggers cat unlock**: Given stage 75 is uncompleted and cat #6 is locked, skipping stage 75 must call `saveProgress(75, 1, null, 0)` then `getNewlyUnlockedCat(75)` must return cat #6. Congratulations dialog shown.
12. **Skip triggers progress achievements**: Given `maxCompletedLevel == 89`, skipping stage 90 must trigger `clear_90` ("숲 졸업") achievement and award its 50-coin reward.
13. **Skip does NOT trigger mastery/gameplay achievements**: Given skip on any stage, `star3_*` achievements must not trigger (skip awards 1★, not 3★). `speed_*`, `no_undo`, `optimal_clear` must not trigger (no play data).
14. **Skip effective cost = 150 coins**: Skip costs 100 coins and permanently forfeits the 50-coin first-clear bonus. A later replay of the skipped stage must NOT award first-clear bonus (`existing.completed == true` → `isFirstClear = false`).
