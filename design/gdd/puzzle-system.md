---
status: revised
source: app/src/main/java/com/meowrescue/game/puzzle/
date: 2026-04-15
revised: 2026-04-15
verified-by: User
---

# Puzzle System (Cat Rescue Sliding Puzzle)

> **Note**: This document was reverse-engineered from the existing implementation.
> It captures current behavior and clarified design intent. Some sections may be
> incomplete where implementation is partial or intent was unclear.

---

## A. Overview

Meow Rescue is a sliding-block puzzle game where the player moves blocks on a grid
to create a clear path for a 1x1 cat block to reach an exit edge. The game is
inspired by Rush Hour / Klotski but uses a single-cell cat for greater mobility.
Puzzles are procedurally generated with a BFS solver guaranteeing solvability and
measuring optimal move counts. 200 fixed stages span 7 themed worlds, with an
additional Endless mode for infinite replayability.

---

## B. Player Fantasy

The player is a **cat rescuer** guiding trapped cats to freedom. Each puzzle is a
spatial logic challenge where the satisfaction comes from finding the optimal path
through a congested grid. The progression from simple 5x5 grids to complex 7x7
puzzles with portals and linked blocks delivers a layered difficulty curve:
the `minMoves` formula provides a rising baseline through stage ~88 (capped at 16),
while block count, grid size expansion, and progressive mechanic introduction
(walls, linked blocks, portals, multi-cat) sustain challenge through stage 200.
Returning players are rewarded with new mechanics and collectible cats.

---

## C. Detailed Rules

### Grid & Blocks

| Property | Value |
|----------|-------|
| Grid shape | Square (NxN) |
| Grid sizes | 5x5 (stages 1-10), 6x6 (stages 11-50), 7x7 (stages 51+) |
| Cat block | 1x1, can move in both axes |
| Regular blocks | Length 1-3, move only along their orientation axis (length ≥ 2) |
| Wall blocks | 1x1, immovable |
| Key blocks | 1x1, must be placed on lock position to solve |
| Linked blocks | Pair of blocks that move together simultaneously |

### Block Movement

- Blocks slide along their axis (horizontal blocks slide left/right, vertical slide up/down).
- 1-cell blocks can move in **either** axis.
- Movement is discrete (cell-by-cell) but rendered with smooth animation.
- Blocks cannot pass through other blocks or grid boundaries.
- Linked block pairs move in the same direction simultaneously; both must have a clear path.
- Wall blocks cannot be moved by the player.

### Win Condition

The puzzle is solved when **all** of the following are true:
1. The primary cat block is at the exit edge (aligned with `exitRow`/`exitCol` in the correct `exitDirection`).
2. If multi-cat: the second cat is at its respective exit.
3. If key-lock: the key block is on the lock position (`lockRow`, `lockCol`).
4. If checkpoint: the cat has passed through the checkpoint cell during play.

### Exit Directions

Exits can face any of 4 directions: RIGHT, LEFT, BOTTOM, TOP. The exit direction
determines which edge the cat must reach.

### Stage Features (Progressive Unlock)

| Feature | Unlock Stage | Description |
|---------|-------------|-------------|
| 6x6 Grid | 11 | Grid expands from 5x5 to 6x6 (introduced alone, before any new mechanics) |
| Keys & Locks | 16 | A 1x1 key block must reach a lock cell before the puzzle is considered solved |
| Checkpoints | 22 | Cat must pass through a designated cell during its movement path |
| Walls | 51 | 1-3 immovable wall blocks are placed on the grid |
| Linked Blocks | 91 | A pair of blocks move in sync; both must have clearance |
| Portals | 111 | Two portal cells; cat stepping on one teleports to the other |
| Multi-Cat | 131 | Two cat blocks, each with its own exit; both must reach their exits |

Feature selection is deterministic per stage (seeded RNG with `stage * 7919 + 42`).
**Scaffolding principle**: each new mechanic is introduced in isolation. Stages 16-21
have Keys only; stages 22-30 have Checkpoints only; stages 31+ combine them.

### Undo System

- Full move history is tracked via `ArrayDeque<MoveRecord>`.
- Undo restores: block position, linked partner position, portal teleport, checkpoint state.
- Unlimited undos available.
- `undoUsed` flag is tracked for the "no undo" achievement.

### Hints & Auto-Solve

- **Hints**: 2 free per stage. Shows the next optimal move (from BFS solver). Additional hints via rewarded ad or **20 coins each** (see `economy-expansion.md` Sink 1).
- **Auto-Solve**: Plays the full optimal solution automatically. Requires rewarded ad.

### Power-Ups

