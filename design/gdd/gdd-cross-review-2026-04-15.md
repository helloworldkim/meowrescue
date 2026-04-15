# Cross-GDD Review Report

**Date**: 2026-04-15
**GDDs Reviewed**: 4
**Systems Covered**: Puzzle, Cat Launch, Progression, Economy
**Note**: No `game-concept.md` or `game-pillars.md` exist. Pillar checks used inferred pillars.
**Entity registry**: Empty — run `/consistency-check` to populate.

---

## Consistency Issues

### Blocking (must resolve before architecture begins)

🔴 **C-1: Launch achievements are defined but unreachable**
- Cat Launch GDD AC-15/16, Progression GDD Section C, `AchievementDefs.kt` lines 42-44: define `launch_10`, `launch_1cat`, `launch_tnt` with trigger conditions and coin rewards.
- `LaunchGameActivity.onStageClear()` calls only `saveLaunchProgress()` — zero calls to `unlockAchievement()` anywhere in Launch code.
- These 3 achievements (110 coins total) can never be earned.

🔴 **C-2: Cat Launch identity contradiction — "equal-weight core mode" vs. universal "minigame" treatment**
- `cat-launch-system.md` line 23: "Cat Launch is an **equal-weight core mode** — not a minigame."
- `AchievementDefs.kt` line 41, `GameRepository.kt` line 131, `README.md` lines 12/52/62/64: all say "minigame."
- Economy GDD frames Launch as subordinate (first-clear bonus 30 vs. 50, "lower than Puzzle's" wording).

🔴 **C-3: Economy GDD contains 3 stale "code fix needed" notes for bugs already fixed**
- Edge case 10 (`getEndlessCount()` default): already fixed to 0 in `GameRepository.kt` line 111.
- Edge case 7 (`require(stars in 1..3)`): already implemented at `GameRepository.kt` lines 44, 134.
- Edge case 8 (negative amount guards): already implemented at `GameRepository.kt` lines 176, 182, 189.

### Warnings (should resolve, but won't block)

⚠️ **C-4: Star coin rewards dual-owned** — Economy GDD (Section G) and Puzzle GDD (Section D) both define `{3★:30, 2★:20, 1★:10}`. Economy should be sole owner.

⚠️ **C-5: Power-up costs dual-owned** — Economy GDD and Puzzle GDD both list 30/40/50 in tuning knobs.

⚠️ **C-6: First-clear bonus dual-owned** — Economy GDD and Puzzle GDD both define the 50-coin bonus.

⚠️ **C-7: World theme boundaries dual-owned** — Progression GDD and Puzzle GDD both specify stage-to-world mappings.

⚠️ **C-8: Puzzle GDD omits "no first-clear bonus" for Endless** — Economy GDD states it explicitly; Puzzle GDD is silent.

⚠️ **C-9: Endless theme cycling hardcodes `% 7`** — GDDs all say `stage % 7`; code uses `stage % THEMES.size`. Semantically equivalent today, diverges if themes added.

⚠️ **C-10: Cat Launch `bestScore` stored as `stars * 50`** — does not match the rich scoring formula in Cat Launch GDD Section C. Score persistence is a placeholder.

⚠️ **C-11: Economy GDD AC-6 ice refund precondition too narrow** — says "only cat blocks" vs. Puzzle GDD AC-13's correct "cat/wall/key blocks."

⚠️ **C-12: "Cat Collection" and "Achievement System" listed as top-level dependencies** but exist only as subsections of Progression GDD. Dependency references should say "Progression System (Cat Collection)" etc.

---

## Game Design Issues

### Blocking

🔴 **D-1: Cat Launch is structurally subordinate to Puzzle — "equal-weight" claim is unsupported**
- Cat unlocks: 100% gated behind Puzzle milestones. Cat Launch cannot unlock cats.
- Achievements: 3/30 are Launch-specific (5% of total coin rewards). Achievement trigger code is missing.
- Economy: Puzzle generates ~18,180 coins; Launch ~3,000. First-clear bonus is lower (30 vs 50).
- World themes: Puzzle has 7 themed worlds; Launch has none.
- **Decision needed**: Either commit to making Launch truly equal-weight (add Launch-specific progression, fix achievement triggers, add Launch sinks, add themes) OR reclassify as bonus/secondary mode in the GDD.

