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
The target post-expansion surplus ratio is 2-3x — generous enough for casual mobile
but scarce enough for spending decisions to matter.

### Sink Categories

| # | Sink | Mode | Price Range | Total Capacity |
|---|------|------|-------------|---------------|
| 1 | Additional Hints | Puzzle | 20 coins/hint | ~4,000 coins |
| 2 | Cat Cosmetics | Both | 150-300 coins/variant | ~5,850 coins |
| 3 | Stage Skip | Puzzle | 100 coins/skip | ~700 coins |
| 4 | Grid Themes | Puzzle | 300-500 coins/theme | ~2,400 coins |
| | **Total sink capacity** | | | **~12,950 coins** |

Against ~22,180 total income (puzzle 18,180 + launch 4,000 assuming 50 stages, all 3★),
this yields a ~1.7x surplus ratio — within the 2-3x target.

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
- Variant types: **Color Palette** (recolor, 150 coins) and **Accessory** (hat/scarf overlay, 300 coins).
- Cosmetics are purely visual — no gameplay effect.
- Unlocked cosmetics persist forever (one-time purchase).
- Cosmetics apply in both Puzzle mode (cat block visual) and Cat Launch mode (projectile sprite).
- A cat must be **unlocked** before its cosmetics can be purchased.
- Purchased via a new **Shop tab** in the Collection screen.

| Item Type | Count | Price | Subtotal |
|-----------|-------|-------|----------|
| Color Palette (13 cats) | 13 | 150 | 1,950 |
| Accessory (13 cats) | 13 | 300 | 3,900 |
| **Total** | **26** | | **5,850** |

### Sink 3: Stage Skip (Puzzle Mode)

- Player can skip one puzzle stage for **100 coins**.
- Skip awards 1★ completion (minimum) — no first-clear bonus, no score.
- Limit: **1 skip per world** (7 skips maximum across 200 stages).
- Skipped stages can be replayed later for proper stars.
- Skip button appears on the **stage fail** screen (not always visible — only when stuck).
- `skipUsed[worldIndex]` tracked in SharedPreferences.

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
| Cat Cosmetics (26 items) | 5,850 |
| Stage Skip (7 max) | 700 |
| Grid Themes (6 items) | 2,400 |
| **Total** | **12,950** |

Post-expansion surplus: ~22,180 / 12,950 ≈ **1.7x** (within 2-3x target).

> **Note**: Income assumes 50 Cat Launch stages (all 3★). Launch stages are
> procedurally unlimited — a dedicated Launch player can exceed this. See
> cross-review D-1 for the income cap design decision.

## D. Formulas

```
hintCost = 20 (flat, per hint after 2 free)

cosmeticCost(type) = { colorPalette: 150, accessory: 300 }

skipCost = 100 (flat)
skipStarAward = 1 (minimum completion)
skipFirstClearBonus = 0

themeCost(tier) = { standard: 300, premium: 400, deluxe: 500 }

totalSinkCapacity = (200 * 20) + (13 * 150 + 13 * 300) + (7 * 100) + (2*300 + 2*400 + 2*500)
                  = 4,000 + 5,850 + 700 + 2,400 = 12,950

postExpansionSurplusRatio = totalIncome / totalSinkCapacity
                          ≈ 22,180 / 12,950 ≈ 1.7x
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
| Color Palette price | 150 | 80-250 | Mid-game aspiration target |
| Accessory price | 300 | 150-500 | Late-game aspiration target |
| Skip price | 100 | 50-200 | Frustration relief vs. devaluing puzzle mastery |
| Skip limit per world | 1 | 0-3 | Total skip capacity; >2 trivializes progression |
| Standard theme price | 300 | 150-400 | Collection completionism pacing |
| Premium theme price | 400 | 250-600 | Mid-tier aspiration |
| Deluxe theme price | 500 | 300-800 | Top-tier aspiration; must feel "worth saving for" |

---

## H. Acceptance Criteria

1. **Paid hint deduction**: Given 0 free hints remaining and balance ≥ 20, tapping hint must call `spendCoins(20)` then show the next optimal move. Balance decreases by 20.
2. **Paid hint insufficient balance**: Given balance < 20, tapping hint shows "코인이 부족합니다!" toast. No hint shown, no coins deducted.
3. **Cosmetic purchase persistence**: After `spendCoins(150)` for a color palette, the cosmetic must persist across app restarts. `getCoins()` decreases by 150.
4. **Cosmetic locked-cat guard**: Given cat ID 7 not yet unlocked, attempting to purchase its cosmetic must be blocked at the UI level (button disabled).
5. **Skip awards 1★ only**: `saveProgress(stageId, 1, null, 0)` with `isFirstClear` forced false. No first-clear bonus. No score.
6. **Skip limit enforcement**: After using 1 skip in World 3 (stages 61-90), no further skips are available for any stage in World 3.
7. **Theme unlock persistence**: After purchasing a theme, it appears in the theme selector and persists across restarts.
8. **Theme override in play**: When a purchased theme is selected, `PuzzleRenderer` uses the custom palette instead of `WorldTheme.forStage()` colors.
9. **Surplus ratio**: Total first-clear income (puzzle + launch) + achievement income must not exceed 2.5× total sink capacity. Verified by formula, not runtime.
10. **No gameplay advantage from cosmetics**: Cat cosmetics must not change hitbox size, ability parameters, or any gameplay-affecting property.
