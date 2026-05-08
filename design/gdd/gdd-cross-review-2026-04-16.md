# Cross-GDD Review Report — 2026-04-16 (Post-Approval Full Review)

**Date**: 2026-04-16
**GDDs Reviewed**: 5
**Systems Covered**: Puzzle System, Cat Launch System, Progression System, Economy System, Economy Expansion
**Pillars**: P1 Rescue & Collect, P2 Spatial Mastery, P3 Physics Spectacle, P4 Visible Progress
**Anti-Pillars**: AP1 No Competition, AP2 No Narrative, AP3 No Pay-to-Win
**Previous Review**: 2026-04-16 (earlier session, Verdict: CONCERNS — all prior blockers now resolved)

---

## Consistency Issues

### Blocking (must resolve before architecture begins)

**🔴 C-1: Income base mismatch — 22,290 vs 22,180**
- `economy-expansion.md` uses **22,180** in Sections A, C, D, and AC#9
- `economy-system.md` defines canonical income base as **22,290** (puzzle 18,290 + launch 4,000)
- Economy System is the declared single source of truth
- The expansion's figure is stale from before the final achievement total (2,290) was locked
- **Fix**: Update 4 locations in economy-expansion.md: lines ~33, ~133, ~169-170, ~233

**🔴 C-2: Economy Expansion overview sink total ~17,000 vs revised 17,350**
- `economy-expansion.md` Section A overview table shows **~17,000** (using skip=700 = 7×100 direct cost)
- The authoritative revised total in Section C is **17,350** (using skip=1,050 = 7×150 effective cost)
- The overview table was never updated after the revised calculation was added
- **Fix**: Update overview table total to ~17,350 and skip row to ~1,050

### Warnings (should resolve, but won't block)

**⚠️ C-3: Game pillars achievement count stale — 30 vs 32**
- `game-pillars.md` P4 says "30 achievements across 7 categories"
- Correct count is **32** (after cat_5 and cat_10 were added)
- **Fix**: Update line 89 of game-pillars.md

**⚠️ C-4: Three missing reciprocal dependencies**
- `economy-system.md`, `cat-launch-system.md`, and `progression-system.md` do not list `economy-expansion.md` in their Dependencies tables
- Economy Expansion lists all three as dependencies in its Section F
- **Fix**: Add Economy Expansion row to each GDD's Dependencies table

**⚠️ C-5: "Achievement System" named as standalone dependency**
- `puzzle-system.md` and `cat-launch-system.md` list "Achievement System" in dependencies
- No such standalone GDD exists — achievements are a subsection of `progression-system.md`
- **Fix**: Rename to "Progression System (achievements)" in both dependency tables

**⚠️ C-6: Puzzle hint section incomplete post-expansion**
- `puzzle-system.md` Section C (Hints) only describes the rewarded-ad path
- Missing: coin-purchased hints (20 coins each) from Economy Expansion Sink 1
- **Fix**: Add cross-reference to economy-expansion.md Sink 1

