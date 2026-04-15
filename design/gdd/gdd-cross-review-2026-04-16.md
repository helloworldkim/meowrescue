# Cross-GDD Review Report — 2026-04-16 (Re-review)

**GDDs Reviewed**: 5
**Systems Covered**: Economy System, Progression System, Puzzle System, Cat Launch System, Economy Expansion
**Previous Review**: 2026-04-15 (Verdict: FAIL, 5 blockers)

---

## Consistency Issues

### Blocking

**B-1: Economy GDD Balance Snapshot uses 30 instead of 50 for Launch first-clear bonus**
- File: `economy-system.md`, line 174
- Text: `50 * 30 first-clear bonus = 1,500`
- Should be: `50 * 50 first-clear bonus = 2,500`
- Impact: Understates Launch income by 1,000 coins. Stated surplus ratio 8.8x is actually 9.2x. Propagates to Economy Expansion's ~24,000 income assumption.
- Fix: Change 30 → 50 and recalculate downstream totals.

**B-2: Economy Expansion overview table contradicts its own Section C**
- File: `economy-expansion.md`, lines 26-29 vs lines 64-68 and 100-106
- Overview table: Cosmetics "~5,200", Total "~12,300"
- Detailed math: Cosmetics 5,850, Total 12,950
- Fix: Update overview table to match Section C math (5,850 cosmetics, 12,950 total).

**B-3: Cat Launch parity table says achievements "Not Started" but code implements all 3 triggers**
- File: `cat-launch-system.md`, line 35
- `checkLaunchAchievements()` in `LaunchGameActivity.kt` now calls `unlockAchievement()` for launch_10, launch_1cat, launch_tnt.
- Fix: Update parity table to "3/3 existing implemented; 8-10 target remains Not Started."

### Warnings

**W-1: Star coin rewards triple-owned** (Economy Section G, Puzzle Section D, Economy Section G again for Launch)
**W-2: Power-up costs dual-owned** (Economy Section G, Puzzle Section G)
**W-3: First-clear bonus dual-owned** (Economy Section G ×2, Puzzle Section D)
**W-4: World theme boundaries dual-owned** (Progression Section G, Puzzle Section G/H)
**W-5: Achievement coin rewards dual-owned** (Economy Section G, Progression Section G)
- Recommendation: Economy GDD = sole owner of all coin/reward values. Progression GDD = sole owner of world boundaries and achievement definitions. Others use cross-references.

**W-6: Economy Expansion income total (~24,000) has no derivation**
- No explicit Launch stage count assumption. Economy GDD's calculation has the B-1 error.
- Recommendation: Add income derivation to Economy Expansion Section D with stated assumptions.

**W-7: Skip + first-clear interaction under-specified**
- Does skip permanently consume the first-clear bonus? AC-5 says "isFirstClear forced false" but writes a completion record.
- Recommendation: Clarify in edge cases whether skip preserves or consumes first-clear opportunity.

**W-8: Cat Launch bestScore stored as `stars * 50` placeholder** (code) vs rich scoring formula (GDD)
- `GameRepository.saveLaunchProgress()` uses `val score = stars * 50`.
- Recommendation: Implement GDD formula or document placeholder as intentional.

**W-9: Puzzle GDD does not explicitly state "no first-clear bonus for Endless"**
- Economy GDD states it; Puzzle GDD is silent.
- Recommendation: Add one sentence to Puzzle Endless section.

**W-10: Economy Expansion has no reverse references from base GDDs' dependency tables**
- Recommendation: Add Economy Expansion to dependency tables of base GDDs once it moves to Approved.

---

## Game Design Issues

### Blocking

**D-1: Cat Launch first-clear bonus is an INFINITE uncapped income source**
- Launch stages are unlimited procedural with unique IDs; each earns 50-coin first-clear.
- All economy surplus calculations assume ~50 Launch clears but NO CAP exists in code or design.
- This invalidates the entire economy-expansion balance model.
- Suggested: Cap first-clear to first N unique Launch stages (e.g., 100), or replace with difficulty-based coin multiplier.

**D-2: No formal game-concept.md or game-pillars.md exists**
- No explicit design filter for feature scoping, prioritization, or cut/keep decisions.
- Inferred pillars: Rescue/Collect, Spatial Mastery, Physics Spectacle, Visible Progress.
- Suggested: Create `design/game-pillars.md` ratifying 3-4 pillars.

### Warnings

**D-3: Cat Launch difficulty selector is non-functional**
- Easy/Normal/Hard award identical coins with no separate progress tracking per difficulty.
- Dominant strategy: always play Easy. The selector is dead UI.
- Suggested: Add coin multiplier (1.0x/1.25x/1.5x) or remove selector.

**D-4: Endless mode is an uncapped infinite coin source with no diminishing returns**
- No session cap, no escalating difficulty, no reduced payouts.
- Suggested: Diminishing returns (e.g., halve coins every 10 consecutive clears per session) or daily cap.

