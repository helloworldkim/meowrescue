# Systems Index

| # | System | GDD File | Status | Dependencies |
|---|--------|----------|--------|-------------|
| 1 | Puzzle System | `puzzle-system.md` | Approved | Economy, Progression, Cat Collection |
| 2 | Cat Launch System | `cat-launch-system.md` | Approved | Progression, Cat Collection, Economy |
| 3 | Progression System | `progression-system.md` | Approved | Puzzle, Cat Launch, Economy |
| 4 | Economy System | `economy-system.md` | Approved | Puzzle, Progression, Cat Launch |
| 5 | Economy Expansion | `economy-expansion.md` | Approved | Economy, Puzzle, Cat Launch, Progression |

## Design Order

1. Economy System (foundation — other systems reference coin flow)
2. Progression System (depends on economy; gates content)
3. Puzzle System (core gameplay loop; consumes economy + progression)
4. Cat Launch System (parallel core loop; consumes progression)

## Status Legend

- **Not Started** — No GDD exists
- **In Progress** — GDD being authored
- **Reverse-Documented** — GDD generated from existing code (needs formal review)
- **In Review** — Under /design-review
- **Approved** — Passed design review
