# Sprint 4 — 2026-05-31 to 2026-06-13

## Sprint Goal
Close the newly unblocked core-economy stories (ADR-0015/0016 enable story-004 and story-005), author the two remaining untraced-TR ADRs (TR-prog-004 stage-unlock, TR-prog-012 world-theme), and complete the core-progression/006 first-clear unlock story.

## Capacity
- Total days: 10 (2 weeks, solo developer)
- Buffer (20%): 2 days reserved for unplanned work
- Available: 8 days

## Tasks

### Must Have (Critical Path)

| ID | Task | Agent/Owner | Est. Days | Dependencies | Acceptance Criteria |
|----|------|-------------|-----------|--------------|---------------------|
| T-4-1 | Core Economy 004 — Launch coin earn implementation | gameplay-programmer | 0.5 | ADR-0015 (done) | `awardLaunchCoins()` in GameRepository; difficulty multiplier applied; first-clear bonus ≤ stage 50; unit test passes |
| T-4-2 | Core Economy 005 — Endless daily cap implementation | gameplay-programmer | 0.5 | ADR-0016 (done) | `awardEndlessCoins()` in GameRepository; partial award logic; SharedPrefs date reset; unit test passes |
| T-4-3 | ADR for TR-prog-004 — Stage unlock progression | technical-director | 0.3 | TR-prog-004 in tr-registry.yaml | ADR authored, Accepted, story-005/006 reference it |
| T-4-4 | ADR for TR-prog-012 — World theme unlock | technical-director | 0.3 | TR-prog-012 in tr-registry.yaml | ADR authored, Accepted |
| T-4-5 | Core Progression 006 — First-clear stage unlock | gameplay-programmer | 0.8 | T-4-3 (ADR), ADR-0015 (done) | Stage N+1 unlocked after Stage N clear; persistence survives restart; integration test passes |

### Should Have

| ID | Task | Agent/Owner | Est. Days | Dependencies | Acceptance Criteria |
|----|------|-------------|-----------|--------------|---------------------|
| T-4-6 | Core Progression 007 — World theme unlock (cosmetic) | gameplay-programmer | 0.5 | T-4-4 (ADR) | World theme unlocked after 10-clear threshold; persisted to Room; unit test passes |
| T-4-7 | Foundation Layer Boundaries 005 — CI violation evidence doc | lead-programmer | 0.2 | T-3-9 (done — CI workflow) | `production/qa/evidence/layer-boundaries-ci-run-sprint4.md` documents first CI green run |
| T-4-8 | Foundation Threading 006 — StrictMode disk-on-main detection | engine-programmer | 0.3 | T-3-6 (done) | `StrictMode.setThreadPolicy` in debug build; logcat evidence of no violations in Cat Launch session |
| T-4-9 | Foundation Threading 007 — ConcurrentHashMap render state | engine-programmer | 0.3 | T-3-6 (done) | `mutableMapOf()` in render-path replaced with `ConcurrentHashMap`; no render-thread data race under StrictMode |

### Nice to Have

| ID | Task | Agent/Owner | Est. Days | Dependencies | Acceptance Criteria |
|----|------|-------------|-----------|--------------|---------------------|
| T-4-10 | Foundation Layer Boundaries 004 — Code review checklist | lead-programmer | 0.1 | T-3-7 (done) | `docs/architecture/layer-boundaries-review-checklist.md` with ≥ 5 checkpoints |
| T-4-11 | T-3-8 carryover — T-2-8 evidence annotation | producer | 0.1 | T-3-2/T-3-3 (done) | `t-2-8-polish-onboarding-inventory-2026-04-21.md` annotated with which items were addressed |
| T-4-12 | Feature layer epic skeleton | lead-programmer | 0.3 | T-4-3, T-4-4 | `production/epics/feature-*/` directories created; README.md stubs present |

## Carryover from Sprint 3

| Task | Reason | New Estimate |
|------|--------|--------------|
| T-3-4 Cat Launch memory profile | Requires physical Android device — no device available in dev environment | 0.1 days; carry to T-4 device session |
| T-3-8 T-2-8 evidence annotation | Low-priority producer task; not blocking any story | 0.1 days → T-4-11 |

## Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| ADR-for-TR-prog stories block T-4-5/T-4-6 | Low — ADRs are short authoring tasks | High — progression stories can't start without ADR | Author T-4-3/T-4-4 on Day 1; T-4-5/T-4-6 follow immediately |
| T-4-1/T-4-2 integration with existing GameRepository is wider than scoped | Medium — GameRepository already has addCoins | Medium — schema changes may ripple | Read GameRepository + IGameRepository before implementing; surface surprises early |
| Cat Launch memory profile (T-3-4) requires device session coordination | High probability of remaining deferred | Low — not blocking any Sprint 4 story | Keep as explicit carryover; do not let it gate Sprint 4 completion |

## Dependencies on External Factors
- T-3-4 (memory profile): requires physical Android device — not gated by Sprint 4 completion
- GitHub Actions (T-3-9 workflow): requires a push to trigger real CI; T-4-7 evidence needs a green run log

## Definition of Done for this Sprint
- [ ] All Must Have tasks completed (T-4-1 through T-4-5)
- [ ] All tasks pass acceptance criteria
- [ ] QA plan exists (`production/qa/qa-plan-sprint-4.md`)
- [ ] All Logic/Integration stories have passing unit/integration tests
- [ ] Smoke check passed (`/smoke-check sprint`)
- [ ] QA sign-off report: APPROVED or APPROVED WITH CONDITIONS (`/team-qa sprint`)
- [ ] No S1 or S2 bugs in delivered features
- [ ] Design documents updated for any deviations
- [ ] Code reviewed and merged