| Power-Up | Cost | Effect |
|----------|------|--------|
| Magnet | 30 coins | Highlights all movable blocks (toggle) |
| Ice | 40 coins | Removes one random non-cat block from the grid |
| Shuffle | 50 coins | Randomly repositions non-cat blocks (BFS-verified solvable) |

- If Ice finds no removable block, the cost is refunded.
- Shuffle is BFS-verified after each attempt: if the shuffled state is unsolvable,
  the system re-shuffles (up to 5 attempts). If all attempts fail, the original
  grid is restored and the cost is **refunded** via `refundCoins(50)`.
  This matches Ice's refund-on-failure policy — power-ups that produce no effect
  do not consume coins.

### Tutorial

Stages 1-3 show tutorial overlays for first-time players. Tutorial completion is
persisted via SharedPreferences. Stage 1 shows a full tutorial; stages 2-3 show
brief auto-dismissing tips (2.5 seconds).

### Endless Mode

- Generates random stages from the pool `131..10000` (using advanced feature set).
- Tracks consecutive clears (`endlessCount`) and personal best.
- Awards coins per clear (same star-based rates).
- Cycles through all 7 world themes.

---

## D. Formulas

### Difficulty Scaling

```
minMoves(stage) =
  if stage ≤ 5:  stage + 1
  else:          min(16, max(6, floor(sqrt(stage) * 1.5 + 2)))

acceptThreshold = max(2, floor(minMoves * 0.50))
```

The generator accepts a puzzle if `optimalMoves >= acceptThreshold`. This is a
lower bound — puzzles below this floor are discarded and the generator retries.
For stages 1-5 (minMoves 2-6), acceptThreshold = 2, matching the quality filter's
`optimalMoves >= 2` criterion. From stage 12+ (minMoves 7+), acceptThreshold rises
above 2, providing a tighter difficulty floor.

The `max(6, ...)` floor ensures monotonicity at the stage 5→6 boundary (the linear
branch yields 6 at stage 5; the sqrt branch would drop to 5 at stage 6 without it).

### Grid Size by Stage

```
gridSize(stage) =
  stage ≤ 10:  5
  stage ≤ 50:  6   (from stage 11 — grid expansion before feature introduction)
  stage > 50:  7
```

### Block Count Range

| Stage Range | Grid | Blocks Min | Blocks Max |
|-------------|------|-----------|-----------|
| 1-5 | 5x5 | 3 | 5 |
| 6-10 | 5x5 | 5 | 8 |
| 11-30 | 6x6 | 6 | 10 |
| 31-50 | 6x6 | 8 | 12 |
| 51-75 | 7x7 | 9 | 14 |
| 76-100 | 7x7 | 10 | 15 |
| 101+ | 7x7 | 11 | 16 |

### Puzzle Generation Constraints

- **Max accepted depth**: Generated puzzles with BFS solution depth > 45 are rejected
  (`MAX_ACCEPTED_DEPTH = 45`, matching `PuzzleSolver.MAX_BFS_DEPTH`). This ensures
  hints and auto-solve can always find the solution.
- **Fallback BFS verification**: When generation falls back to a hand-crafted layout,
  `optimalMoves` is BFS-confirmed (not fabricated). If the featured fallback has < 2
  moves, a simple fallback is tried. All fallback results use real BFS-derived values.

### Quality Filter (`isQualityPuzzle`)

A generated puzzle must pass these checks to be accepted:

1. `optimalMoves ≥ 2`
2. At least one direct blocker on the cat's exit path has a **different orientation**
   than the exit axis (prevents single-axis-slide solutions)
3. After stage 5: at least 2 direct blockers on the cat's exit path
4. After stage 30: at least 3 direct blockers on the cat's exit path
5. At least one direct blocker must be **trapped** (no free movement in its axis
   without first moving another block)

Puzzles failing these checks are discarded and the generator retries.
Fallback puzzles bypass the quality filter (hand-crafted layouts are pre-validated).

### Block Length Distribution

```
regularBlock: 20% chance length 1, 50% length 2, 30% length 3
blockerBlock: 33% chance length 3, 67% length 2
```

### Star Rating

```
3★: moves ≤ optimalMoves
2★: moves ≤ floor(optimalMoves * 1.5)
1★: moves > floor(optimalMoves * 1.5)
```

### Score

Score serves as a **per-stage personal best** metric supporting P4 (Visible Progress).
It is displayed at the stage-end screen and stored as `bestScore` (best-ever, via
`max(new, existing)`). Higher scores reward efficiency — solving closer to optimal
moves earns more points.

```
star2Limit = floor(optimalMoves * 1.5)
score = max(0, star2Limit - moves + 1) * 100 + stars * 200
```