**⚠️ C-7: No explicit Economy AC for Shuffle refund**
- `economy-system.md` has AC#9 for Ice refund but no corresponding AC for Shuffle refund
- Puzzle GDD specifies `refundCoins(50)` on all-shuffle-attempts-fail
- General `refundCoins` semantics (AC#16) cover it implicitly
- **Fix**: Add AC#9a for Shuffle refund mirroring Ice AC#9

---

## Game Design Issues

### Warnings

**⚠️ D-1: Long-term Endless income uncapped in aggregate**
- Daily cap is 150 coins, but no aggregate/lifetime cap exists
- Over 120 days of daily play: 18,000 Endless coins + 22,290 progression = 40,290 total
- Against 17,350 sinks = 2.32x surplus — exceeds 1.0-2.5x target at ~150 days
- No terminal sink absorbs long-tail income
- **Recommendation**: Monitor post-launch; consider aggregate cap or live-ops sinks if retention exceeds 120 days

**⚠️ D-2: Cat Launch replay income uncapped**
- Unlike Endless (150/day cap), Cat Launch replays have no daily limit
- Hard 3-star replays earn 60 coins each, unlimited
- **Recommendation**: Acceptable for MVP — replay income is low-yield. Revisit if analytics show farming

**⚠️ D-3: One-time sinks exhaust; infinite sources persist**
- After all cosmetics (9,900), themes (2,400), and skips (1,050) are purchased, only consumable sinks remain
- No aspirational spending goals post-completion
- **Recommendation**: Plan post-launch content sinks (seasonal cosmetics, new theme packs) in live-ops design

**⚠️ D-4: No catch-up mechanism for struggling players**
- Players who spend heavily on power-ups/hints early have fewer coins for cosmetics later
- No mercy mechanic (failure bonus, pity timer)
- **Recommendation**: Consider adding a small coin bonus after N consecutive failures on the same stage

**⚠️ D-5: Cat Launch difficulty ignores ability roster**
- Hard mode (+50 offset) assumes ability diversity
- A player at puzzle stage 10 has only 3 Normal cats facing Hard-tier stages
- Cat selection fills with repeated Normal cats — mechanically functional but strategically impoverished
- **Recommendation**: Add UI warning when selecting Hard with < 5 unique ability types unlocked

**⚠️ D-6: "Equal-weight" Cat Launch parity aspirational, not current**
- 4 of 7 parity requirements are "Not Started" (cat unlocks, coin sinks, visual progression, achievements)
- Planning should treat Launch as a dependent secondary mode until parity items are implemented
- **Recommendation**: Acknowledge in architecture that Launch parity is post-MVP scope

### Clean Passes

- **Player attention budget**: 3-4 concurrent systems — within casual mobile limits ✓
- **No dominant strategy** invalidates all others ✓
- **No runaway positive feedback loops** ✓
- **Individual difficulty curves** well-designed for casual mobile ✓
- **All systems serve at least one pillar**; no anti-pillar violations ✓
- **Player fantasies coherent** and mutually reinforcing ✓

---

## Cross-System Scenario Issues

**Scenarios walked**: 5

1. First-time puzzle clear at cat unlock stage (stage 30)
2. Endless mode clear near daily coin cap
3. Shuffle power-up fails on a cat unlock stage
4. Stage skip on a cat unlock stage (stage 75)
5. Cat Launch clear triggering achievements + coins

### Blockers

**🔴 S-1: `cat_5`/`cat_10` achievements unreachable** — Progression + Economy
- The two collection achievements (`cat_5`: 30 coins, `cat_10`: 80 coins) are defined in `AchievementDefs` but never checked in any code path
- `checkAchievements()` checks cat_3/cat_7/cat_all only
- `checkLaunchAchievements()` does the same
- Endless branch checks only endless_10/endless_50
- **110 coins of rewards are permanently inaccessible**
- The 32-achievement total of 2,290 coins can never actually be fully earned
- **Fix**: Add cat_5/cat_10 checks to all achievement check sites

**🔴 S-2: Shuffle refund not implemented** — Puzzle + Economy
- `puzzle-system.md` specifies `refundCoins(50)` on all-attempts-fail
- `PuzzleView.applyShufflePowerUp()` returns `Unit` (void), not `Boolean`
- The caller does not check for failure and does not refund
- Player silently loses 50 coins with no effect
- Contrast: Ice refund IS implemented correctly
- **Fix**: Change return type to Boolean, add refund logic mirroring Ice path

**🔴 S-3: Skip `isFirstClear` mechanism undefined** — Economy Expansion + Progression
- Economy Expansion specifies "isFirstClear forced false in skip flow"
- Current `saveProgress()` calculates `isFirstClear` from DB state — no parameter to force it false
- The skip feature cannot be implemented as specified without a function signature change
- **Fix**: Define mechanism in GDD (new parameter vs. separate method) before architecture

### Warnings

**⚠️ S-4: Endless branch skips economy/collection achievement checks**
- `coins_100`, `coins_1000`, and `cat_*` achievements not checked in Endless path
- Player earning 1000th coin through Endless won't get achievement until a non-Endless clear
- **Fix**: Call shared achievement checks from Endless path

**⚠️ S-5: Power-up count increments on failed/refunded power-ups**
- `incrementPowerUpUseCount()` runs before the effect is attempted
- Failed Shuffle and failed Ice inflate count toward `powerup_10`
- **Decision needed**: Should failed/refunded power-ups count toward the achievement?

**⚠️ S-6: Skip star-coin award internally inconsistent**
- Edge Case 10 calculates effective cost as 150 (100 paid + 50 forfeited) — implies no star coins
- Formula section defines `skipStarAward = 1` — implies 10 coins ARE awarded (effective cost = 140)
- **Decision needed**: Does skip award 10 star coins or not?

**⚠️ S-7: Achievement notification UX for multi-unlock undefined**
- Up to 5 achievements can fire in one stage clear
- No specification for presentation (queue, batch, summary), ordering, or max simultaneous
- **Fix**: Define in UX spec before implementation

**⚠️ S-8: Achievement check order creates within-cycle coin feedback**
- Each `unlockAchievement()` calls `addCoins()`, updating `totalCoinsEarned` mid-cycle
- Later checks (e.g., `coins_1000`) see the updated total
- Order of checks matters but is unspecified
- **Fix**: Define check order or use snapshot of `totalCoinsEarned` at cycle start

### Info

**ℹ️ S-9**: Cat unlock dialog vs achievement dialog ordering is implicit in code, not specified in GDD.

**ℹ️ S-10**: Partial Endless coin award (e.g., 10 of 30) has no specified UI treatment distinct from a full award.

**ℹ️ S-11**: Haptic feedback fires on failed Shuffle, giving false feedback.

**ℹ️ S-12**: Coin display update timing after stage clear is not documented in any GDD.

---

## GDDs Flagged for Revision

| GDD | Reason | Type | Priority |
|-----|--------|------|----------|
| `economy-expansion.md` | Income base 22,180→22,290 (4 locations), overview sink total ~17,000→17,350 | Consistency | Blocking |
| `progression-system.md` | `cat_5`/`cat_10` not in any achievement check code path | Scenario | Blocking |
| `puzzle-system.md` | Shuffle refund not implemented; hint section stale post-expansion | Scenario + Consistency | Blocking + Warning |
| `game-pillars.md` | Achievement count 30→32 | Consistency | Warning |
| `economy-system.md` | Missing expansion dependency; no Shuffle refund AC | Consistency | Warning |
| `cat-launch-system.md` | Missing expansion dependency | Consistency | Warning |

---

## Verdict: CONCERNS

No **design-level** blockers exist — the game's holistic design is sound. Pillar alignment, attention budget, difficulty curves, player fantasies, and economic loops are well-designed for casual mobile.

However, **3 scenario blockers** (S-1/S-2/S-3) and **2 consistency blockers** (C-1/C-2) require resolution:

- **S-1**: `cat_5`/`cat_10` achievements unreachable (110 coins locked, breaks 2,290 total)
- **S-2**: Shuffle refund not implemented (GDD says refund, code does not)
- **S-3**: Skip `isFirstClear` mechanism undefined (cannot implement skip as specified)
- **C-1**: economy-expansion.md income base stale (22,180 → 22,290)
- **C-2**: economy-expansion.md overview sink total stale (~17,000 → 17,350)

These do not prevent architecture from beginning, but should be resolved in parallel with early architecture work. The scenario blockers (S-1/S-2) are concrete code fixes; S-3 and the consistency blockers are GDD updates.

### Required Actions

1. **economy-expansion.md**: Update all instances of 22,180 to 22,290 and overview total to ~17,350
2. **Code**: Add `cat_5`/`cat_10` to all achievement check paths (PuzzleActivity, LaunchGameActivity, Endless branch)
3. **Code**: Implement Shuffle refund (change return type, add refund logic)
4. **economy-expansion.md**: Resolve skip star-coin ambiguity (does skip award 10 coins or not?)
5. **game-pillars.md**: Update achievement count 30→32
6. **All GDDs**: Add missing reciprocal Economy Expansion dependencies

---

## Previous Review Resolution

| Prior Finding | Status |
|---------------|--------|
| B-1: Launch first-clear 30→50 arithmetic | **RESOLVED** (economy-system.md updated) |
| B-2: Expansion overview table stale totals | **PARTIALLY RESOLVED** (cosmetics fixed; skip/income stale) |
| B-3: Launch parity table achievements | **RESOLVED** (status updated) |
| D-1: Uncapped Launch first-clear income | **RESOLVED** (50-stage cap implemented) |
| D-2: No game-pillars.md | **RESOLVED** (game-pillars.md created and approved) |
| D-3: Difficulty selector non-functional | **RESOLVED** (coin multipliers 1.0/1.5/2.0 implemented) |
| D-4: Endless uncapped income | **RESOLVED** (150/day cap implemented) |
| W-1 through W-5: Tuning knob ownership | **RESOLVED** (Economy GDD declared sole owner) |
