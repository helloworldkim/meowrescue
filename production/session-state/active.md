# Session State — 2026-04-20

## Current Task
**Stage advanced: Production → Polish** (gate-check verdict: CONCERNS, accepted with tracked conditions — 2026-04-22).

## Tracked Production Conditions (from gate-check 2026-04-20)
- [x] **ADR-0014**: DONE 2026-04-21 — ADR already Accepted. Migration implemented: IGameRepository + SharedPrefsWrapper + GameRepository + FakeGameRepository + AdManager refactored. `ad_prefs` violation resolved. BUILD SUCCESSFUL, 4/4 validation criteria pass.
- [x] **Untraced TRs (5)**: RESOLVED 2026-04-22 — TR-economy-006/007/009 + TR-prog-004/012 all mapped in `architecture-traceability.md` (ADR-0010/ADR-0011). No further ADR authorship required (TD gate-check confirmation).
- [x] **Memory profiling milestone**: DONE 2026-04-21 — 32 MB ceiling + draw-call cap (56) set in technical-preferences.md. Cat Launch fixed: RGB_565 surface + bitmap decode + DEBRIS_PER_OBSTACLE 6→4. Estimated ~22 MB post-fix.
- [x] **MIGRATION_4_5 — recovery migration**: DONE 2026-05-08 — Story 010 Complete. `Migration4To5.kt` + `AppDatabase.kt` (v5) already implemented. Test coverage added: `MigrationV4V5Test.kt` (5 Robolectric, BUILD SUCCESSFUL) + `MigrationV4V5InstrumentedTest.kt` (4 instrumented, device execution deferred). `build.gradle.kts` updated with `androidTestImplementation` deps.
- [ ] **Core mechanic polish sprint**: juice / audio / haptics / animation-curve pass in Production Sprint 1 (user flagged "Partially — needs polish").
- [ ] **Onboarding clarity pass**: first-2-min UI legibility + tutorial copy review in Production Sprint 1 (user flagged "Partially — could be better").