**D-5: Cat Launch stage ID collision across difficulty levels**
- Stage 35 Easy and stage 35 Hard share one progress record.
- First-clear bonus consumed by first difficulty played; Hard play invisible if Easy played first.
- Suggested: Encode difficulty into stage ID (e.g., stageId * 10 + difficultyLevel).

**D-6: Skip → cat unlock interaction unspecified**
- Skipping stage 150 could unlock cat #10 (Charge). Intended?
- Binary design decision needed before implementation.

**D-7: Skip → achievement interaction unspecified**
- Skipping to stage 200 could unlock "Legendary Rescuer" achievement.
- Binary design decision needed before implementation.

**D-8: Cross-mode difficulty not calibrated to cat ability power**
- Explosive cats trivialize early Launch stages regardless of difficulty setting.
- Suggested: Factor unlocked ability tier into Launch stage generation.

**D-9: No coin sink exists within Cat Launch gameplay**
- All current and planned sinks are Puzzle-only (power-ups, hints, skip, themes).
- Cat cosmetics are purchased from Collection screen, not in-context during Launch.

**D-10: Cosmetic storage schema and rendering pipeline unspecified**
- Economy-expansion.md describes behavior but no persistence schema or rendering spec.

**D-11: Economy spending decisions have no weight**
- 8.8x surplus pre-expansion. Post-expansion ratio is fictional due to uncapped Launch income.

---

## Cross-System Scenario Issues

**Scenarios walked**: 5

### Warnings

**SC-1: Launch TNT Clear — Stage ID collision**
Playing stage 35 on Easy consumes the first-clear bonus; later playing on Hard gets 0 bonus. Star rating max-merged makes Hard performance invisible.

**SC-2: Low-Coin Power-Up — No recovery guidance**
"Not enough coins" toast shown but no suggestion how to earn more. Post-expansion this scenario becomes common.

**SC-3: Stage Skip — Cat unlock/achievement interactions undefined**
Skipping stage 150 or 200 may trigger cat unlocks and achievements. Design decision required.

**SC-4: Buy Cosmetic → Launch — Storage and rendering gaps**
Cosmetic persistence schema undefined. Rendering pipeline for accessory overlay on physics projectile unspecified. Cat selection is random, so purchased cosmetic may not appear.

### Info

- Cat unlock on puzzle clear: works correctly, no failure modes.
- Achievement timing: crash between saveProgress and checkAchievements self-heals.
- Cat selection randomness: purchased cosmetics may not appear in next Launch stage.

---

## Previous Review (2026-04-15) Resolution Status

| Finding | Status |
|---------|--------|
| C-1: Launch achievements unreachable | **RESOLVED** |
| C-2: "minigame" terminology | **RESOLVED** |
| C-3: Stale "code fix needed" notes | **RESOLVED** |
| C-4–C-7: Tuning knob dual-ownership | UNRESOLVED |
| C-8: Endless first-clear exclusion | UNRESOLVED |
| C-9: Endless theme cycling %7 | UNRESOLVED |
| C-10: Launch bestScore placeholder | UNRESOLVED |
| D-1: Cat Launch subordination | Partially addressed |
| D-2: Economy open loop | Addressed (Expansion designed; arithmetic errors remain) |

---

## GDDs Flagged for Revision

| GDD | Reason | Type | Priority |
|-----|--------|------|----------|
| economy-system.md | Launch first-clear arithmetic wrong (30→50) | Consistency | Blocking |
| economy-expansion.md | Stale overview table; no income derivation; skip interactions | Consistency | Blocking |
| cat-launch-system.md | Parity table stale; uncapped first-clear; difficulty selector | Design | Blocking |
| puzzle-system.md | No explicit Endless first-clear exclusion | Consistency | Warning |
| progression-system.md | Achievement reward ownership overlap | Consistency | Warning |

---

## Verdict: CONCERNS

**Improvement from previous review**: FAIL → CONCERNS. Three original blockers (C-1 achievements, C-2 identity, C-3 stale notes) are RESOLVED. Economy Expansion closes the open loop structurally.

**Remaining blockers**:
- 3 consistency blockers (B-1, B-2, B-3) are quick edits (< 5 min total)
- 2 design blockers (D-1 uncapped income, D-2 no pillars) require design decisions

The consistency blockers do not prevent architecture. The design blockers are decisions, not contradictions — they can be resolved alongside implementation planning.

**Required actions before re-running**:
1. Fix B-1: Economy GDD Launch first-clear 30→50 in formula
2. Fix B-2: Economy Expansion overview table 5,200→5,850, 12,300→12,950
3. Fix B-3: Cat Launch parity table achievement status
4. Decide D-1: Cap Launch first-clear income (design decision)
5. Decide D-2: Create game-pillars.md (when ready)
