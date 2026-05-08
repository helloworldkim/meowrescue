---
status: revised
source: app/src/main/java/com/meowrescue/game/launch/
date: 2026-04-15
revised: 2026-04-15
verified-by: User
---

# Cat Launch System

> **Note**: This document was reverse-engineered from the existing implementation.
> It captures current behavior and clarified design intent. Some sections may be
> incomplete where implementation is partial or intent was unclear.

---

## A. Overview

Cat Launch is a physics-based slingshot game (Angry Birds-style) where the player
flings cats at structures to destroy enemies. It uses JBox2D for 2D rigid-body
physics. The game features 4 obstacle materials, 10 structure templates, 5 cat
abilities, TNT chain explosions, and a procedural stage generator with difficulty
scaling. Cat Launch is an **equal-weight core mode** alongside the Puzzle game —
not a minigame.

### Equal-Weight Parity Requirements

Cat Launch must achieve structural parity with Puzzle mode across these dimensions:

| Dimension | Current State | Target State | Status |
|-----------|--------------|-------------|--------|
| Cat unlocks | Puzzle-only (13 cats gated behind puzzle stages) | Launch-specific unlock path (TBD) | Not Started |
| Coin sinks | None in Launch | Launch-specific sinks (ability boosts, cosmetics) | Not Started |
| First-clear bonus | 30 (lower than Puzzle's 50) | **50** (parity with Puzzle), capped at 50 stages | **Done** |
| Achievements | 3/3 existing triggers implemented | 8-10 Launch achievements (expand from 3) | Partially Done |
| Visual progression | No themes | World themes or visual stage progression | Not Started |
| Difficulty rewards | Easy/Normal/Hard award identical coins | Coin multiplier by difficulty (×1.0/×1.5/×2.0) | **Done** |
| Code terminology | "minigame" throughout codebase | "mode" throughout codebase | **Done** |

> Items marked "Not Started" require dedicated design sessions. Use
> `/design-system launch-progression` and `/design-system economy-expansion`
> when ready.

---

## B. Player Fantasy

The player is a **cat commander** launching a squad of cats to defeat enemies
sheltered behind destructible structures. The satisfaction comes from choosing
the right cat ability for each shot, triggering spectacular chain reactions with
TNT, and clearing stages with minimal cats for maximum stars. The physics
simulation creates emergent destruction moments that feel rewarding and
unpredictable.

---

## C. Detailed Rules

### Physics World

| Property | Value |
|----------|-------|
| World size | 20m × 15m |
| Gravity | (0, -10) m/s² |
| Time step | 1/60s |
| Velocity iterations | 8 |
| Position iterations | 3 |
| Ground height | 0.5m (static body) |

### Slingshot

| Property | Value |
|----------|-------|
| Anchor position | (2.0, 2.5) meters |
| Power factor | 10.0× pull vector |
| Max launch speed | 12.0 m/s (clamped) |
| Projectile radius | 0.25m (default) |

The player drags to aim (pull-back gesture), release launches the cat. The impulse
is `pullVector * POWER_FACTOR`, clamped to `MAX_LAUNCH_SPEED`.

### Obstacle Materials

| Material | Max HP | Density | Score Value | Color |
|----------|--------|---------|-------------|-------|
| Wood | 30 | 0.5 | 100 | Brown |
| Glass | 15 | 0.3 | 50 | Light blue |
| Stone | 60 | 1.2 | 200 | Gray |
| TNT | 10 | 0.3 | 150 | Red |

All obstacles have: friction 0.6, restitution 0.1, linear damping 0.5, angular damping 0.8.

### Enemies

| Property | Value |
|----------|-------|
| Shape | Circle |
| HP | 20 |
| Density | 0.8 |
| Score value | 500 per kill |
| Linear damping | 0.3 |
| Angular damping | 0.5 |

### Damage System

```
damage = collisionImpulse * DAMAGE_MULTIPLIER (10)
```

Damage is applied to **both** colliding bodies. Obstacles and enemies are destroyed
when HP ≤ 0. Collision events with impulse < 0.1 are ignored.

### TNT Chain Explosions

When a TNT obstacle is destroyed (HP ≤ 0):
1. Apply blast at TNT position: radius 2.0m, force 50.
2. Blast damage to all dynamic bodies in radius: `force * falloff * 0.5` (integer).
3. Blast force falloff: `1 - (distance / radius)`, applied as impulse.
4. Other TNT blocks hit by the blast may be destroyed, triggering further explosions.
5. `pendingTntExplosions` counter increments for screen shake effects.

### Cat Abilities

| Ability | Display Name | Cats | Activation | Parameters |
|---------|-------------|------|------------|------------|
| Normal | 일반 | 1, 2, 3 | None | — |
| Redirect | 방향전환 | 4, 5 | Tap during flight | Redirects velocity toward tap position at 1.5× speed |
| Split | 분열 | 6, 7 | Tap during flight | Splits into 3 fragments, 30° spread, 0.5× fragment radius |
| Explosive | 폭발 | 8, 9, 12 | Auto on collision | 2.5m blast radius, 80 force |
| Charge | 돌진 | 10, 11, 13 | Passive | 2.0× size, penetrates up to 3 obstacles |

- Abilities are **one-time use** per shot (`abilityUsed` flag).
- Explosive auto-triggers on **any** collision (not tap-activated).
- Charge counts penetrations; destroyed after reaching `maxPenetrateCount`.
- Split destroys the original projectile and creates `fragmentCount` new normal projectiles.

### Structure Templates (10 types)

| Template | Description | Block Count |
|----------|-------------|-------------|
| TOWER | Vertical stack | 5-8 blocks |
| ARCH | Two pillars + cap | 3 blocks |
| PYRAMID | 4-3-2-1 rows | 10 blocks |
| BRIDGE | Two pillars + span | 3 blocks |
| FORTRESS | Box enclosure (4 walls) | 4 blocks |
| TALL_TOWER | Wide base + alternating layers + cap | 8-10 blocks |
| CASTLE | 2-floor structure with rooms | 7 blocks |
| DOUBLE_ARCH | 3 pillars + 2 caps | 5 blocks |
| PLATFORM_STACK | 3 platforms with pillar supports | 9 blocks |
| L_SHAPE | 3-wide base + vertical arm | 6 blocks |

Structures are placed between X = 8.0m and X = 19.0m with even spacing.

### Difficulty Tiers

| Stage Range | Cats | Enemies | Max Structures | Materials | Templates | TNT Chance |
|-------------|------|---------|----------------|-----------|-----------|------------|
| 1-10 | 3 | 2 | 2 | Wood, Glass | Tower, Arch | 0% |
| 11-25 | 4 | 3 | 3 | Wood, Glass | +Pyramid, Tall Tower | 0% |
| 26-50 | 4 | 5 | 3 | +Stone | +Bridge, Double Arch, Platform Stack | 10% |
| 51-100 | 5 | 6 | 4 | All (no TNT base) | All 10 templates | 15% |
| 101+ | 6 | 8 | 5 | All (no TNT base) | All 10 templates | 20% |

TNT is inserted by replacing non-base blocks (blocks with `offsetY > 0`) at the specified chance rate.

### Difficulty Selector

| Level | Offset | Coin Multiplier | Description |
|-------|--------|----------------|-------------|
| Easy (쉬움) | +0 | ×1.0 | Wood/Glass focus, fewer enemies |
| Normal (보통) | +25 | ×1.5 | Stone appears, varied structures |
| Hard (어려움) | +50 | ×2.0 | All materials/structures, many enemies |

The offset is added to the stage ID when selecting difficulty parameters.
The coin multiplier is applied to star coins: Easy 10/20/30, Normal 15/30/45, Hard 20/40/60.

### First-Clear Bonus Cap

First-clear bonus (50 coins) is awarded for the first **50 unique** Launch stage IDs cleared.
After 50 unique stages, clears earn star coins (with difficulty multiplier) but no first-clear bonus.
Total capped first-clear income: 50 × 50 = 2,500 coins.

### Cat Selection

Cats are selected from the player's unlocked collection. If more cats are available
than needed, a random subset is chosen (seeded by stage). If fewer are available,
cats are repeated.

**Queue preview**: The player sees the **current cat** and the **next cat** in the
launch queue before firing. This enables basic tactical planning ("the next cat is
Explosive — I'll soften this structure with my current Normal cat first"). The full
queue beyond the next cat is hidden, preserving some adaptation challenge.

- The queue is displayed as two cat icons near the slingshot (current = large, next = small).
- After launching, the queue shifts: next becomes current, and a new next is revealed.
- On the final cat, the "next" slot is empty.

### Settling & Win/Lose

- **Settled**: All dynamic bodies have velocity < 0.2 m/s for 15 consecutive
  physics frames (~0.25s at 60 Hz).
- **Settling timeout**: If no settling occurs within **10 seconds** (600 physics
  frames) after the last cat launch, all dynamic body velocities are forced to zero
  and win/lose is evaluated immediately. This prevents permanent hangs from
  physics oscillation or balanced-edge scenarios.
- **Stage Clear**: All enemies destroyed after settling.
- **Stage Fail**: All cats used and enemies remain after settling.
- Out-of-bounds bodies (x < -0.5, x > 20.5, y < -1) are auto-removed.

### Scoring

```
obstacleScore = material.scoreValue (per destroyed obstacle)
enemyScore = 500 (per destroyed enemy)
remainingCatBonus = remainingCats * 1000 (awarded after stage clear)
totalScore = sum(obstacleScores) + sum(enemyScores) + remainingCatBonus
```

### Star Thresholds

Stars are based on **cats used** relative to `catCount`:
```
3★ threshold = ceil(catCount * 0.4)  (use ≤ this many cats)
2★ threshold = ceil(catCount * 0.7)
1★ threshold = catCount              (use all cats)
```

### Debris Particles

When an obstacle is destroyed, 6 debris particles spawn with:
- Radial velocity (1.5-3.5 m/s) + upward bias (+1 m/s Y)
- Random rotation and rotation speed
- Life: 1.0, decays at 1.5× per second
- Material-colored for visual variety

---

## D. Formulas

### Launch Impulse

```
impulse = pullVector * POWER_FACTOR (10.0)
speed = clamp(impulse.length, 0, MAX_LAUNCH_SPEED (12.0))
```

### Collision Damage

```
damage = maxNormalImpulse * DAMAGE_MULTIPLIER (10)
```

Applied to both bodies in the collision.

### Blast Damage & Force

```
falloff = max(0.0, 1.0 - (distance / blastRadius))
blastDamage = floor(blastForce * falloff * 0.5)
impulseVector = direction.normalized * blastForce * falloff
```

Note: `max(0, ...)` clamp ensures bodies at or beyond `blastRadius` receive zero
damage and zero impulse. The query also skips `distance > radius` as an early-out.

### TNT Blast Parameters

```
TNT_BLAST_RADIUS = 2.0m
TNT_BLAST_FORCE = 50
```

### Explosive Cat Blast Parameters

```
EXPLOSIVE_BLAST_RADIUS = 2.5m
EXPLOSIVE_BLAST_FORCE = 80
```

### Redirect Velocity

```
direction = normalize(tapWorldPos - catPosition)
redirectSpeed = originalSpeed * 1.5
newVelocity = direction * redirectSpeed
```

### Star Threshold Calculation

```
threeStar = ceil(catCount * 0.4)
twoStar = ceil(catCount * 0.7)
oneStar = catCount
```

---

## E. Edge Cases

1. **Explosive auto-trigger**: Explosive cats detonate on ANY collision, including the ground. Players must aim carefully.
2. **TNT chain loop**: Multiple adjacent TNTs can chain-react. The system processes destruction sequentially within a single `step()`, so chains resolve in one frame burst.
3. **Charge penetration limit**: After 3 penetrations, the Charge cat is destroyed. If it hits multiple bodies in one physics step, each counts separately.
4. **Split fragments inherit velocity**: Fragments spread from the original's velocity angle, not from a fixed direction. Speed is preserved.
5. **Redirect 1.5× speed boost**: The redirected cat moves faster than its original speed, making it more powerful post-redirect.
6. **No cat overlap**: Cats are launched one at a time. The next cat is only available after the previous shot settles.
7. **Empty cat pool**: If `unlockedCatIds` is empty, all cats default to ID 0 (Normal ability).
8. **Out-of-bounds cleanup**: Bodies that fall below y=-1 or exit the world horizontally are destroyed silently (no score awarded for OOB enemies).
9. **TNT never as base block**: TNT insertion skips blocks at `offsetY ≤ 0.01` to prevent structural collapse before the player acts.
10. **Redirect CCD**: At redirect speeds above 15.0 m/s, continuous collision detection (CCD) is enabled on the cat body to prevent tunneling through thin obstacles. CCD is set at body creation time via `body.isBullet = true` for Redirect cats.
11. **Settling timeout**: If physics bodies oscillate without settling within 10 seconds after the last cat launch, velocities are forced to zero and win/lose is evaluated. This prevents permanent game hangs from balanced-edge or low-energy oscillation scenarios.
12. **Zero-force launch**: If the player releases the slingshot with zero pull distance, the cat drops from the anchor position under gravity. This counts as a used cat and the shot proceeds normally to settling.

---

## F. Dependencies

| System | Relationship |
|--------|-------------|
| **Progression System** | Reads unlocked cats for selection; writes stage clear / star count |
| **Cat Collection** | Cat IDs determine which abilities are available |
| **Economy System** | Shared coin pool; awards coins on stage clears (10/20/30 + 50 first-clear) |
| **Progression System (achievements)** | Triggers launch-specific achievements (launch_10, launch_1cat, launch_tnt) |
| **Economy Expansion** | Cat cosmetics render on projectile sprites |
| **Sound System** | Launch SFX, collision sounds, explosion effects |
| **Puzzle System** | Independent sibling mode; shares cat collection |

---

## G. Tuning Knobs

| Parameter | Current Value | Location |
|-----------|--------------|----------|
| World dimensions | 20×15m | `LaunchPhysicsWorld` companion |
| Gravity | (0, -10) | `GRAVITY` |
| Slingshot position | (2.0, 2.5) | `SLINGSHOT_X/Y` |
| Power factor | 10.0 | `POWER_FACTOR` |
| Max launch speed | 12.0 | `MAX_LAUNCH_SPEED` |
| Settled threshold | 0.2 m/s | `SETTLED_VELOCITY_THRESHOLD` |
| Damage multiplier | 10 | `DAMAGE_MULTIPLIER` |
| Debris per obstacle | 6 | `DEBRIS_PER_OBSTACLE` |
| TNT blast radius | 2.0m | `TNT_BLAST_RADIUS` |
| TNT blast force | 50 | `TNT_BLAST_FORCE` |
| Enemy HP | 20 | `createEnemy()` |
| Enemy score | 500 | `SCORE_ENEMY` |
| Remaining cat score | 1000 | `SCORE_REMAINING_CAT` |
| Material HP/density/score | See materials table | `ObstacleMaterial` enum |
| Split fragment count | 3 | `CatAbility.Split` |
| Split spread angle | 30° | `CatAbility.Split` |
| Explosive blast radius | 2.5m | `CatAbility.Explosive` |
| Explosive blast force | 80 | `CatAbility.Explosive` |
| Charge size multiplier | 2.0× | `CatAbility.Charge` |
| Charge max penetrations | 3 | `CatAbility.Charge` |
| Difficulty tier boundaries | 10/25/50/100 | `LaunchStageGenerator.getDifficultyParams()` |
| TNT chance per tier | 0/0/10/15/20% | `getDifficultyParams()` |
| Structure X range | 8.0-19.0m | `STRUCTURE_X_MIN/MAX` |
| Difficulty offsets | 0/25/50 | `LaunchDifficulty` enum |
| Difficulty coin multipliers | 1.0/1.5/2.0 | `LaunchDifficulty` enum |
| First-clear bonus cap | 50 unique stages | `GameRepository.saveLaunchProgress()` |
| Ability speed cap | 18.0 m/s | `CatAbility.Redirect` (max post-redirect speed) |
| CCD threshold | 15.0 m/s | `LaunchPhysicsWorld` (enable bullet mode above this) |
| Settling timeout | 10.0 seconds | `LaunchPhysicsWorld` (force-settle after this duration) |

---

## H. Acceptance Criteria

### Launch & Physics
1. **Speed clamp (launch)**: At slingshot release, `body.linearVelocity.length()` must be clamped to `MAX_LAUNCH_SPEED` (12.0) regardless of pull distance. Post-launch abilities (Redirect) may produce velocities up to `ABILITY_SPEED_CAP` (18.0). At speeds above 15.0 m/s, continuous collision detection (CCD) must be enabled on the cat body to prevent tunneling through thin obstacles.
2. **Damage symmetry**: On collision with impulse > 0.1, `damage = impulse * 10` is applied to **both** bodies independently.
3. **Settling detection**: `isSettled()` returns true when (a) all dynamic body velocities are < 0.2 m/s for 15 consecutive physics frames (~0.25s at 60 Hz), OR (b) 10 seconds (600 frames) have elapsed since the last cat launch. Condition (b) forces all dynamic body velocities to zero before evaluating win/lose. After either condition triggers, win/lose is evaluated.

### Abilities
4. **One-time activation**: Each ability fires exactly once per shot. After `abilityUsed = true`, subsequent taps or collisions must not re-trigger.
5. **Explosive ground collision**: Explosive cats auto-detonate on ANY collision, including ground. A poorly aimed Explosive that hits the ground first must detonate at ground level, not reach the structure.
6. **Charge penetration**: `penetrateCount` increments once per unique obstacle/enemy body hit. After reaching `maxPenetrateCount` (3), the Charge cat is destroyed. Multiple collision events from the same body in one physics step must count as 1 penetration.
7. **Split symmetry**: Fragments spread at equal angular intervals across `spreadAngleDeg` (30°), centered on the original velocity vector. Fragment count = 3, fragment radius = original × 0.5.
8. **Redirect formula**: `redirectSpeed = originalSpeed * 1.5`. Direction = normalized vector from cat position to tap world position.

### Scoring & Stars
9. **Star thresholds**: Stars are determined by cats used relative to `catCount`:
   - `3★: catsUsed ≤ ceil(catCount * 0.4)`
   - `2★: catsUsed ≤ ceil(catCount * 0.7)`
   - `1★: catsUsed ≤ catCount`
   Edge case: `ceil(3 * 0.4) = 2` and `ceil(4 * 0.4) = 2` — catCount 3 and 4 share the same 3★ threshold (2 cats). This is intentional; players must be efficient regardless of pool size.
10. **Score formula**: `totalScore = sum(material.scoreValue per destroyed obstacle) + 500 per destroyed enemy + remainingCats * 1000`. Score is computed after stage clear, not during play.
11. **OOB no-score**: Enemies destroyed by falling out of bounds (y < -1 or x outside world) must not award the 500 enemy score.

### Coin Rewards
12. **Launch coin awards**: `saveLaunchProgress(stageId, stars, difficulty)` must award `starCoins(stars) * coinMultiplier(difficulty)` plus 50 first-clear bonus (if unique clears < 50) on first completion. Multipliers: Easy ×1.0, Normal ×1.5, Hard ×2.0. Star coins are rounded to nearest integer after multiplication. `totalCoinsEarned` must increase accordingly.
12a. **First-clear cap**: First-clear bonus (50 coins) is only awarded for the first 50 unique Launch stage IDs. After 50 unique clears, `isFirstClear` is forced false regardless of stage history. `getCompletedLaunchCount()` is used to check the cap.

### TNT
13. **Chain propagation**: Destroying a TNT obstacle triggers `applyBlast(pos, 2.0, 50)`. If the blast destroys another TNT, that TNT also triggers a blast. Chains must propagate until no more TNTs are destroyed.
14. **TNT base protection (generation-time)**: TNT insertion during stage generation must skip blocks with `offsetY ≤ 0.01` (structural base blocks). Blast-time damage has no base-block immunity — all dynamic bodies within blast radius take damage regardless of position.

### Achievements
15. **launch_1cat**: Triggers when all enemies are destroyed and `catsUsed == 1` at stage clear.
16. **launch_tnt**: Triggers when `totalTntExplosions >= 3` at stage clear. `totalTntExplosions` accumulates across all chains in the stage and is never reset mid-stage.