**Output range**: [200, varies by optimalMoves]
- Minimum (1★, moves >> optimal): 0 × 100 + 1 × 200 = **200** (floor, never lower)
- Optimal clear (3★, moves = optimal): (star2Limit - optimal + 1) × 100 + 600

### Coin Rewards (per clear)

> Coin values owned by Economy System — see `economy-system.md` Section D.
> Star coins: 10/20/30. First-clear bonus: 50. Power-up costs: 30/40/50.

---

## E. Edge Cases

1. **Portal + Checkpoint**: Cat can reach a checkpoint by teleporting through a portal. The solver tracks this.
2. **Linked block deadlock**: If one block of a linked pair is trapped, the other cannot move either. The solver handles this correctly in BFS.
3. **1-cell block ambiguity**: 1-cell blocks can move in either axis, unlike multi-cell blocks. This is intentional for puzzle complexity.
4. **Multi-cat ordering**: The solver checks cat exits by list order (cat 0 = primary, cat 1 = secondary). The two cats are independent.
5. **Key placement distance**: Keys are placed at least Manhattan distance 2 from the lock to prevent trivial solutions.
6. **Generation fallback**: If the generator cannot produce a quality puzzle within the deadline (3s normal, 5s advanced), it falls back to a hand-crafted layout. Fallback puzzles are BFS-verified — `optimalMoves` is the real BFS result, never fabricated.
7. **Depth cap rejection**: Puzzles with BFS solution depth > 45 are rejected during generation. This prevents puzzles that the solver/hints/auto-solve cannot handle.
8. **Ice power-up on empty grid**: If no non-cat blocks exist, the cost is refunded.
9. **Shuffle solvability**: After shuffling, the grid is BFS-verified. If unsolvable, the system re-shuffles (up to 5 attempts). If all fail, the original grid is restored.
10. **Combo system — CUT**: Scaffolded variables (`comboCount`, `maxCombo`) exist in code but have no increment logic and no design. The combo feature is deferred indefinitely. The scaffolded variables should be removed during the next cleanup pass to avoid confusion.
11. **Concurrent generation**: `seedOffsetCache` uses `ConcurrentHashMap` for thread-safe access during preload + on-demand generation.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Economy System** | Consumes coins for power-ups; earns coins from clears |
| **Progression System** | Reads/writes stage completion, stars, best scores |
| **Cat Collection** | Selected cat determines visual; unlocked cats are earned via progression |
| **Progression System (achievements)** | Triggers achievement checks on clear (speed, efficiency, stars, etc.) |
| **Ad System** | Rewarded ads gate hints, auto-solve, and next-stage transitions |
| **Sound System** | World-themed BGM, haptic feedback on moves |
| **Cat Launch System** | Independent sibling mode; shares cat collection and coin economy |
| **Economy Expansion** | Paid hints (20 coins after 2 free), stage skip (100 coins), grid themes |

---

## G. Tuning Knobs

| Parameter | Current Value | Location |
|-----------|--------------|----------|
| Grid sizes per tier | 5/5/6/6/6/7/7/7 | `PuzzleGenerator.difficultyFor()` |
| Min moves formula | `max(6, sqrt(stage)*1.5+2)` | `PuzzleGenerator.difficultyFor()` |
| Min moves cap | 16 | `PuzzleGenerator.difficultyFor()` |
| Accept threshold | 50% of minMoves | `ACCEPT_THRESHOLD = 0.50` |
| Generation deadline (normal) | 3000ms | `GENERATION_DEADLINE_MS` |
| Generation deadline (advanced) | 5000ms | `GENERATION_DEADLINE_ADVANCED_MS` |
| Max outer attempts | 80 | `MAX_OUTER_ATTEMPTS` |
| Max inner attempts | 60 | `MAX_INNER_ATTEMPTS` |
| BFS max depth | 45 | `PuzzleSolver.MAX_BFS_DEPTH` |
| BFS max states | 150K / 250K / 300K | `MAX_STATES_DEFAULT/CHECKPOINT/MULTI` |
| Star thresholds | 1.0x / 1.5x optimal | `PuzzleView` line 456-459 |
| Score multiplier | 100 per move saved | `PuzzleView` line 464 |
| Star bonus | 200 per star | `PuzzleView` line 464 |
| Hint count per stage | 2 | `PuzzleActivity.loadStage()` |
| Power-up costs | See `economy-system.md` Section G | `PuzzleActivity.handlePowerUp()` |
| Max accepted depth | 45 | `PuzzleGenerator.MAX_ACCEPTED_DEPTH` |
| Max shuffle retries | 5 | `PuzzleView.MAX_SHUFFLE_ATTEMPTS` |
| Feature unlock stages | 11(grid)/16(keys)/22(cp)/51/91/111/131 | `PuzzleGenerator.featuresForStage()` |
| Total stages | 200 (+ endless) | Hard cap in `handleNextStage()` |
| Tutorial stages | 1, 2, 3 | `PuzzleActivity.loadStage()` |