## Session Extract — /dev-story 2026-04-20
- Story: production/epics/core-puzzle-solver/story-001-solver-solve-fast.md — PuzzleSolver solveFast BFS Core
- Files changed: app/src/main/java/com/meowrescue/game/core/puzzle/PuzzleConstants.kt (created), app/src/main/java/com/meowrescue/game/core/puzzle/PuzzleSolver.kt (created), app/src/test/java/com/meowrescue/game/core/puzzle/PuzzleSolverSolveFastTest.kt (created — 8 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/core/puzzle/PuzzleSolverSolveFastTest.kt
- Blockers: None
- Note: story spec said tests/unit/puzzle/ but Android convention (and existing project) uses app/src/test/; used Android path
- Next: /code-review then /story-done production/epics/core-puzzle-solver/story-001-solver-solve-fast.md

## Session Extract — T-2-3 Memory Profiling 2026-04-21
- Task: Cat Launch memory 35 MB → ≤32 MB
- Files changed:
  - app/src/main/java/com/meowrescue/game/launch/ui/LaunchGameView.kt — holder.setFormat(RGB_565) in init (~8 MB saved)
  - app/src/main/java/com/meowrescue/game/launch/ui/LaunchGameActivity.kt — loadCatBitmaps() uses RGB_565 BitmapFactory.Options (~4.87 MB saved)
  - app/src/main/java/com/meowrescue/game/launch/physics/LaunchPhysicsWorld.kt — DEBRIS_PER_OBSTACLE 6→4 (draw calls 62→52, under 56 cap)
  - .claude/docs/technical-preferences.md — Memory Ceiling 32 MB hard cap + draw-call cap + cat bitmap format docs
- Build: SUCCESSFUL
- Estimated post-fix memory: ~22 MB (surface 8 MB + bitmaps ~5 MB + JBox2D ~5 MB + misc)
- Next: T-2-2 ADR-0014 Sound & Ad SDK Integration (Sprint 2 hard deadline)

## Session Extract — /story-done 2026-04-20
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-puzzle-solver/story-001-solver-solve-fast.md — PuzzleSolver solveFast BFS Core
- Test updates: renamed misleading test + added test_solveFast_budgetAbortsMidSearch_returnsNegativeOne; 9 tests pass
- Tech debt logged: None (advisory deviations noted in story Completion Notes)
- Next recommended: /story-readiness production/epics/core-puzzle-solver/story-002-solver-solve-steps.md

## Session Extract — /dev-story 2026-04-20 (story-002)
- Story: production/epics/core-puzzle-solver/story-002-solver-solve-steps.md — PuzzleSolver solveSteps Full Solution Path Reconstruction
- Files changed: app/src/test/java/com/meowrescue/game/core/puzzle/PuzzleSolverSolveStepsTest.kt (created — 17 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/core/puzzle/PuzzleSolverSolveStepsTest.kt
- Blockers: None — solveSteps was already implemented in migrated Core PuzzleSolver.kt
- Note: story spec said tests/unit/puzzle/ but Android convention used; alreadySolved path returns emptyList() (not null), state-limit abort returns null
- Next: /code-review then /story-done production/epics/core-puzzle-solver/story-002-solver-solve-steps.md

## Session Extract — /story-done 2026-04-20 (story-002)
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-puzzle-solver/story-002-solver-solve-steps.md — PuzzleSolver solveSteps Full Solution Path Reconstruction
- Tech debt logged: None (advisory deviations noted in story Completion Notes)
- Next recommended: Core Puzzle Solver epic is COMPLETE — both stories done. Consider /sprint-plan or next epic.

## Director Panel Verdicts (2026-04-20 gate)
- Creative Director: READY
- Technical Director: CONCERNS (ADR-0014, untraced TRs, config gaps)
- Producer: CONCERNS (budget 6–8 Production sprints; scope-check Core epics before Sprint 2)
- Art Director: CONCERNS (memory ceiling + draw-call cap are load-bearing, not administrative)

## Completed This Session
- [x] Master Architecture (`docs/architecture/architecture.md`)
- [x] ADR-0001: Repository Pattern & Persistence Strategy
- [x] ADR-0002: Database Schema & Migration Path
- [x] ADR-0003: Coin Operation Atomicity & Invariants
- [x] ADR-0004: Threading & Coroutine Model
- [x] ADR-0005: Achievement System Architecture
- [x] ADR-0006: Layer Boundary Enforcement
- [x] Architecture Registry updated (2 API decisions, 5 forbidden patterns)

## Key Decisions
- OQ-2 resolved: Failed power-ups DO count toward powerup_10 (ADR-0005)
- OQ-3 resolved: Achievement checks use totalCoinsEarned snapshot at cycle start (ADR-0005)
- All 6 ADRs Accepted 2026-04-17 (ADR-0001 through ADR-0006)

## Open Questions Remaining (6 of 8 resolved or deferred)
- OQ-1: Skip star coin (0 or 10?) — deferred to ADR-0011
- OQ-4: Multi-achievement UI — deferred to UI spec
- OQ-5: Cosmetic/Theme DB schema — defined in ADR-0002 (v4 migration)
- OQ-6: Cat Launch parity scope — deferred to sprint planning
- OQ-7: Memory ceiling — deferred to perf optimization
- OQ-8: Test framework — deferred to sprint 1

## Files Written
- `docs/architecture/architecture.md`
- `docs/architecture/adr-0001-repository-pattern.md`
- `docs/architecture/adr-0002-database-schema.md`
- `docs/architecture/adr-0003-coin-atomicity.md`
- `docs/architecture/adr-0004-threading-model.md`
- `docs/architecture/adr-0005-achievement-system.md`
- `docs/architecture/adr-0006-layer-boundaries.md`
- `docs/registry/architecture.yaml`

## Next Steps
1. Accept ADR-0001 through ADR-0006 (Proposed → Accepted)
2. Write Feature-layer ADRs (0007, 0008, 0011 are highest priority)
3. Run `/gate-check pre-production` after ADRs are Accepted
4. Run `/create-control-manifest` to produce programmer rules sheet

## Session Extract — /architecture-review 2026-04-17
- Verdict: CONCERNS
- Requirements: 63 total — 15 covered, 9 partial, 39 gaps
- New TR-IDs registered: 63 (full registry populated)
- GDD revision flags: None
- Top ADR gaps: Puzzle Generation (ADR-0007), JBox2D Integration (ADR-0008), Stage Complete Flow (ADR-0011)
- Report: docs/architecture/architecture-review-2026-04-17.md

## Session Extract — /gate-check pre-production 2026-04-17
- Verdict: FAIL
- Artifacts: 4/13 present; Quality: 5/10 passing
- Action taken: Accepted ADR-0001 through ADR-0006; renamed traceability file; scaffolded test infrastructure + CI workflow
- Blockers remaining: art bible, accessibility doc, UX specs, ADR-0007/0008/0011 (Feature-layer)

## Session Extract — /art-bible 2026-04-17
- All 9 sections complete: `design/art/art-bible.md`
- Visual rule locked: "Warm pastels hold the world together; physics lets it unravel with a smile."
- 8-color primary palette, 7 world palettes, all 13 cats individually designed
- Cross-reviewed by ux-designer (Section 7 UX fixes integrated) + technical-artist (Section 8 tech budgets integrated)
- AD-ART-BIBLE sign-off skipped (lean mode)
- Known open items: Cat Launch world themes (target state documented), block-cap deferred to perf testing, legacy asset rename deferred, Memory Ceiling TBD

## Session Extract — accessibility requirements 2026-04-17
- Tier committed: **Standard** — `design/accessibility-requirements.md`
- Derived from art bible commitments + Android-only/touch-only tailoring
- 12 color signals audited, all have non-color backups (art bible 4.5)
- Explicit in-scope additions: colorblind toggle, text size scaling 75-150%, motion reduction mode, 3-bus volume sliders, low-move pulse animation
- Explicit out-of-scope (Comprehensive tier deferred): TalkBack menu support, HUD repositioning, high contrast mode, external audit
- 5 open questions to resolve before v1.7 (tutorial timing, volume bus count, motion reduction UX, colorblind toggle necessity, scanner CI integration)

## Session Extract — HUD design 2026-04-17
- All 7 sections complete: `design/ux/hud.md`
- Philosophy: "Minimal but present, mode-adaptive" — HUD as ambient warmth
- 5 Must-Show elements (coin, pause, move counter, current+next cat, cats remaining)
- Puzzle bottom action strip 72dp (power-ups/hint/undo) vs. Launch floating queue at 90dp from bottom
- Cat queue touch-passthrough during slingshot gesture (resolves UX-review conflict)
- All 9/10 Standard-tier accessibility commitments satisfied (colorblind toggle pending impl)
- 8 open questions tracked; 4 shared with accessibility-requirements.md
- **Status**: APPROVED via `/ux-review` (2026-04-17) with 4 advisories resolved in-place

## Session Extract — Interaction Pattern Library 2026-04-17
- 25 patterns cataloged across 6 groups: `design/ux/interaction-patterns.md`
- 13 patterns crystallized from hud.md review; 12 added for settings + baselines
- Group 1 Buttons (5): Primary, Icon, Power-up, Text Link, Disabled rule
- Group 2 Containers (5): Pill/Chip, Card, Modal Dialog, Toast, Overlay Scrim
- Group 3 Feedback (6): Button Press, Coin Inc/Dec, Star Fill, Low-Threshold Pulse, Value Flash
- Group 4 Emotional Peaks (2): Cat Unlock 3-Beat, Ad Handoff Overlay
- Group 5 Navigation (4): Corner Radius Ladder, Touch-Passthrough, Screen Transition, World Transition
- Group 6 Form Controls (3): Toggle Switch, Slider, Segmented Selector
- Animation Standards consolidated; Sound Standards stubbed for sound-designer
- 5 pattern-specific open questions + 4 cross-references to HUD OQs
- **Status**: APPROVED via `/ux-review` (2026-04-17), 3 advisories documented (Sound Standards deferred, OQ-P3 cost-badge size, OQ-P1 destructive variant)

## Session Extract — Main Menu UX Spec 2026-04-17
- All 15 sections complete: `design/ux/main-menu.md`
- Flow decision: Main Menu → Mode Selector → Stage Select (equal-weight parity for Puzzle + Cat Launch)
- Secondary nav: bottom icon row (Collection / Shop / Settings / Sound)
- Layout: Hero zone (~40% logo + idle cats) / Primary action (~25% Play + optional Endless) / Bottom nav (88dp)
- References all 25 patterns from interaction-patterns.md; flags 1 new pattern (Idle Breathe Animation)
- 7 open questions tracked; 1 blocking (OQ-M7 Mode Selector spec needed before Play button target exists)
- **Status**: APPROVED via `/ux-review` (2026-04-17), 2 advisories (resolution criterion missing, Mode Selector dependency)

## Session Extract — Mode Selector UX Spec 2026-04-17
- All 15 sections complete: `design/ux/mode-selector.md`
- Resolves main-menu.md OQ-M7 (Mode Selector dependency)
- Two co-equal cards (Puzzle / Cat Launch), vertical stacking 720×600dp each, 24dp gap
- Per-card progress preview: Puzzle shows "N / 200 stages cleared" + star count + Endless flag; Launch shows best score + first-clear count + cats unlocked
- No pressure affordances — no pulsing, no last-played highlight, static cards during deliberation
- Enforces equal-weight parity principle (cat-launch-system.md) visually
- Flags 1 new pattern (CNT-02 Full-Width Card Variant) for library addition
- 5 open questions; 1 blocking (OQ-MS5 `getLaunchBestScore()` needs architecture confirmation)
- **Status**: APPROVED via `/ux-review` (2026-04-17), 4 advisories (data dep, pattern library update, art handoff, minor labeling)

## Session Extract — /gate-check pre-production 2026-04-17 (SECOND RUN)
- **Verdict: PASS** (upgraded from FAIL earlier this session)
- Artifacts: 12/13 present + 1 N/A (no game-engine ref docs for Native Android)
- Quality: 11/11 passing
- 0 blockers; 3 advisory notes (Feature-layer ADRs 0007-0014 planned but not yet written)
- Director panel skipped per user decision (pragmatic given artifact coverage)
- Chain-of-Verification: 5 questions checked — verdict unchanged (PASS)
- `production/stage.txt` updated to "Pre-Production"

## Session Extract — Pause Menu UX Spec 2026-04-17

- All 15 sections complete: `design/ux/pause-menu.md`
- Status: Ready for Review (run `/ux-review design/ux/pause-menu.md`)
- Resolves hud.md OQ-8 (pause dialog contents spec)
- **Structure**: CNT-03 Modal Dialog — Header (Paused + Close-X), Stage Info, Resume (BTN-01), Secondary row (Restart/Settings/Help as BTN-02+label), destructive Quit link (BTN-04 Amber Dusk underline)
- **5 forgiving resume affordances**: Resume button, Close-X, scrim tap, pause re-tap, Android back gesture
- **Mode variants**: Puzzle (optimal moves + best star), Cat Launch (difficulty + best score), Endless (count/best, Give Up replaces Restart), Tutorial (Restart disabled)
- **3 new patterns flagged for library update**: BTN-04 destructive variant, BTN-02 Icon+Label variant, CNT-03 Confirm sub-dialog variant
- **2 blocking architecture questions**: OQ-PM1 (back-gesture routing), OQ-PM9 (`worldFromStage()` Repository method)
- 12 open questions total; cross-references to main-menu.md OQ-M3, mode-selector.md OQ-MS5, ADR-0011

## Session Extract — Pause Menu `/ux-review` 2026-04-17

- **Verdict: APPROVED** (0 blocking, 3 advisory)
- Advisories: stage-info world accent null-handling, secondary-row 72dp cell clarification, Endless restart semantics
- Dependencies called out: OQ-PM1 (back-gesture), OQ-PM9 (`worldFromStage()`)

## Session Extract — Stage Select UX Spec 2026-04-18

- All 16 sections complete: `design/ux/stage-select.md`
- Status: Ready for Review (run `/ux-review design/ux/stage-select.md`)
- **Scope**: Puzzle-first unified spec with Cat Launch Deviations section (Option 2). Cat Launch gets Placeholder A (no Stage Select, direct Difficulty Picker → gameplay) for v1.7.
- **Structure**: 4-col × up to 8-row tile grid (72×88dp per art bible § 7.6), world pagination via NAV-04 (chevrons + swipe + dot indicator), 3 tile states (cleared/unplayed-unlocked/locked)
- **Default pagination rule**: Fresh entry → `worldFromStage(maxCompletedLevel + 1)` (no last-viewed persistence)
- **Return-from-clear animation**: FBK-04 Star Fill (full sequence for first clear, single-star for replay improvement) + Warm White outline pulse
- **Endless Mode tile**: world 7 page after `maxCompletedLevel >= 200`; one-time scale-from-0 animation
- **2 new patterns flagged for library**: CNT-02 Endless Mode tile variant, Page indicator dots
- **2 blocking architecture questions**: OQ-SS5 (`getAllStarRatings()` batch), OQ-SS6 (Stage Clear star-signal to Stage Select)
- **35 acceptance criteria across 6 categories**; 11 open questions
- **Spawns new UX spec work**: OQ-SS10 — `/ux-design launch-difficulty-picker` (Placeholder A dependency)

## Current Stage: Pre-Production

## Session Extract — Stage Select `/ux-review` 2026-04-18

- **Verdict: APPROVED** (0 blocking, 4 advisory)
- Advisories: loading skeleton content, pagination dot sizing, tutorial helper SharedPreferences link, swipe threshold value
- Dependencies flagged: OQ-SS5, OQ-SS6 (architecture — batch star reads + clear-signal flow)

## Session Extract — Launch Difficulty Picker UX Spec 2026-04-18

- All 15 sections complete: `design/ux/launch-difficulty-picker.md`
- Status: Ready for Review
- Resolves stage-select.md OQ-SS10 (Placeholder A dep)
- **Structure**: small single-decision screen — CTL-03 (Easy/Normal/Hard) + BTN-01 Start + dynamic context panel (coin multiplier + best score)
- **Default**: Normal on every entry (no persistence — OQ-LDP1 confirmed)
- **Visual**: 70%+ vertical breathing space (Anticipation register, AP1 no-pressure)
- **No new patterns required** — all components use existing library
- **2 blocking architecture questions**: OQ-LDP5 (`getBestLaunchScoresAllDifficulties()` batch method — cross-ref OQ-MS5/OQ-SS5), OQ-LDP6 (Difficulty enum source)
- 24 acceptance criteria; 7 open questions

## Session Extract — Launch Difficulty Picker `/ux-review` 2026-04-18

- **Verdict: APPROVED** (0 blocking, 3 advisory — all sound-designer-related)
- No new patterns required
- 0 UX spec dependencies remaining

## Session Extract — `/gate-check pre-production` 2026-04-18

- **Verdict: PASS** (re-verification of 2026-04-17 gate)
- 13/13 artifacts, 11/11 quality checks passing
- 0 blockers; strengthened by 3 new approved UX specs since prior gate
- Director panel skipped per 2026-04-17 precedent
- Chain-of-Verification: 5 questions checked → verdict unchanged

## Session Extract — Feature-layer ADRs 2026-04-18

**Written + Accepted in order: ADR-0011 → ADR-0007 → ADR-0008**

- **ADR-0011 Stage Complete Flow** (`docs/architecture/adr-0011-stage-complete-flow.md`) — orchestrator pattern (`ProgressionManager.completeStage()`) with `StageResult` input + `StageCompletionOutcome` output. Resolves OQ-PM9, OQ-SS6, OQ-MS5, OQ-LDP5, OQ-LDP6, OQ-PM12 across UX specs.
- **ADR-0007 Puzzle Generation** (`docs/architecture/adr-0007-puzzle-generation.md`) — formalizes existing `PuzzleGenerator`/`PuzzleSolver` as `PuzzleService` facade in Core. Seeded determinism (`stage * 7919 + 42`), fixed-timestep BFS deadlines (3000ms/5000ms), in-memory session cache.
- **ADR-0008 JBox2D Integration** (`docs/architecture/adr-0008-jbox2d-integration.md`) — `PhysicsLoop` render-thread integrator with fixed 1/60s accumulator pattern, `BodyHandle` opaque type, `ScoreEventQueue`, `TntChainDetector`. Resolves OQ-PM10.

**Registry updates** (all committed to `docs/registry/architecture.yaml`):
- State ownership: +1 (current_difficulty_selection)
- Interface contracts: +5 (stage_completion_orchestration, stage_completion_outcome_carry, puzzle_generation_facade, physics_body_lifecycle, launch_score_events)
- API decisions: +3 (stage_completion_entry_point, puzzle_generation_dispatcher, physics_library)
- Forbidden patterns: +7 (per_mode_stage_completion, mid_stage_state_persistence, non_deterministic_puzzle_generation, bypass_puzzle_service, body_mutation_during_step, variable_timestep_physics, raw_body_reference_outside_core)

**Total ADRs now Accepted**: 9 (0001–0008 + 0011). ADRs 0009 (Expansion Storage) and 0010 (Reserved) remain unwritten but are not blocking for Pre-Production.

Next phase work:
- ~~Open **fresh Claude Code session** and run `/architecture-review`~~ — **DONE 2026-04-18**
- Build vertical slice prototype
- Run playtest sessions (≥3)
- Create control manifest, epics, stories
- Batch library update: `interaction-patterns.md` — 5 new patterns (from pause-menu + stage-select): BTN-04 destructive, BTN-02 Icon+Label, CNT-03 Confirm sub-dialog, CNT-02 Endless Mode tile, Page indicator dots

## Session Extract — /architecture-review 2026-04-18

- **Verdict: CONCERNS** (same as 2026-04-17, but coverage jumped 24% → 54%)
- Requirements: 63 total — 34 covered (+19), 11 partial (+2), 17 gaps (−22), 1 N/A
- ADRs Reviewed: 9 (all Accepted: 0001–0008, 0011)
- New TR-IDs registered: None (no new GDD content since 2026-04-17)
- Cross-ADR conflicts: None
- **🔴 GDD↔ADR conflict RESOLVED in-session**: ADR-0011 `computeStars(LaunchClear)` was score-based; GDD AC #9 is cats-based. ADR-0011 revised (R1) to adopt cats-based formula — `LaunchClear.scoreThresholds: ScoreThresholds` replaced with `catCount: Int`, `ScoreThresholds` data class removed, `computeStars` rewritten to `ceil(catCount × 0.4)` / `ceil(× 0.7)`. See ADR-0011 § Revision History.
- GDD revision flags: None (after R1 resolution)
- Engine compatibility: PASS (Native Android, all stable APIs, no post-cutoff surface)
- Top ADR gaps (priority): ADR-0010 Rendering (5 TRs), ADR-0008 amendment (4 Launch runtime TRs), ADR-0007 amendment (3 Puzzle runtime TRs)
- Report: `docs/architecture/architecture-review-2026-04-18.md`
- Traceability index updated: `docs/architecture/architecture-traceability.md`

### Immediate Next Action
Write ADR-0010 (Rendering Architecture) — highest-impact remaining gap. Run `/architecture-decision rendering-architecture` in a fresh session per skill protocol.

## Session Extract — ADR-0010 Rendering Architecture 2026-04-18

- **Written + Proposed**: `docs/architecture/adr-0010-rendering-architecture.md` (328 lines)
- **Decision**: Dual-renderer split with 4 Presentation surfaces:
  1. `PuzzleView` (custom `View` + `Canvas`, invalidate-driven, main thread)
  2. `LaunchView` (`SurfaceView` + dedicated render thread at 60 Hz, ticks `PhysicsLoop` per ADR-0008)
  3. `HudOverlay` (layered Android Views; `CoinChipView` observes `EconomyManager.coins: StateFlow<Int>`)
  4. `ShopView` / `CollectionView` / `Menu*View` (plain `RecyclerView` + Android Views)
- Cross-surface services: `CosmeticBitmapCache` (4 MB LruCache keyed by (catId, cosmeticId)) + `ThemeResolver` (pure object) + `WorldThemeSpec` (data class).
- **Alternatives rejected**: Unified SurfaceView (wastes Puzzle-mode battery), Jetpack Compose (framework weight not justified), per-frame cosmetic composite (threatens 56-drawBitmap ceiling).
- **GDD sync fix**: Renamed ADR data class `WorldTheme` → `WorldThemeSpec` to avoid clash with existing `WorldTheme.forStage(stage): Int` worldIndex helper in `puzzle-system.md` AC-17 and `progression-system.md` AC-17. GDDs untouched.
- **TRs covered**: TR-economy-009 (coin sync), TR-puzzle-015 (world theme render), TR-launch-012 (debris), TR-launch-016 (cat queue preview), TR-expand-005 (cosmetics). Partials closed: TR-prog-012 (theme render), TR-expand-004 (theme override surface), TR-expand-007 (shop UI surface).
- **Registry updates** (all committed to `docs/registry/architecture.yaml` R2):
  - Interface contracts: +3 (world_theme_injection, coin_display_sync, cosmetic_bitmap_rendering)
  - API decisions: +2 (gameplay_render_surface, hud_state_observation)
  - Forbidden patterns: +3 (non_stateflow_coin_display, canvas_types_outside_presentation, per_frame_cosmetic_composite)
- **Risks flagged**: R1 (Launch draw-call ceiling breach at block-cap), R2 (SurfaceView canvas lock starvation), R3 (cosmetic cache thrash on shop preview), R4 (density-bucket outline artefacts).
- **Status**: Proposed (awaiting Accepted transition + fresh-session `/architecture-review`).

**Total ADRs now**: 10 Accepted (0001–0008, 0011) + 1 Proposed (0010). Remaining gaps: ADR-0007 amendment (Puzzle runtime, 3 TRs), ADR-0008 amendment (Launch runtime, 4 TRs), ADR-0009 (Expansion Storage), ADR-0012 (Power-Up Refund), ADR-0013 (Tutorial).

## Session Extract — ADR-0008 R1 Launch Runtime Amendment 2026-04-18

- **Written + Accepted**: `docs/architecture/adr-0008-jbox2d-integration.md` grew 471 → 883 lines (+412 for R1 Revision). Status **Accepted 2026-04-18**.
- **Scope**: Additive amendment covering 4 gap TRs + 3 partials from `architecture-review-2026-04-18.md`.
  - TR-launch-004 explicit: `ContactDamageApplier` + `DAMAGE_MULTIPLIER=10` + `DAMAGE_IMPULSE_MIN=0.1`.
  - TR-launch-006: `sealed class CatAbility { Normal / Redirect / Split / Explosive / Charge }` + `AbilityController` enforcing one-time-use invariant (Charge exception for penetration count).
  - TR-launch-007: `enum StructureTemplate` (10 types) + `StructurePlacement` (X=8.0–19.0m).
  - TR-launch-008: `DifficultyTier` data class + `DifficultyTiers.TIERS` compile-time table (5 tiers at stage boundaries 10/25/50/100/∞).
  - TR-launch-009: `SettlingDetector.scanPostStep()` — 15 consecutive frames |v|<0.2 OR 10s timeout with forced zero velocities.
  - TR-launch-013: `OutOfBoundsCleaner.scanPostStep()` — silent destruction at y<-1 / x outside [-0.5, 20.5]; no `ScoreEvent` emitted.
  - TR-launch-015: `body.isBullet=true` at `CCD_ENABLE_SPEED=15.0 m/s` (Redirect/Charge enable preemptively; never flip back to false mid-flight).
- **Design decisions locked**:
  - Strategy-per-ability sealed class (clean dispatch, one place per ability).
  - New `PostStepRunner` order inside `PhysicsLoop.tick()`: `drain → step → tntChain → abilityDeferred → settling → oob → (next iter's drain)`.
  - Charge is the only ability that legitimately fires more than once per shot.
- **Constants added**: `DAMAGE_MULTIPLIER`, `DAMAGE_IMPULSE_MIN`, `SETTLED_VELOCITY_THRESHOLD`, `SETTLED_FRAMES_REQUIRED`, `SETTLING_TIMEOUT_SECONDS`, `OOB_Y_MIN/X_MIN/X_MAX`, `CCD_ENABLE_SPEED`, `ABILITY_SPEED_CAP`, `SPLIT_FRAGMENT_COUNT/SPREAD/SCALE`, `EXPLOSIVE_BLAST_RADIUS/FORCE`, `CHARGE_MAX_PENETRATIONS/SIZE_MULTIPLIER`.
- **Validation added**: 9 new criteria (Validation #11–19) covering one-time activation, Split symmetry, Redirect+CCD, settling frame count + timeout, OOB silent destruction, damage floor, tier selection, template unlock gating.
- **Registry impact**: None. R1 uses existing `physics_body_lifecycle` and `launch_score_events` contracts; preserves all R0 forbidden patterns. Potential new `mid_shot_ability_retrigger` pattern flagged but deferred.
- **GDD sync**: CLEAN. R1 names (`CatAbility.*`, `EXPLOSIVE_BLAST_RADIUS`, `SETTLED_VELOCITY_THRESHOLD`, `ABILITY_SPEED_CAP`, etc.) all match `cat-launch-system.md` § G Tuning Knobs.

**Total ADRs now**: 11 Accepted (0001–0008 w/R1 + 0011) + 1 Proposed (0010). Remaining gaps: ADR-0007 amendment (Puzzle runtime, 3 TRs), ADR-0009 (Expansion Storage), ADR-0012 (Power-Up Refund), ADR-0013 (Tutorial).

## Session Extract — ADR-0007 R1 Puzzle Runtime Amendment 2026-04-18

- **Written + Accepted**: `docs/architecture/adr-0007-puzzle-generation.md` grew 444 → 814 lines (+370 for R1 Revision). Status **Accepted 2026-04-18**.
- **Scope**: Additive amendment covering 3 Puzzle runtime TRs.
  - TR-puzzle-002: `MoveValidator` (pure, Feature) enforces axis-lock for multi-cell blocks (length ≥ 2) and dual-axis for 1-cell; linked-partner clearance required in the SAME direction.
  - TR-puzzle-005: `WinConditionChecker` (pure fn, Core — alongside PuzzleSolver) returns `sealed class WinState { Won / InProgress / WaitingOnKey / WaitingOnCheckpoint / WaitingOnSecondaryCat }`. Short-circuits on InProgress for hot-path cheapness.
  - TR-puzzle-009: `sealed class MoveRecord` (Feature) + `UndoStack.pop(grid)` with strict restoration order: (1) portal pre-position → (2) block position → (3) linked partner → (4) checkpoint flag. Unlimited undos; `undoUsed` flag sticks for achievement.
- **Layer placement**: Win-check in Core (pure, shared with solver); moves/undo in Feature (mutate live grid). Keeps `bypass_puzzle_service` forbidden pattern intact.
- **Pipeline formalized**: Per-move — validate → apply → win-check → (if Won) fire completeStage. Undo — pop(grid) → engine.moveCount-- → undoUsed=true.
- **Validation added**: 12 new criteria (#10–21) covering axis-lock, dual-axis, linked rejection, all 5 WinState cases, combined portal+checkpoint undo, unlimited undo (500 moves), undoUsed persistence, and solver/checker agreement (100 random grids).
- **Registry impact**: None. Deferred stances `win_condition_check` interface contract and `duplicate_win_check` forbidden pattern — register only if a second consumer appears.
- **GDD sync**: CLEAN. All R1 names (`checkpointReached`, `undoUsed`, `moveCount`, axis / orientation / linked / portal / checkpoint / exit) match `puzzle-system.md`. No GDD revisions needed.

**Total ADRs now**: 12 Accepted (0001–0008 w/R1 + 0007 w/R1 + 0011) + 1 Proposed (0010). Remaining gaps: ADR-0009 (Expansion Storage, 3 TRs + 3 partials), ADR-0012 (Power-Up Refund, 1 TR + 1 partial), ADR-0013 (Tutorial, 1 TR).

## Session Summary (3 ADRs delivered)

| ADR | Status | Lines | TRs Closed |
|-----|--------|-------|-----------|
| ADR-0010 Rendering Architecture | Proposed | 328 (new) | 5 gaps + 3 partials |
| ADR-0008 R1 Launch Runtime | Accepted | +412 | 4 gaps + 3 partials |
| ADR-0007 R1 Puzzle Runtime | Accepted | +370 | 3 gaps |
| **Totals** | | **+1110** | **12 gaps + 6 partials closed** |

Registry grew +118 lines across 3 interface contracts + 2 API decisions + 3 forbidden patterns (all from ADR-0010). R1 amendments added zero new registry stances (by design — R1 is additive detail inside existing contracts).

Remaining gaps from `architecture-review-2026-04-18.md`: 2 TRs (ADR-0009 territory) + 1 TR (ADR-0012) + 1 TR (ADR-0013). Coverage should now be in the ~85% range; re-run `/architecture-review` in a fresh session to confirm.

## Session Extract — /architecture-review 2026-04-18 (run 2)

- **Verdict**: CONCERNS (same tier as run 1, but coverage jumped 54% → 73% Accepted / 81% pending ADR-0010)
- **Requirements**: 63 total — 46 ✅ Covered, 5 🟡 Covered-pending-ADR-0010-Acceptance, 6 ⚠️ Partial, 5 ❌ Gap, 1 N/A
- **ADRs Reviewed**: 10 (9 Accepted: 0001–0008 w/R1 + 0011 w/R1; 1 Proposed: 0010)
- **New TR-IDs registered**: None (no new GDD content since 2026-04-17)
- **Cross-ADR conflicts**: None blocking. One advisory: `architecture.md` EconomyManager interface is stale vs ADR-0010 StateFlow addition (additive, not contradictory).
- **GDD revision flags**: None (ADR-0011 R1 resolved the prior Launch star formula conflict; all R1 amendments were GDD-synced at write time).
- **Engine compatibility**: PASS — all 10 ADRs Native Android Kotlin, LOW risk, no post-cutoff/deprecated APIs.
- **Dependency graph**: No cycles. ADR-0010's dependencies (0004, 0006, 0008, 0011) all Accepted → cleared for Acceptance transition.
- **Top remaining gaps**: ADR-0010 Acceptance (5 TRs), ADR-0009 Expansion Storage (2 gaps + 3 partials), ADR-0012 Power-Up Refund (1 TR), ADR-0013 Tutorial (1 TR).
- **Report**: `docs/architecture/architecture-review-2026-04-18-r2.md`
- **Traceability index updated**: `docs/architecture/architecture-traceability.md` (revision history entry appended)

### Immediate Next Action

**Accept ADR-0010** (Rendering Architecture) — single-action coverage bump from 73% → 81%. Review the Proposed content for implementation readiness, then transition Status `Proposed` → `Accepted (YYYY-MM-DD)` in a fresh session.

Subsequent sequential plan:
1. Write ADR-0009 via `/architecture-decision expansion-storage-purchase-flow`
2. Write ADR-0012 (Power-Up Refund Protocol) and ADR-0013 (Tutorial Overlay) — can be batched
3. Re-run `/architecture-review` after all four actions to confirm PASS verdict
4. Re-run `/gate-check pre-production` once PASS achieved

## Session Extract — ADR-0010 Acceptance 2026-04-18

- **Action**: ADR-0010 Rendering Architecture transitioned `Proposed` → `Accepted 2026-04-18`.
- **File**: `docs/architecture/adr-0010-rendering-architecture.md` Status line updated.
- **Traceability**: `architecture-traceability.md` updated — 5 🟡 TRs promoted to ✅ (economy-009, puzzle-015, launch-012, launch-016, expand-005); coverage line: 51/63 ✅ (81%), 7 ⚠️ Partial, 4 ❌ Gap, 1 N/A; gaps list corrected to 4 active (dropped ADR-0010 pending row); revision history appended.
- **Registry impact**: None — ADR references are path-based, no Status field in `architecture.yaml`.
- **Total ADRs now**: 10 Accepted (0001–0008 w/R1 where applicable, 0010, 0011 w/R1) + 0 Proposed.

### Immediate Next Action

Write ADR-0009 (Expansion Storage & Purchase Flow) via `/architecture-decision expansion-storage-purchase-flow` in a fresh session. Highest-impact remaining gap — resolves 2 ❌ TRs (expand-001, expand-006) + 4 ⚠️ partials (expand-002, 003, 004, 007).

## Session Extract — ADR-0009 Expansion Storage & Purchase Flow 2026-04-18

- **Written + Proposed**: `docs/architecture/adr-0009-expansion-storage-purchase-flow.md` (~440 lines).
- **Decision**: Four Core-layer per-sink services — `HintPurchaser`, `CosmeticPurchaser`, `SkipService`, `ThemePurchaser` — each owning pricing, guards, persistence, and post-effect for its sink. Rejected alternatives: single facade (loses SRP), feature-co-located helpers (violates cross_feature_import / upward_layer_dependency).
- **Schema v3→v4 (expanded from ADR-0002 sketch)**: 3 tables — `cosmetic_purchases(catId, type PK, purchasedAt)`, `selected_cosmetics(catId PK, cosmeticId)` (NEW), `theme_unlocks(themeId PK, purchasedAt)`. `@Entity(primaryKeys=["catId","type"])` + `exportSchema=true` noted.
- **SharedPrefs**: `selected_theme_id` (nullable String), `skip_used_world_0..6` (Boolean × 7).
- **Skip path**: `SkipService.skip` → `ProgressionManager.applySkip(stage)` (ADR-0011 slot). Writes 1★ with best-star rule, forces `isFirstClear=false`, calls single `checkAchievements(SKIP)` per ADR-0005.
- **Pre-Acceptance review (lead-programmer)**: 2 blocking + 3 advisory. Fixes applied in-place: (1) `CosmeticCacheInvalidator` Core interface introduced (Presentation `CosmeticBitmapCache` implements it) to preserve ADR-0006 layer independence; (2) `SkipService.isAvailable` changed from `fun` to `suspend fun` (hits Room); (3) dropped `ThemeResolver.invalidateOverride` call (themes read fresh on Activity onCreate); (4) `getSelected()` changed to non-suspend (SharedPrefs safe on Main); (5) Migration Plan gained `@Entity(primaryKeys=...)` + `exportSchema=true` guidance.
- **Registry updates (R3)** — all committed to `docs/registry/architecture.yaml`:
  - State ownership: +3 (`equipped_cosmetic_per_cat`, `active_theme_selection`, `skip_consumed_per_world`)
  - Interface contracts: +2 (`cosmetic_cache_invalidation`, `stage_skip_application`)
  - API decisions: +2 (`cosmetic_equipped_state_persistence`, `theme_selected_state_persistence`)
  - Forbidden patterns: +2 (`bypass_expansion_purchase_services`, `cosmetic_cache_direct_import_in_core`)
- **GDD sync**: CLEAN — names match `economy-expansion.md` (`hintsBought`, `skipUsed`, `isFirstClear`, tier names, cosmetic types).
- **Status**: Proposed (awaiting independent `/architecture-review` + Accepted transition in a fresh session).
- **TRs addressed on Acceptance**: expand-001, expand-002, expand-003, expand-004, expand-005 (wiring), expand-006, expand-007 — all 7 partials/gaps from `architecture-traceability.md` resolve.

**Total ADRs now**: 10 Accepted (0001–0008 w/R1, 0010, 0011 w/R1) + 1 Proposed (0009).

### Immediate Next Action

1. Open a fresh Claude Code session and run `/architecture-review` to validate ADR-0009 and produce a coverage re-read (should jump 81% → ~92%).
2. Accept ADR-0009 (Proposed → Accepted 2026-04-18 or today's date) after review.
3. Batch-write ADR-0012 (Power-Up Refund Protocol) and ADR-0013 (Tutorial Overlay) — both are scoped to one TR each and can share a session.
4. Re-run `/gate-check pre-production` to confirm PASS with ≥95% coverage.

## Session Extract — /architecture-review 2026-04-18 (run 3)

- **Verdict**: CONCERNS (tier unchanged; coverage 81% → 90% on ADR-0009 Acceptance)
- **Requirements**: 63 total — 51 ✅ Covered, 6 🟡 Covered-pending-ADR-0009-Acceptance, 3 ⚠️ Partial, 2 ❌ Gap, 1 N/A
- **ADRs Reviewed**: 11 (10 Accepted: 0001–0008 w/R1, 0010, 0011 w/R1; 1 Proposed: 0009)
- **New TR-IDs registered**: None (no new GDD content since 2026-04-17)
- **Cross-ADR conflicts**: None blocking. One advisory: ADR-0002 v4 sketch (2 tables, no purchasedAt) diverges from ADR-0009 authoritative v4 (3 tables + purchasedAt). ADR-0009 Ordering Note documents the supersedes. Recommend amending ADR-0002 with pointer to ADR-0009's Migration Plan.
- **GDD revision flags**: None. All ADR-0009 names match `economy-expansion.md`.
- **Engine compatibility**: PASS. All 11 ADRs Native Android / Kotlin / LOW risk / no post-cutoff surface.
- **Dependency graph**: No cycles. ADR-0009's 7 dependencies (0001, 0002, 0003, 0005, 0006, 0010, 0011) all Accepted → cleared for Acceptance transition.
- **Top remaining gaps**: ADR-0009 Acceptance (6 TRs), ADR-0012 Power-Up Refund (1 TR + 1 partial), ADR-0013 Tutorial (1 TR).
- **Report**: `docs/architecture/architecture-review-2026-04-18-r3.md`
- **Traceability index updated**: `docs/architecture/architecture-traceability.md` (revision history entry appended)

### Immediate Next Action

1. Accept ADR-0009 (Proposed → Accepted 2026-04-18) — no content changes needed per this review. Single-action 81% → 90% coverage bump.
2. Batch-write ADR-0012 + ADR-0013 in a single fresh session (both 1-TR scoped).
3. Re-run `/architecture-review` → expect **PASS** verdict.
4. Re-run `/gate-check pre-production` to confirm PASS with stronger coverage.
5. Advisory (non-blocking): amend ADR-0002 with pointer to ADR-0009 as authoritative v4 DDL; refresh `architecture.md` § ADR Audit + Feature Layer ExpansionManager.

## Session Extract — ADR-0009 Accept + ADR-0012 + ADR-0013 2026-04-18

- **ADR-0009 status**: `Proposed` → `Accepted (2026-04-18)`. No content changes (review run 3 confirmed no blockers).
- **ADR-0012 Power-Up Refund Protocol** (`docs/architecture/adr-0012-power-up-refund-protocol.md`, ~330 lines) — Accepted 2026-04-18.
  - Core `PowerUpService` + `PowerUpEffect` strategy pattern; refund is structural via `EffectOutcome.Applied/NoEffect/RetryExhausted` sealed class.
  - Shuffle takes `PuzzleGrid.snapshot()` pre-execute; restores on `RetryExhausted`; uses `PuzzleService.solveFast(grid) >= 1` per ADR-0007 / GDD AC 12.
  - Ice returns `NoEffect` when grid has only cat/wall/key blocks → refund 40 per GDD AC 13.
  - `powerup_use_count` increments on BOTH Applied AND Refunded paths (per ADR-0005 OQ-2); NOT on `InsufficientCoins`. Single `AchievementContext.POWERUP_USED` fan-in after counter write.
  - Resolves TR-puzzle-010 (Gap → ✅) and TR-economy-008 (Partial → ✅).
- **ADR-0013 Tutorial Overlay** (`docs/architecture/adr-0013-tutorial-overlay.md`, ~340 lines) — Accepted 2026-04-18.
  - Presentation `TutorialController` + `sealed class TutorialStep` (Stage1Full / Stage2BriefTip / Stage3BriefTip).
  - Stage 1: `blocksInput=true`, dismisses on first move OR Skip. Stages 2/3: `blocksInput=false`, auto-dismiss 2500ms via `lifecycleScope.launch { delay(...) }`.
  - `tutorial_completed` SharedPrefs flag written ONLY after Stage3BriefTip dismisses (crash-safe — partial completion re-runs from stage 1 on relaunch).
  - Motion-reduction accessibility hook: `accessibilitySettings.motionReductionEnabled` swaps timer for explicit confirm button.
  - Overlay Z-order above PuzzleView (ADR-0010 Surface 1) + HudOverlay (Surface 3).
  - Resolves TR-puzzle-013 (Gap → ✅).
- **Traceability index updated** (`docs/architecture/architecture-traceability.md`):
  - ADRs Accepted: 10 → **13** (0001–0013).
  - ADRs Proposed: 1 → **0**.
  - Coverage: 51/63 ✅ (81%) + 6 🟡 + 3 ⚠️ + 2 ❌ → **60/63 ✅ (95%)** + 2 ⚠️ + 0 ❌.
  - Remaining 2 ⚠️ Partials (TR-puzzle-012, TR-launch-011) are acknowledged Feature-layer formula choices; Known Gaps section now reads "None".
  - Revision history appended.
- **Registry impact**: None for this session (ADR-0012 introduces no new contracts — consumes existing ADR-0003/0005/0007 stances; ADR-0013 adds only a Presentation class that consumes existing ADR-0001 SharedPrefs surface). Optional future additions: `power_up_refund_protocol` interface contract + `missing_power_up_refund` forbidden pattern — deferred unless a second consumer appears.

**Total ADRs now**: 13 Accepted (0001–0013, with R1 amendments on 0007/0008/0011).

### Immediate Next Action

1. Run `/architecture-review` in a fresh session to confirm expected **PASS** verdict at 95% coverage.
2. Run `/gate-check pre-production` to validate PASS — current Pre-Production stage is stable; this verifies readiness for Production phase planning.
3. (Advisory from review run 3, still non-blocking) — amend ADR-0002 with a pointer to ADR-0009 as the authoritative v4 DDL; refresh `architecture.md` § ADR Audit + Feature Layer ExpansionManager section to reference ADR-0009 + ADR-0012 + ADR-0013.
4. Begin `/create-control-manifest` — all Foundation + Feature ADRs now Accepted; programmer rules sheet can be extracted.
5. Begin `/create-epics` after control manifest lands.

## Session Extract — /architecture-review 2026-04-18 (run 4, PASS)

- **Verdict**: **PASS** (first PASS — upgraded from CONCERNS tier after three prior runs)
- **Requirements**: 63 total — 60 ✅ Covered (95%), 2 ⚠️ Partial (acknowledged Feature-layer formula choices: TR-puzzle-012 score, TR-launch-011 score), 0 ❌ Gap, 1 N/A (TR-expand-008 surplus ratio — design validation)
- **ADRs Reviewed**: 13 (all Accepted: 0001–0013 with R1 amendments on 0007/0008/0011)
- **New TR-IDs registered**: None (no new GDD content)
- **Cross-ADR conflicts**: None blocking. Two advisories carried forward:
  1. ADR-0002 v4 sketch (2 tables, no purchasedAt) superseded by ADR-0009 authoritative v4 (3 tables + purchasedAt). Documented in ADR-0009 Ordering Note; recommend pointer amendment in ADR-0002 (non-blocking).
  2. `architecture.md` § ADR Audit still reads "Existing ADRs: None" — pre-ADR stale text; recommend refresh (non-blocking).
- **GDD revision flags**: None. All GDD↔ADR names verified consistent.
- **Engine compatibility**: PASS across all 13 ADRs. Native Android / Kotlin / LOW risk / no post-cutoff APIs / no deprecated API references.
- **Dependency graph**: Clean. 0 cycles. All declared `Depends On` references resolve to Accepted ADRs.
- **Remaining deferrable ADR**: ADR-0014 (Sound & Ad SDK Integration) — write when Sound/Ad feature sprint begins. Not required for any current phase gate.
- **Report**: `docs/architecture/architecture-review-2026-04-18-r4.md`
- **Traceability index updated**: `docs/architecture/architecture-traceability.md` (revision history entry appended; coverage numbers unchanged from run 3b)

### Immediate Next Action

1. Re-run `/gate-check pre-production` to confirm gate stability (expected PASS — no regression).
2. Begin `/create-control-manifest` — extract flat programmer rules sheet from the 13 Accepted ADRs + 19 registered forbidden patterns.
3. Begin `/create-epics` after the control manifest lands.
4. (Deferred, non-blocking) Batched doc refresh: ADR-0002 pointer amendment + `architecture.md` § ADR Audit update.

## Session Extract — /gate-check pre-production 2026-04-18 (run 3, PASS)

- **Verdict**: **PASS** (3rd re-verification; prior PASS 2026-04-17 and 2026-04-18 r1)
- **Review Mode**: lean — Director Panel skipped per 2026-04-17 / 2026-04-18 precedent (user-confirmed)
- **Results**: Artifacts 13/13 + 1 N/A (engine reference); Quality 11/11; 0 blockers
- **Strengthening since 2026-04-18 r1**: +7 ADRs (0007 R1, 0008 R1, 0009, 0010, 0011 R1, 0012, 0013); architecture-review r4 PASS (95% hard coverage)
- **Advisories (3, non-blocking)**:
  1. Example test file absent — tests/unit/ has README only; precedent-accepted, recommended add one unit test in Sprint 1
  2. ADR-0002 v4 sketch stale vs ADR-0009 authoritative (superseded; Ordering Note documents); cosmetic pointer amendment recommended
  3. architecture.md § ADR Audit stale text ("Existing ADRs: None") — doc refresh recommended; bundle with Advisory 2
- **Chain-of-Verification**: 5 questions checked → verdict unchanged (PASS)
- **Report**: `production/gate-checks/pre-production-2026-04-18-r3.md`
- **Stage**: Pre-Production unchanged (re-verification, no transition)

### Immediate Next Action

**Run `/create-control-manifest`** — extract flat programmer rules sheet from 13 Accepted ADRs + 19 registered forbidden patterns. Then `/create-epics layer: foundation` → `/create-epics layer: core` → `/create-stories [epic-slug]` per epic. Advisories 2+3 can be bundled in a post-manifest doc-refresh pass.

## Session Extract — Control Manifest Discovery 2026-04-18

- **Finding**: `docs/architecture/control-manifest.md` already present (2026-04-18, 343 lines) covering all 13 ADRs with R1 amendments. Quality spot-check passed: ADR-0008 R1 tuning constants, ADR-0010 4-surface architecture, ADR-0013 tutorial 3-stage flow, OQ-2 refund counter, 19 forbidden patterns with registry cites, authoritative API decisions table, layer dependency diagram all present. ADR-0014 flagged as pending.
- **Decision**: No rewrite required — manifest reflects current ADR state. Previous session extract was missing but file is current.

## Session Extract — Foundation Epics Discovery 2026-04-18

- **Finding**: `/create-epics layer: foundation` had already been run in a prior session. All 3 Foundation Epic files + 9 persistence story files existed at skill invocation.
- **Pre-existing state**: `foundation-persistence/EPIC.md` + 9 stories (001–009); `foundation-threading/EPIC.md` only; `foundation-layer-boundaries/EPIC.md` only. Index.md preview listed all 3 as "Ready / Not yet created" for stories.
- **Recovery**: Inadvertent `mv` nested new-directory attempts inside existing dirs cleaned up with user-approved single-file `rm`. No loss of prior work.
- **Slug naming locked**: `foundation-*` prefix per project convention (user-confirmed).

## Session Extract — /create-stories foundation-threading 2026-04-18

- **8 stories written** to `production/epics/foundation-threading/`:
  - story-001 Dispatcher policy + assertion (Logic, ADR-0004)
  - story-002 LifecycleScope convention + GlobalScope ban (Logic, ADR-0004)
  - story-003 Render thread template `LaunchRenderThread` (Logic, ADR-0004 + ADR-0008 consumer)
  - story-004 Render → Main handoff via `runOnUiThread` (Logic, ADR-0004)
  - story-005 Pause/Resume flag coordination (Integration, ADR-0004 + ADR-0008 Pause Semantics)
  - story-006 StrictMode debug config + clean-run evidence (Config/Data, ADR-0004 Validation #1)
  - story-007 ConcurrentHashMap shared-cache convention (Logic, ADR-0004 + ADR-0007 consumer)
  - story-008 Test coroutine scaffolding `runTest` setup (Logic, ADR-0004 test patterns)
- **Type distribution**: 6 Logic · 1 Integration · 1 Config/Data
- **EPIC.md Stories table** populated with dependency order; **index.md** updated to "8 created".
- **QL-STORY-READY skipped** (lean mode).

## Session Extract — /create-stories foundation-layer-boundaries 2026-04-18

- **5 stories written** to `production/epics/foundation-layer-boundaries/`:
  - story-001 Package skeleton + `internal` visibility + `package-info` (Config/Data, ADR-0006 § Package Structure)
  - story-002 `check-layer-boundaries.sh` CI script + planted-violation self-test (Logic, 9 grep-detectable forbidden patterns)
  - story-003 GitHub Actions `layer-checks.yml` workflow (Config/Data, CI wiring)
  - story-004 Code-review checklist + agent prompt update (Config/Data, 1 grep-invisible pattern `non_stateflow_coin_display` + semantic rules)
  - story-005 ADR-0006 Validation Criteria #1–4 evidence run (Integration, post-Foundation baseline)
- **Type distribution**: 3 Config/Data · 1 Logic · 1 Integration
- **EPIC.md Stories table** populated; **index.md** updated to "5 created".
- **QL-STORY-READY skipped** (lean mode).

## Foundation Layer Stories Complete

| Epic | Stories | Total |
|---|---|---|
| foundation-persistence | 9 (prior session; story-001~009) | 9 |
| foundation-threading | 8 (this session; story-001~008) | 8 |
| foundation-layer-boundaries | 5 (this session; story-001~005) | 5 |
| **Grand Total** | | **22** |

## Session Extract — /sprint-plan Sprint 1 2026-04-18

- **File written**: `production/sprints/sprint-1.md` + `production/sprint-status.yaml`
- **Window**: 2026-04-21 → 2026-05-02 (2 weeks, 10 working days)
- **Capacity**: solo-dev ~3h/day → 30h total; 20% buffer → 24h available
- **Sprint Goal**: deliver working Foundation persistence (Room v1→v3 migrations, atomic coin SQL, GameRepository facade) + threading test scaffolding
- **Task breakdown**:
  - Must Have (24h, 9 stories): T-1-1 threading-s008 → T-1-2 threading-s001 → T-1-3 to T-1-9 persistence-s001 through s007
  - Should Have (4h, 2 stories): T-1-10 persistence-s009 FakeGameRepository + T-1-11 layer-boundaries-s001 package skeleton
  - Nice to Have (3h, 1 story): T-1-12 persistence-s008 migration v3→v4 (stretch)
- **5 risks identified**: Room migration unfamiliarity (M/M), velocity variance (M/M), source-set split friction (L/L), ADR-0002↔0009 drift (L/L), StrictMode deferred to Sprint 2 leaves Main-thread gap (M/L)
- **PR-SPRINT skipped** (lean mode)
- **Carryover**: none (first implementation sprint)

## Session Extract — /qa-plan sprint Sprint 1 2026-04-18

- **File written**: `production/qa/qa-plan-sprint-1-2026-04-18.md`
- **Test distribution**: 5 Logic (unit) · 6 Integration · 1 Config/Data (audit) · 0 Visual/Feel · 0 UI
- **Estimated test count**: ~64 automated tests across all 12 stories (test file paths all specified per story)
- **Build-artifact verification**: `schemas/AppDatabase/{1,2,3,4}.json` additive-diff checks per migration story
- **Manual evidence**: dispatcher-policy doc check, schema exports sprint-1, layer-package audit (3 separate evidence docs)
- **Playtest requirements**: none (pure Foundation scaffolding — playtests begin when Feature-layer stories land)
- **Definition of Done**: per-story + per-sprint gates both defined (smoke check, `/team-qa` APPROVED/APPROVED-WITH-CONDITIONS, no S1/S2 bugs, `/scope-check` clean)

### Immediate Next Action

1. `/story-readiness foundation-persistence/story-001-room-scaffolding-v1.md` — validate first Must Have story is implementation-ready (Manifest Version, TR, ADR, AC embedded).
2. `/dev-story foundation-persistence/story-001-room-scaffolding-v1.md` — begin coding Sprint 1 Task T-1-3 (Room scaffolding + v1 schema).
3. T-1-1 and T-1-2 (threading) may run first to unblock test infrastructure; T-1-3 does not strictly depend on them but their tests will need the scaffolding.
4. Mid-sprint: `/sprint-status` for burndown snapshot. End-of-story: `/story-done` updates `sprint-status.yaml`.

---

## Session Extract — /dev-story 2026-04-18
- Story: `production/epics/foundation-threading/story-008-test-coroutine-scaffolding.md` — Test coroutine scaffolding (runTest setup)
- Files changed:
  - `app/build.gradle.kts` (added `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")` at line 79)
  - `app/src/test/java/com/meowrescue/game/threading/CoroutineTestSupport.kt` (new — helper object)
  - `app/src/test/java/com/meowrescue/game/threading/CoroutineTestSupportSelfTest.kt` (new — 4 self-tests, AC a–d)
  - `tests/README.md` (added "Writing Suspend Tests" section)
  - `tests/unit/README.md` (pointer to tests/README.md)
- Test written: `CoroutineTestSupportSelfTest.kt` — 4 tests covering AC a–d
- Deviations:
  - Test file paths moved from story's `tests/unit/threading/` to Gradle-visible `app/src/test/java/...` (story text needs correction)
  - Self-test filename uses PascalCase `CoroutineTestSupportSelfTest.kt` (project convention) vs story's snake_case
- Blockers: None
- Next: `/code-review app/src/test/java/com/meowrescue/game/threading/CoroutineTestSupport.kt app/src/test/java/com/meowrescue/game/threading/CoroutineTestSupportSelfTest.kt app/build.gradle.kts` → `/story-done production/epics/foundation-threading/story-008-test-coroutine-scaffolding.md`

## Session Extract — /story-done 2026-04-19
- Verdict: COMPLETE WITH NOTES
- Story: `production/epics/foundation-threading/story-008-test-coroutine-scaffolding.md` — Test coroutine scaffolding (T-1-1)
- Criteria: 5/5 passing; 3 advisory deviations (test-path correction, PascalCase filename, 2 deferred polish items)
- Post-review fixes applied: removed unused imports (`advanceUntilIdle`, `async`); added wall-clock bracket `assertTrue(wallElapsed < 100L)` for AC-2
- Story file patched: path references updated from `tests/unit/threading/` → `app/src/test/java/com/meowrescue/game/threading/`
- sprint-status.yaml: T-1-1 status → done, completed → 2026-04-19
- Tech debt logged: None (2 deferred polish items noted in story Completion Notes instead)
- Next recommended: T-1-2 `production/epics/foundation-threading/story-001-dispatcher-policy-assertion.md` (unblocked — depends on T-1-1 which is now done) OR T-1-3 `production/epics/foundation-persistence/story-001-room-scaffolding-v1.md` (no dependencies, can run in parallel)

## Session Extract — /dev-story 2026-04-19 (T-1-2)
- Story: `production/epics/foundation-threading/story-001-dispatcher-policy-assertion.md` — DispatcherPolicy + assertion helper
- Files changed:
  - `app/build.gradle.kts` — added `buildFeatures { buildConfig = true }` at line 62 (required for `BuildConfig.DEBUG` on AGP 8+)
  - `app/src/main/java/com/meowrescue/game/core/threading/DispatcherPolicy.kt` (new — object + top-level suspend `assertDispatcher`)
  - `app/src/test/java/com/meowrescue/game/core/threading/DispatcherAssertionTest.kt` (new — 4 JUnit 4 tests)
- Test written: 4 tests (AC-2 IO happy path, AC-2 Default happy path, AC-3 throws, AC-3 message content)
- Deviations:
  - Test path moved from story's `tests/unit/threading/dispatcher_assertion_test.kt` to Gradle-visible `app/src/test/java/com/meowrescue/game/core/threading/DispatcherAssertionTest.kt` (same drafting error as T-1-1)
  - AC-4 (release no-op) deferred to Sprint 2 — requires debug/release source-set split; inspection-based evidence documented in test KDoc
  - `Dispatchers.Main` happy-path not tested — requires `Dispatchers.setMain()` setup, documented in test KDoc
- Pending manual evidence: `production/qa/evidence/dispatcher-policy-doc-check.md` (lead-programmer sign-off during code review)
- Note: sub-agent hit rate limit after drafting — files written directly by orchestrator using the fully-approved draft
- Blockers: None
- Next: `/code-review app/src/main/java/com/meowrescue/game/core/threading/DispatcherPolicy.kt app/src/test/java/com/meowrescue/game/core/threading/DispatcherAssertionTest.kt app/build.gradle.kts` → `/story-done production/epics/foundation-threading/story-001-dispatcher-policy-assertion.md`

## Session Extract — /story-done 2026-04-19 (T-1-2)
- Verdict: COMPLETE WITH NOTES
- Story: `production/epics/foundation-threading/story-001-dispatcher-policy-assertion.md` — DispatcherPolicy + assertion helper (T-1-2)
- Criteria: 5/5 addressed (AC-4 release no-op deferred to Sprint 2; AC-5 cross-epic, tooling delivered)
- Post-review hardening applied: AC-3(c) message assertion now verifies dispatcher toStrings (not just scaffold words); test KDoc enumerates `Main.immediate` as deferred alongside `Main`
- Deviations (all ADVISORY): test-path correction, gradle `buildFeatures { buildConfig = true }` addition, Main/Main.immediate tests deferred, release no-op deferred to Sprint 2, AC-1 sign-off doc substituted by `/code-review` APPROVED verdict in lean mode
- Story file patched: path references and Test Evidence section updated to reflect actual Gradle-visible location
- sprint-status.yaml: T-1-2 status → done, completed → 2026-04-19
- Tech debt logged: None (deferrals tracked in story Completion Notes)
- Sprint progress: 2/9 Must Have done (T-1-1, T-1-2). 7 remaining: T-1-3..T-1-9
- Next recommended: T-1-3 `production/epics/foundation-persistence/story-001-room-scaffolding-v1.md` — Room scaffolding + v1 schema (UserProgress). No dependencies. Already validated READY earlier this session. Est: 4 h.

## Session Extract — T-1-3 PAUSED 2026-04-19
- Status: PAUSED pending re-plan (user chose Option C)
- Discovery: `/dev-story` pre-flight found that `app/src/main/java/com/meowrescue/game/data/` already contains a shipping v3 Room schema (AppDatabase v3, 4 entities, 2 migrations, public visibility, `exportSchema = false`, non-suspend DAO methods). The ADRs + stories assume greenfield; reality is retrofit on a live game.
- Divergences in existing code:
  - `AppDatabase`: version=3, exportSchema=false, public class, getInstance() factory, MIGRATION_1_2 + MIGRATION_2_3 already present
  - `UserProgress`: `levelId` (not `stageId`), `bestScore` and `catUnlocked: String?` already present, public data class
  - `UserProgressDao`: public interface, non-suspend methods, different method names (saveProgress/getProgressForLevel vs upsert/getById), missing getMaxCompleted/getThreeStarCount/getAll
- Decision: Pause story implementation. Run `/reverse-document` on existing `data/` package to produce as-built architecture. Then re-scope T-1-3 through T-1-9 as retrofit stories with realistic sizing.
- Impact on sprint: T-1-3..T-1-9 estimates need revision. Original plan assumed ~20 h for 7 stories; retrofit will likely be ~12-16 h (mostly visibility audits, suspend conversion, missing method additions, gradle exportSchema enablement).
- sprint-status.yaml: T-1-3 status unchanged (ready-for-dev) pending re-plan output — will update after `/reverse-document` + producer re-scope
- Sub-agent ad47fb98f51bde848 was mid-flight asking for option; left paused, not continued
- Next: `/reverse-document app/src/main/java/com/meowrescue/game/data/` → producer re-scope → resume with revised stories

## Session Extract — /reverse-document 2026-04-19 (persistence as-built)
- Scope: `app/src/main/java/com/meowrescue/game/data/` (11 files, schema v3 shipping)
- Type: architecture (gap analysis, not new ADR)

### Decisions recorded (D-1 through D-6)
- D-1: Remove `catUnlocked` column via v3→v4; compute unlocks from `CAT_DEFINITIONS` + `getMaxCompletedLevel()`
- D-2: Rename Kotlin field `levelId` → `stageId`; preserve DB column via `@ColumnInfo(name = "levelId")`
- D-3: Amend ADR-0005 `TOTAL_REWARD` 2290 → 2180 (match shipping `AchievementDefs.ALL`)
- D-4: Migrate `achievements.unlockedAt TEXT` → `timestamp INTEGER` at v3→v4 (discard existing strings; display-only)
- D-5: Accept Repository-tier IO dispatch as ADR-0001 documented deviation (DAOs stay non-suspend)
- D-6: Gap-analysis doc at `docs/architecture/persistence-as-built-2026-04-19.md`

### Files written (4)
- `docs/architecture/persistence-as-built-2026-04-19.md` (new, ~350 lines) — full divergence matrix, 6 decisions, revised Sprint 1 retrofit plan with per-story scope + sizing, combined v3→v4 migration spec (removes catUnlocked + converts unlockedAt + adds v4 expansion tables), ADR amendments required, follow-up work register (12 items)
- `docs/architecture/adr-0005-achievement-system.md` — R2: all 5 `2290` → `2180` references; added R2 note at top pointing to the as-built doc
- `docs/architecture/adr-0001-repository-pattern.md` — R2: added "Accepted Deviations" section documenting D-5 (Repository-tier IO dispatch) and AppDatabase `getInstance()` factory acceptance
- `docs/architecture/adr-0002-database-schema.md` — R2: added "Revision Note" documenting v3 divergences (catUnlocked, levelId, unlockedAt TEXT, achievementId vs id) + MIGRATION_3_4 pointer

### Impact on Sprint 1
- T-1-1, T-1-2: DONE (unchanged by this work)
- T-1-3 through T-1-9, T-1-12: Retrofit scope documented in as-built doc § "Revised Sprint 1 Retrofit Plan"
- Total revised sizing: 24h (matches original 24h budget — different work per story)
- T-1-10, T-1-11 remain greenfield as originally planned

### Pending follow-up (user decides next action)
1. Rewrite story files T-1-3..T-1-9 to retrofit scope (option C from earlier — not applied yet)
2. Re-run `/create-control-manifest` to reflect ADR R2 amendments (DAO non-suspend rule, 2180 balance)
3. Re-run `/qa-plan sprint` to regenerate QA plan for retrofit scopes
4. Update TR-prog-001 registry entry: note `stageId` is Kotlin-side only
5. Optionally archive as-built doc to `persistence-as-built.md` (no date suffix) post-Sprint-1

### Recommended resume path
- T-1-3 retrofit scope is now well-defined (3h). If you want to proceed:
  - `/story-readiness production/epics/foundation-persistence/story-001-room-scaffolding-v1.md` against updated ADRs (will likely flag story text as stale vs retrofit scope)
  - OR first rewrite story T-1-3..T-1-9 text to match the retrofit plan, then run story-readiness, then dev-story
- T-1-6 (atomic coin ops) has the smallest retrofit delta — SQL is already correct, just needs tests. May be quickest next win.

## Session Extract — Story Retrofit Rewrites 2026-04-19
- Applied option 1 from as-built follow-up: rewrote 7 persistence story files with Retrofit Scope banners
- Banner format per story: supersedes original AC, points to `docs/architecture/persistence-as-built-2026-04-19.md`, lists concrete retrofit tasks, preserves original content below as reference
- Story status updated: Ready → "Ready (Retrofit — see as-built doc 2026-04-19)"
- Manifest Version bumped: 2026-04-18 → "2026-04-19 (R2 ADR amendments)"
- Revised estimates per story (sum = 16.5h):
  - T-1-3 Room scaffolding retrofit (3h) — stageId rename via @ColumnInfo, exportSchema=true, back-fill 1.json, internal visibility, Integration test
  - T-1-4 v1→v2 back-fill (1.5h) — commit 2.json, MigrationTestHelper validation test
  - T-1-5 v2→v3 back-fill (2h) — commit 3.json, v1→v2→v3 chain test; 3 known v3 divergences deferred to T-1-12
  - T-1-6 atomic coin ops (2h) — ZERO production changes; test-only: Logic tests + race test + spend-refund cycle
  - T-1-7 achievement idempotent unlock (3h) — add require(TOTAL_REWARD == 2180), seed-at-startup, Logic tests
  - T-1-8 SharedPrefsWrapper extract (2h) — new wrapper class, migrate GameRepository prefs.* calls, add D-9 expansion keys
  - T-1-9 GameRepository retrofit (3h) — saveProgress API rename, computed getUnlockedCats (D-1), move R.drawable out, SQL getThreeStarCount, ADR-0001 surface gaps
- Total Sprint 1 remaining: 16.5h (T-1-3..T-1-9) + 2h (T-1-10) + 2h (T-1-11) + 3.5h (T-1-12) = 24h. Within capacity.
- Note: sprint-status.yaml `estimate_days` field not yet updated (producer-level decision); current values still show old greenfield estimates. Can be synced in a later pass.
- Verification: grep confirmed all 7 files contain "Retrofit Scope" banner.

### Next actions
1. Optionally `/story-readiness production/epics/foundation-persistence/story-004-atomic-coin-operations.md` to validate T-1-6 — smallest retrofit, best next win
2. `/dev-story production/epics/foundation-persistence/story-004-atomic-coin-operations.md` — test-only story; ~2h; no production code changes needed
3. After T-1-6 closes, tackle T-1-3 (structural changes) then T-1-4..T-1-5 (schema back-fills)

## Session Extract — /dev-story 2026-04-19 (T-1-6)
- Story: production/epics/foundation-persistence/story-004-atomic-coin-operations.md — Atomic coin operations on PlayerStatsDao
- Retrofit scope: test-only; zero production code changes
- Files changed:
  - app/build.gradle.kts (added 4 test deps: robolectric, androidx.test:core, androidx.test.ext:junit, room-testing + testOptions.unitTests.isIncludeAndroidResources)
  - app/src/test/java/com/meowrescue/game/data/PlayerStatsDaoTest.kt (new, 12 @Test fns, all passing)
- Test result: 12/12 passed (0 failures, 0 errors, 0 skipped) — 3.98s total
- AC coverage: AC-1 (addCoins×3), AC-2 (spendCoins success×2), AC-3 (insufficient×2), AC-4 (refund), AC-5 (concurrent race — linchpin test passes), AC-6 (100-cycle preservation)
- Delegation note: 2 engine-programmer sub-agents diverged from plan (dropped AC-5/AC-6, renamed tests incorrectly); main thread took over and wrote file directly
- Next: /code-review app/src/test/java/com/meowrescue/game/data/PlayerStatsDaoTest.kt app/build.gradle.kts then /story-done production/epics/foundation-persistence/story-004-atomic-coin-operations.md

## Session Extract — /story-done 2026-04-19
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/foundation-persistence/story-004-atomic-coin-operations.md — Atomic coin operations on PlayerStatsDao
- Status updates: story file (Ready→Complete + Completion Notes), sprint-status.yaml (T-1-6 ready-for-dev→done, completed 2026-04-19)
- Tech debt logged: 3 items (TD-001 addCoins(1) single-unit test, TD-002 AC-5 fan-out test, TD-003 AC-5 comment wording) in docs/tech-debt-register.md
- Next recommended: T-1-5 (migration v2→v3) — nearest retrofit dependency for T-1-6; or T-1-8 (SharedPreferences wrapper) as parallel retrofit work

## Session Extract — /dev-story 2026-04-19
- Story: `production/epics/foundation-persistence/story-006-shared-preferences-wrapper.md` — SharedPreferences wrapper (T-1-8)
- Files changed: `app/src/main/java/com/meowrescue/game/data/SharedPrefsWrapper.kt` (new), `app/src/main/java/com/meowrescue/game/data/GameRepository.kt` (modified — `prefs` → `sharedPrefs`), `app/src/test/java/com/meowrescue/game/data/SharedPrefsWrapperTest.kt` (new, 27 tests)
- Test written: `app/src/test/java/com/meowrescue/game/data/SharedPrefsWrapperTest.kt` — 27 tests, all passing (9s)
- Compile: `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL (3s)
- Key decisions: preserved `"meow_rescue"` filename (player-data preservation), `selected_cat` default 1, `endless_coin_today` key name, `endless_coin_date` default `""` (all per shipping reality; story AC table had stale typos superseded by Retrofit Scope header)
- Blockers: None
- Next: /code-review app/src/main/java/com/meowrescue/game/data/SharedPrefsWrapper.kt app/src/main/java/com/meowrescue/game/data/GameRepository.kt app/src/test/java/com/meowrescue/game/data/SharedPrefsWrapperTest.kt → /story-done production/epics/foundation-persistence/story-006-shared-preferences-wrapper.md


## Session Extract — /dev-story 2026-04-20
- Story: production/epics/foundation-persistence/story-001-room-scaffolding-v1.md — Room scaffolding & v1 schema (T-1-3)
- Files changed: app/src/main/java/com/meowrescue/game/data/UserProgress.kt, UserProgressDao.kt, AppDatabase.kt, GameRepository.kt, app/build.gradle.kts, app/schemas/com.meowrescue.game.data.AppDatabase/1.json
- Test written: app/src/test/java/com/meowrescue/game/data/RoomV1ScaffoldingTest.kt (11 tests, all passing)
- Deviations: UserProgress and GameRepository kept public (cascade prevented); AppDatabase + UserProgressDao marked internal. T-1-11 will complete visibility audit.
- Next: /story-done production/epics/foundation-persistence/story-001-room-scaffolding-v1.md


## Session Extract — /dev-story 2026-04-20 (T-1-4)
- Story: production/epics/foundation-persistence/story-002-migration-v1-v2-launch-progress.md — Migration v1→v2 launch_progress
- Files changed: LaunchProgressDao.kt (internal), schemas/com.meowrescue.game.data.AppDatabase/2.json (backfill)
- Test written: app/src/test/java/com/meowrescue/game/data/MigrationV1V2Test.kt (10 tests, all passing)
- Deviations: MigrationTestHelper not used (requires device instrumentation); Robolectric inMemory approach used instead
- Next: /story-done production/epics/foundation-persistence/story-002-migration-v1-v2-launch-progress.md

## Session Extract — /sprint-plan 2026-04-20
- Verdict: COMPLETE
- Sprint 2 plan written to: production/sprints/sprint-2.md (2026-05-05 to 2026-05-16)
- sprint-status.yaml updated: Sprint 2, 10 stories (6 Must Have, 3 Should Have, 1 Nice to Have)
- No QA plan found — run /qa-plan sprint before T-2-1
- Next recommended: /qa-plan sprint → /story-readiness foundation-persistence/story-009-fake-game-repository.md → /dev-story T-2-1

## Session Extract — /dev-story 2026-04-20
- Story: production/epics/foundation-persistence/story-009-fake-game-repository.md — FakeGameRepository test double
- Files changed: app/src/main/java/com/meowrescue/game/data/IGameRepository.kt (created), app/src/main/java/com/meowrescue/game/data/GameRepository.kt (modified — added : IGameRepository + override modifiers), app/src/test/java/com/meowrescue/game/data/FakeGameRepository.kt (created), app/src/test/java/com/meowrescue/game/data/FakeGameRepositoryTest.kt (created — 50 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/data/FakeGameRepositoryTest.kt
- Blockers: None
- Deviation: saveProgress in fake does NOT award coins (pure persistence fake — intentional; real GameRepository awards coins as a side-effect)
- Next: /code-review app/src/main/java/com/meowrescue/game/data/IGameRepository.kt app/src/test/java/com/meowrescue/game/data/FakeGameRepository.kt then /story-done production/epics/foundation-persistence/story-009-fake-game-repository.md

## Session Extract — /story-done 2026-04-20
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/foundation-persistence/story-009-fake-game-repository.md — FakeGameRepository test double
- Tech debt logged: None (4 advisory items documented in story Completion Notes)
- Next recommended: T-2-2 ADR-0014 Sound & Ad SDK Integration (must-have, no dependencies)

## Session Extract — /architecture-decision 2026-04-20
- ADR: docs/architecture/adr-0014-sound-ad-sdk-integration.md — Sound & Ad SDK Integration
- Status: Accepted
- Registry: +5 stances (sound_sfx_playback, sound_bgm_playback, ad_sdk, ad_stage_counter_persistence, rewarded_ad_coin_grant)
- Key finding: AdManager had direct_sharedprefs_outside_foundation violation — fixed in Migration Plan (add 2 methods to IGameRepository/SharedPrefsWrapper)
- T-2-2: done 2026-04-20
- Next: T-2-4 /create-stories core-economy + T-2-5 /create-stories core-progression (both unblocked)

## Session Extract — /create-stories core-economy 2026-04-20
- T-2-4: done 2026-04-20
- 6 stories written to production/epics/core-economy/ (story-001..006)
- Stories 001-003, 006: Ready; Stories 004-005: Blocked (no governing ADR — Cat Launch multiplier, Endless daily cap)
- T-2-6 unblocked: status ready-for-dev, file set to story-001-economy-manager-core.md

## Session Extract — /dev-story 2026-04-20
- Story: production/epics/core-economy/story-001-economy-manager-core.md — EconomyManager Core class + atomic coin ops + StateFlow
- Files changed: app/src/main/java/com/meowrescue/game/core/economy/EconomyManager.kt (created), app/src/test/java/com/meowrescue/game/core/economy/EconomyManagerTest.kt (created — 10 tests)
- Test written: app/src/test/java/com/meowrescue/game/core/economy/EconomyManagerTest.kt
- Test result: 228 tests completed, 1 pre-existing failure (StageTest — unrelated); all 10 EconomyManager tests PASSED
- Blockers: None
- Deviations: Used FakeGameRepository for tests (Story 002 DB dependency deferred — acceptable for Logic story)
- Next: /code-review app/src/main/java/com/meowrescue/game/core/economy/EconomyManager.kt then /story-done production/epics/core-economy/story-001-economy-manager-core.md

## Session Extract — /story-done 2026-04-20
- Verdict: COMPLETE
- Story: production/epics/core-economy/story-001-economy-manager-core.md — EconomyManager Core class + atomic coin ops + StateFlow
- Tech debt logged: None
- Next recommended: T-2-5 /create-stories core-progression (Must Have, ready-for-dev)

## Session Extract — /create-stories core-progression 2026-04-20
- T-2-5 COMPLETE: 7 stories written to `production/epics/core-progression/`
- Stories 001-005: Ready (Logic x4, Integration x1)
- Stories 006-007: Blocked — TR-prog-004 (sequential unlock) and TR-prog-012 (world theme mapping) have no governing ADR
- GDD/ADR discrepancy flagged: GDD TR-prog-006 says 2,290 coins; ADR-0005 R2 says 2,180 (shipping value). Stories use 2,180.
- sprint-status.yaml: T-2-5 → done
- Sprint 2 Must Have status: T-2-1 ✅, T-2-2 ✅, T-2-3 (device needed), T-2-4 ✅, T-2-5 ✅, T-2-6 ✅
- Next recommended: /story-readiness production/epics/core-progression/story-001-progression-manager-save.md

## Session Extract — /dev-story 2026-04-21
- Story: production/epics/core-progression/story-001-progression-manager-save.md — ProgressionManager Core
- Files changed: app/src/main/java/com/meowrescue/game/core/progression/ProgressionManager.kt (created), app/src/test/java/com/meowrescue/game/core/progression/ProgressionManagerSaveTest.kt (created — 13 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/core/progression/ProgressionManagerSaveTest.kt
- Deviation (minor): Best-ever semantics implemented at repository tier (FakeGameRepository already does maxOf). ProgressionManager delegates directly — no double-read needed.
- Blockers: None
- Next: /code-review app/src/main/java/com/meowrescue/game/core/progression/ProgressionManager.kt then /story-done

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-progression/story-001-progression-manager-save.md — ProgressionManager Core
- Tech debt logged: None
- Next recommended: story-002-cat-unlock-logic.md (depends on story-001 — now DONE)

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-progression/story-002-cat-unlock-logic.md — Cat unlock logic — newlyUnlockedCat formula
- Tech debt logged: None (dual-catalogue advisory noted in Completion Notes)
- Next recommended: story-003 Achievement check engine (production/epics/core-progression/story-003-achievement-check-engine.md) — Est: 3-4h

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-progression/story-003-achievement-check-engine.md — Achievement check engine
- Tech debt logged: None (TR-prog-006 total mismatch noted in Completion Notes)
- Next recommended: story-004-sharedprefs-persistence.md (Ready, Est: 1-2h)

## Session Extract — /dev-story 2026-04-21
- Story: production/epics/core-progression/story-004-sharedprefs-persistence.md — SharedPrefs persistence
- Files changed: app/src/main/java/com/meowrescue/game/core/progression/ProgressionManager.kt (modified — 8 delegation methods added)
- Test written: app/src/test/java/com/meowrescue/game/core/progression/SharedPrefsProgressionTest.kt (9 tests, all passing)
- Blockers: None
- Next: /code-review then /story-done production/epics/core-progression/story-004-sharedprefs-persistence.md

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE
- Story: production/epics/core-progression/story-004-sharedprefs-persistence.md — SharedPrefs persistence
- Tech debt logged: None
- Next recommended: story-005-db-migration-v1-v3.md (Ready, Est: 2-3h)

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/core-progression/story-005-db-migration-v1-v3.md — DB migration v1→v2→v3
- Tech debt logged: None
- Next recommended: story-006 or story-007 (core-progression) — check readiness first

## Session Extract — /dev-story 2026-04-21
- Story: production/epics/core-economy/story-002-db-player-stats-migration.md — Room DB player_stats schema + migration v2→v3
- Files changed: None — all ACs already satisfied by shipping v1.6.0 code
- Test written: None — MigrationV2V3Test.kt pre-existing and passing
- Blockers: None
- Next: /story-done production/epics/core-economy/story-002-db-player-stats-migration.md

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE
- Story: production/epics/core-economy/story-002-db-player-stats-migration.md — Room DB player_stats schema + migration v2→v3
- Tech debt logged: None
- Next recommended: story-003-puzzle-earn-formula (core-economy)

## Session Extract — /dev-story 2026-04-21
- Story: production/epics/core-economy/story-003-puzzle-earn-formula.md — Puzzle earn formula in EconomyManager
- Files changed: app/src/main/java/com/meowrescue/game/core/economy/EconomyManager.kt (modified — added constants + awardPuzzleClearCoins), app/src/test/java/com/meowrescue/game/core/economy/PuzzleEarnFormulaTest.kt (created — 9 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/core/economy/PuzzleEarnFormulaTest.kt
- Blockers: None
- Next: /code-review app/src/main/java/com/meowrescue/game/core/economy/EconomyManager.kt app/src/test/java/com/meowrescue/game/core/economy/PuzzleEarnFormulaTest.kt then /story-done production/epics/core-economy/story-003-puzzle-earn-formula.md

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE
- Story: production/epics/core-economy/story-003-puzzle-earn-formula.md — Puzzle earn formula in EconomyManager
- Tech debt logged: None
- Next recommended: Check story-004 and story-005 (blocked); story-006 if ready

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE
- Story: production/epics/core-economy/story-006-power-up-service.md — PowerUpService deduct-execute-refund protocol
- Tech debt logged: None
- Next recommended: All core-economy and core-progression unblocked stories are Complete. Remaining blocked stories need ADRs (story-004 launch earn, story-005 endless cap, progression story-006/007). Consider core-puzzle-solver epic next, or author missing ADRs.

## Session Extract — /story-done 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/foundation-layer-boundaries/story-001-package-skeleton-internal-visibility.md — Package skeleton + `internal` visibility + package docs
- Tech debt logged: None (advisory deviations documented in story Completion Notes)
- Next recommended: story-002 (check-layer-boundaries.sh CI grep script)

## Session Extract — T-2-8 Inventory Pass 2026-04-21
- Verdict: COMPLETE WITH NOTES
- Story: T-2-8 — Core mechanic polish + onboarding clarity inventory pass
- Evidence: production/qa/evidence/t-2-8-polish-onboarding-inventory-2026-04-21.md
- Key findings: 4 orphaned SFX (cascade/fail/cage/combo), tutorial text legibility unverified, tutorial gates wrong (stages 1-3 vs stage 1 only), 8 items totalling ~6.5h for Production Sprint 1
- Tech debt logged: None
- Next recommended: Sprint close-out OR T-2-10 (Migration v3→v4 expansion tables)

## Session Extract — /dev-story 2026-04-21
- Story: production/epics/foundation-persistence/story-008-migration-v3-v4-expansion-tables.md — Migration v3→v4 expansion tables
- Files changed: entity/CosmeticPurchase.kt (created), entity/SelectedCosmetic.kt (created), entity/ThemeUnlock.kt (created), dao/CosmeticDao.kt (created), dao/SelectedCosmeticDao.kt (created), dao/ThemeDao.kt (created), migration/Migration3To4.kt (created), AppDatabase.kt (modified v3→v4), GameRepository.kt (modified +8 methods), MigrationV3V4Test.kt (created — 12 tests, all passing)
- Test written: app/src/test/java/com/meowrescue/game/data/MigrationV3V4Test.kt
- Blockers: None
- Note: CosmeticPurchase and ThemeUnlock are public (returned in public GameRepository methods); SelectedCosmetic is internal. Same single-module constraint as story-001.
- Next: /code-review then /story-done production/epics/foundation-persistence/story-008-migration-v3-v4-expansion-tables.md

## Session Extract — /story-done 2026-04-22
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/foundation-persistence/story-008-migration-v3-v4-expansion-tables.md — Migration v3→v4 expansion tables
- Tech debt logged: None
- Next recommended: Sprint 2 close-out (/smoke-check sprint → /team-qa sprint → /gate-check)

## Tracked Polish Conditions (from gate-check 2026-04-22)

| # | Condition | Owner | Priority | Status |
|---|-----------|-------|----------|--------|
| P-1 | Rename Sprint 3 as "Stabilization Sprint" or defer polish/onboarding to Polish Sprint 2 | producer | HIGH | [ ] |
| P-2 | MIGRATION_4_5 — top story in Polish Sprint 1; gates first public release | engine-programmer | CRITICAL | [x] DONE 2026-04-22 |
| P-3 | Real-SQLite migration tests (MigrationTestHelper) for MIGRATION_1_2→MIGRATION_4_5 before Polish exits | lead-programmer | HIGH | [x] DONE 2026-04-23 |
| P-4 | Migration-test standard (ADR or coding-standards addendum) before MIGRATION_4_5 ships | tech lead | HIGH | [x] DONE 2026-04-22 |
| P-5 | On-device Cat Launch memory profile — Android Profiler, mid-range device, 5-min session | engine-programmer | HIGH | [ ] |
| P-6 | External playtest — ≥3 naive players, 20 puzzle + 5 launch stages; log in production/playtests/ | producer | HIGH | [ ] |
| P-7 | /ux-review on all 7 screen specs (CONCERNS-accepted sufficient) | ux-designer | MEDIUM | [ ] |
| P-8 | Fix or delete StageTest.featuresForStageDistribution pre-existing failure | lead-programmer | LOW | [x] DONE 2026-04-22 |
| P-9 | RGB_565 palette calibration check on primary warm-pastel colours | art-director | ADVISORY | [ ] |

## Session Extract — /gate-check 2026-04-22
- Gate: Production → Polish
- Verdict: CONCERNS — ADVANCE TO POLISH
- Stage: production/stage.txt updated → Polish
- Report: production/qa/gate-check-production-to-polish-2026-04-22.md
- Director panel: CD CONCERNS, TD CONCERNS, PR CONCERNS, AD CONCERNS
- Key findings: migration test realism gap (P-3), scope bomb risk Sprint 3 (P-1), all 7 UX specs unreviewed (P-7), Cat Launch memory unconfirmed on-device (P-5)
- Stale condition closed: Untraced TRs resolved in architecture-traceability.md
- Next: Plan Sprint 3 (Stabilization Sprint) — top story is MIGRATION_4_5

## Session Extract — /story-done 2026-05-08
- Verdict: COMPLETE WITH NOTES
- Story: production/epics/foundation-persistence/story-010-migration-v4-v5-recovery.md — Migration v4→v5 recovery
- Files changed: app/build.gradle.kts (androidTestImplementation deps), app/src/test/java/com/meowrescue/game/data/MigrationV4V5Test.kt (5 Robolectric tests), app/src/androidTest/java/com/meowrescue/game/data/MigrationV4V5InstrumentedTest.kt (4 instrumented tests)
- Test written: both files above; BUILD SUCCESSFUL (Robolectric); instrumented device execution deferred
- Tracked condition checked off: MIGRATION_4_5 recovery migration ✅
- Tech debt logged: None
- Next recommended: T-3-2 Core mechanic polish — read production/qa/evidence/t-2-8-polish-onboarding-inventory-2026-04-21.md first

## Session Extract — /dev-story T-3-2 2026-05-08
- Story: T-3-2 Core mechanic polish (A-1/A-2/A-3) — Visual/Feel
- Files changed: PuzzleView.kt (comboCount tracking, cascade SFX, snap shake state, resetPuzzle→playLevelFail), PuzzleRenderer.kt (cubic ease-out A-3, snap shake offset A-2)
- Deviation: playCageDestroy() unwired — no cage type in PuzzleBlock; comment marks call site
- Test written: None (Visual/Feel story)
- Next: T-3-3 Onboarding clarity

## Session Extract — /dev-story T-3-3 2026-05-08
- Story: T-3-3 Onboarding clarity pass (B-1/B-2/B-3) — Visual/Feel
- Files changed: PuzzleRenderer.kt (body 15sp→16sp, hint 13sp→14sp), PuzzlePaints.kt (hint color #9E9E9E→#616161), PuzzleActivity.kt (tutorial gated to stage 1 only; stages 2/3 branches removed)
- Test written: None (Visual/Feel story)
- BUILD SUCCESSFUL: 30 tests green
- Next: T-3-4 (device required) or commit sprint work; consider Should Have stories T-3-5 through T-3-9

<!-- STATUS -->
Epic: Sprint 3 Polish
Feature: Core Polish + Onboarding
Task: T-3-4 Cat Launch memory profile (device required)
<!-- /STATUS -->
