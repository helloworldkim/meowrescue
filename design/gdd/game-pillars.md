---
status: approved
date: 2026-04-16
verified-by: User
---

# Game Pillars — Meow Rescue

> These pillars are the design filter for every feature, scope, and cut/keep decision.
> Every system in the game must serve at least one pillar. A feature that serves no
> pillar is scope creep regardless of how fun it sounds in isolation.

---

## Pillars

### P1: Rescue & Collect (구출과 수집)

> **The player rescues cats and builds a family.**

Collecting all 13 cats is the emotional spine of the game. Each cat unlock is a
milestone moment — a congratulations dialog, a new ability in Cat Launch, a new face
in the Collection screen. The rescue fantasy ("I saved this cat") transforms puzzle
solving from abstract logic into a personal mission.

**This pillar serves:**
- Cat unlock progression (Progression System)
- Cat Collection screen (UI)
- Cat cosmetics as personal expression (Economy Expansion)
- Cat abilities in Cat Launch (Cat Launch System)

**Design test:** Does this feature help the player feel like they're building a cat
family? If not, does it at least not undermine that feeling?

---

### P2: Spatial Mastery (공간 마스터리)

> **The player finds the clever path through a congested grid.**

The core satisfaction of the Puzzle mode is the "aha!" moment — seeing the solution
in a gridlock of blocks. Difficulty comes from spatial reasoning, not from arbitrary
constraints or time pressure. The quality filter ensures every puzzle has a meaningful
challenge (multiple blockers, trapped blocks, cross-axis dependencies).

**This pillar serves:**
- Puzzle generation quality filter (Puzzle System)
- Star rating based on move efficiency (Puzzle System)
- Progressive mechanic introduction (keys → checkpoints → walls → linked → portals → multi-cat)
- Hint system as guided learning, not cheat bypass (Economy Expansion)

**Design test:** Does this feature reward the player for thinking spatially? Does it
make the "aha!" moment more or less likely?

---

### P3: Physics Spectacle (물리 스펙터클)

> **The player launches cats and watches structures collapse in emergent, unpredictable ways.**

Cat Launch's satisfaction comes from the physics simulation creating moments the player
didn't fully plan — a chain of TNT explosions, a structure toppling sideways into another,
a Charge cat punching through three walls. The 5 cat abilities give the player tools to
create these moments; the physics engine makes each one unique.

**This pillar serves:**
- 10 structure templates with varied destruction patterns (Cat Launch System)
- TNT chain explosions (Cat Launch System)
- 5 distinct cat abilities with different spectacle profiles (Cat Launch System)
- Debris particles and screen shake (Cat Launch System)
- Difficulty-scaled stage generation (more structures = more spectacle potential)

**Design test:** Does this feature create more opportunities for emergent destruction
moments? Does it make the player want to replay a shot just to see what happens?

---

### P4: Visible Progress (눈에 보이는 성장)

> **The player sees their journey from Garden to Castle, with tangible milestones at every step.**

Progress must be visible, not just felt. World themes change every 30 stages, creating
a visual journey. Stars accumulate on the stage select screen. Achievement unlocks pop
with coin rewards. The coin balance grows. Every session should end with the player
seeing they are further along than when they started.

**This pillar serves:**
- 7 world themes with distinct color palettes (Progression System)
- 32 achievements across 7 categories (Progression System)
- Star ratings per stage with best-ever tracking (Puzzle + Cat Launch)
- Coin economy as visible reward signal (Economy System)
- Grid themes and cosmetics as progress expression (Economy Expansion)

**Design test:** After a 10-minute play session, can the player point to something
concrete that changed? A new star, a new cat, a new world, a new achievement?

---

## Anti-Pillars

These describe what the game deliberately does NOT do. Features that drift toward
an anti-pillar should be redesigned or cut.

### AP1: No Competition / PvP (경쟁/PvP 아님)

This is a solo casual game. No leaderboards, no versus mode, no social pressure.
The player's only competition is their own previous best. Features that create
comparison anxiety or time pressure violate this anti-pillar.

### AP2: No Narrative Campaign (서사 캠페인 아님)

The game has no story, no dialogue, no cutscenes. Progression is communicated
through world themes, cat unlocks, and achievement milestones — not through plot.
Features that require reading or narrative comprehension to enjoy violate this anti-pillar.

### AP3: No Pay-to-Win (과금 우위 아님)

Coins buy cosmetics and convenience (hints, skips), never gameplay advantage.
Cat abilities are earned through puzzle progression, not purchased. Cosmetics must
not change hitbox sizes, ability parameters, or any gameplay-affecting property.
Features that let spending money produce better gameplay outcomes violate this anti-pillar.

---

## Pillar Alignment Matrix

| System | P1 Rescue | P2 Mastery | P3 Spectacle | P4 Progress |
|--------|-----------|-----------|--------------|-------------|
| Puzzle | Strong | **Primary** | — | Strong |
| Cat Launch | Medium | — | **Primary** | Medium |
| Progression | **Primary** | — | — | **Primary** |
| Economy | — | Supports (hints) | — | Supports (rewards) |
| Economy Expansion | Supports (cosmetics) | Supports (hints) | — | Supports (themes) |

Every system serves at least one pillar. No system violates an anti-pillar.

---

## Usage

When evaluating a new feature or design decision, ask:
1. **Which pillar(s) does this serve?** — If none, reconsider.
2. **Does it violate any anti-pillar?** — If yes, redesign or cut.
3. **Does it strengthen its pillar or dilute it?** — Prefer features that deepen
   an existing pillar over features that add breadth to multiple pillars weakly.