---

## H. Acceptance Criteria

### Generation & Solvability

1. **BFS-confirmed solvability**: Every `GenerateResult` returned by `generateWithResult()` must have `solveFast(result.grid) >= 2`. Clamping `optimalMoves` without BFS confirmation is not acceptable. Fallback puzzles must also be BFS-verified.
2. **Depth cap enforcement**: The generator must reject puzzles with `solveFast(grid) > 45` (`MAX_ACCEPTED_DEPTH`). No puzzle with solution depth > 45 may reach the player.
3. **Endless solvability**: Every endless mode puzzle (`stage 131-10000`) must satisfy AC-1. Test: generate 50 consecutive endless stages and assert `solveFast(result.grid) >= 2` for each.
4. **Quality filter**: Generated puzzles must pass `isQualityPuzzle()` criteria (see Section D) before acceptance. Fallback puzzles bypass the filter.

### Feature Progression

5. **Feature gating**: `featuresForStage(stage)` must return `false` for features whose unlock stage > `stage`. The generated `PuzzleGrid` must contain no blocks/properties for locked features. Specifically: no keys before 16, no checkpoints before 22, no walls before 51, no linked blocks before 91, no portals before 111, no multi-cat before 131.
6. **Grid size gating**: Stages 1-10 must use 5x5, stages 11-50 must use 6x6, stages 51+ must use 7x7.
7. **Scaffolded introduction**: Stages 16-21 may have Keys but never Checkpoints. Stages 22-30 may have Checkpoints but never Keys. Stages 31+ may combine both.

### Star & Score

8. **Star formula determinism**: Given identical `moves` and `optimalMoves`, stars are always: `3` if `moves ≤ optimalMoves`; `2` if `moves ≤ floor(optimalMoves * 1.5)`; `1` otherwise. Implemented in exactly one location.
9. **Score formula**: `score = max(0, floor(optimalMoves * 1.5) - moves + 1) * 100 + stars * 200`. Score minimum is **200** (1★ clear, moves >> optimal). Score is displayed at stage end and stored as `bestScore` (best-ever). Unit tests must verify: optimal clear, exact 2★ boundary, and 1★ case (floor = 200).

### Undo

10. **Undo state restoration**: `undoLastMove()` must restore in order: (1) pre-portal position if `didPortal`, (2) block position before move, (3) linked partner position, (4) `checkpointReached = false` if the move set the checkpoint. After undo, `getMoveCount()` decreases by 1.
11. **Combined portal+checkpoint undo**: A move that teleports through a portal AND sets a checkpoint must be fully reversible in a single undo.

### Power-Ups

12. **Shuffle solvability**: After `applyShufflePowerUp()`, `solveFast(grid) >= 1` must hold (intentionally lower than the `>= 2` generation standard — shuffle only needs to guarantee a path exists). If a shuffle produces an unsolvable state, the system re-shuffles (up to `MAX_SHUFFLE_ATTEMPTS = 5`). If all fail, the original grid is restored and `refundCoins(50)` is called. Net balance: unchanged.
13. **Ice refund**: Given a grid with only cat/wall/key blocks (no removable blocks), `handlePowerUp(1)` [Ice, 40 coins] must deduct 40, call `applyIcePowerUp()` → returns `false`, then `refundCoins(40)`, net balance unchanged.
14. **Coin deduction ordering**: Coins are deducted via `spendCoins(cost)` before any power-up effect. If balance is insufficient, no effect is applied.

### Hints & Auto-Solve

15. **Hint from current state**: `solveSteps()` is called on the live grid from `getCurrentGrid()`, not the initial grid. The hint shows the first move of the optimal path from the current position. If `solveSteps()` returns null, no hint charge is consumed.
16. **Auto-solve correctness**: Auto-solve calls `solveSteps()` on the current grid and plays each step. After all steps, `grid.isSolved()` must return true. Player input is disabled during animation.

### World Themes

17. **Theme mapping**: `WorldTheme.forStage(stage)` returns worldIndex: 0 (1-30), 1 (31-60), 2 (61-90), 3 (91-120), 4 (121-150), 5 (151-180), 6 (181-200). Endless: `stage % 7`.

### Performance

18. **BFS time bound**: `solveFast()` and `solveSteps()` must terminate within the BFS state limit (150K/250K/300K). When the limit is reached, the solver returns -1; the generator treats this as generation-failed and uses the fallback path.
19. **Thread safety**: `seedOffsetCache` must be safe for concurrent access from preload and on-demand generation coroutines.