🔴 **D-2: Economy has no functional closed loop — 7.8× income-to-sink ratio**
- Sources: Puzzle + Launch + Achievements + Endless = ~23,360+ coins.
- Sinks: Power-ups only = ~2,400 estimated spending.
- Coins accumulate indefinitely with no meaningful spending decision.
- Economy GDD's player fantasy ("scarce enough that power-up usage requires thought") cannot be achieved.
- Planned sinks (cosmetics, cat upgrades, modifiers) are listed but not designed or implemented.

### Warnings

⚠️ **D-3: No unified meta-progression connecting both modes** — Player has no reason to switch between Puzzle and Cat Launch. No cross-mode quests, shared milestones, or complementary incentives.

⚠️ **D-4: Cat Launch difficulty selector offers choice without trade-offs** — Easy/Normal/Hard all award identical coins and stars. Dominant strategy: always play Easy.

⚠️ **D-5: Endless mode is an infinite coin source with no diminishing returns** — Random difficulty (no escalation), no session cap. Becomes exploitable if meaningful sinks are added later.

⚠️ **D-6: Puzzle minMoves caps at stage ~65** — Stages 65-200 (67.5% of the game) share the same cap (16 moves). Difficulty increases via mechanics, not the primary metric.

⚠️ **D-7: Cat Launch experience quality depends on when player first tries it** — Too early (stage 15): only 2 Normal cats, boring. Too late (stage 130+): trivially easy stages.

⚠️ **D-8: Economy serves no inferred design pillar** — Coins don't advance rescue narrative (Pillar 1), don't reward mastery (Pillar 2), and have no Cat Launch sink (Pillar 3).

⚠️ **D-9: "Cat rescuer" vs. "cat commander" fantasies unreconciled** — Puzzle rescues cats; Launch uses them as projectiles. No narrative framing bridges the gap. Low priority for casual mobile.

⚠️ **D-10: World theme journey is Puzzle-only** — 7-world visual progression has no Cat Launch equivalent.

---

## Cross-System Scenario Issues

**Scenarios walked**: 4
1. Puzzle first-clear at cat unlock milestone
2. Cat Launch stage clear (1 cat, with potential achievements)
3. Power-up Ice on non-removable grid
4. Endless mode high-score with achievement unlock

### Blockers

🔴 **Scenario 2 — Cat Launch clear** (Cat Launch + Economy + Progression + Achievement)
- At step 3, `LaunchGameActivity.onStageClear()` saves progress but never calls achievement checking.
- `launch_1cat` (clear with 1 cat) and `launch_tnt` (3+ TNT chains) can never trigger.
- 110 achievement coins are permanently unreachable.

### Info

ℹ️ **Scenario 3 — Ice refund** (Puzzle + Economy)
- Economy GDD AC-6 uses narrower precondition ("only cat blocks") than Puzzle GDD AC-13 ("cat/wall/key blocks"). Same runtime behavior, imprecise documentation.

---

## GDDs Flagged for Revision

| GDD | Reason | Type | Priority |
|-----|--------|------|----------|
| `economy-system.md` | 3 stale "code fix needed" notes (edge cases 7, 8, 10); AC-6 wording too narrow; dual-ownership of coin values | Consistency | Blocking |
| `cat-launch-system.md` | "Equal-weight core mode" claim contradicted by all system integrations; `bestScore` storage mismatch; no achievement trigger code | Consistency + Design | Blocking |
| `puzzle-system.md` | Dual-ownership of coin values/power-up costs; missing "no Endless first-clear bonus" clarification | Consistency | Warning |
| `progression-system.md` | Dual-ownership of world theme boundaries; Cat Collection/Achievement listed as separate systems but documented inline | Consistency | Warning |

---

## Verdict: FAIL

3 blocking consistency issues + 2 blocking design issues must be resolved.

### Required actions before re-running:

1. **Decide Cat Launch identity**: Is it "equal-weight core mode" or "bonus/secondary mode"? Update `cat-launch-system.md` Section A and all code comments accordingly. If equal-weight, design Launch-specific progression.
2. **Fix Launch achievement triggers**: Add `checkAchievements()` calls to `LaunchGameActivity.onStageClear()` for `launch_10`, `launch_1cat`, `launch_tnt`.
3. **Remove 3 stale "code fix needed" notes** from `economy-system.md` edge cases 7, 8, and 10.
4. **Resolve tuning knob ownership**: Designate Economy GDD as sole owner of all coin values. Puzzle GDD and Cat Launch GDD should reference Economy GDD rather than re-defining star coins, first-clear bonuses, and power-up costs.
5. **Design at least one additional coin sink** (or explicitly acknowledge in Economy GDD that the current surplus is intentional and power-ups are the only planned sink). The 7.8× income-to-sink ratio makes the economy non-functional as designed.
