# ADR-0016: Endless Mode Daily Coin Cap

## Status
Accepted (2026-05-08)

## Date
2026-05-08

## Engine Compatibility

| Field | Value |
|-------|-------|
| **Engine** | Native Android (Kotlin) — no game engine |
| **Domain** | Economy / Endless Mode |
| **Knowledge Risk** | LOW — SharedPreferences date comparison + conditional addCoins |
| **References Consulted** | `design/gdd/economy-system.md`, `design/gdd/economy-expansion.md`, ADR-0001, ADR-0003 |
| **Post-Cutoff APIs Used** | None |
| **Verification Required** | None |

## ADR Dependencies

| Field | Value |
|-------|-------|
| **Depends On** | ADR-0001 (Repository Pattern — cap state read/write via GameRepository), ADR-0003 (coin atomicity — cap does not bypass addCoins), ADR-0014 (SharedPrefsWrapper — date key storage) |
| **Enables** | Endless mode sustainable economy; prevents infinite-play coin farming |
| **Blocks** | Any Endless clear reward path |
| **GDD Requirements Addressed** | TR-economy-007 |

## Context

### Problem Statement

Endless mode has no stage ceiling: a skilled player can clear indefinitely.
Without a cap, Endless would become the dominant farming path, trivialising
the economy and reducing incentive to play Puzzle mode. The GDD specifies a
daily coin ceiling of 150 coins from Endless clear rewards specifically.

### Requirements (TR-economy-007)

- Maximum 150 coins per day from Endless clear rewards (partial award allowed).
- Awards are partial when `dailyEarned + reward > 150`:
  `actualAward = min(reward, CAP - dailyEarned)`.
- Awards are zero when `dailyEarned >= 150`.
- Daily cap resets at midnight local time; reset date stored in SharedPreferences.
- Achievement coins earned during Endless play are NOT subject to the cap —
  they flow through `addCoins()` independently.
- Stars, `endlessCount`, and `endlessBest` are updated regardless of cap.

## Decision

### Cap Enforcement

```kotlin
// In GameRepository (Dispatchers.IO)
fun awardEndlessCoins(starCoins: Int): Int {
    val today = LocalDate.now().toString()  // ISO-8601
    val storedDate = prefs.getString(KEY_ENDLESS_DATE, "")
    var dailyEarned = if (storedDate == today) prefs.getInt(KEY_ENDLESS_DAILY, 0) else 0

    val actualAward = minOf(starCoins, ENDLESS_DAILY_COIN_CAP - dailyEarned).coerceAtLeast(0)
    if (actualAward > 0) {
        addCoins(actualAward)  // atomic via ADR-0003
        dailyEarned += actualAward
    }
    prefs.edit()
        .putString(KEY_ENDLESS_DATE, today)
        .putInt(KEY_ENDLESS_DAILY, dailyEarned)
        .apply()
    return actualAward
}
```

Constants (in `GameRepository` or economy constants file):

| Constant | Key | Value |
|----------|-----|-------|
| `ENDLESS_DAILY_COIN_CAP` | — | 150 |
| `KEY_ENDLESS_DATE` | `endless_coin_date` | ISO date string |
| `KEY_ENDLESS_DAILY` | `endless_daily_earned` | Int |

### Reset Semantics

The date is read at award time. If it differs from today, `dailyEarned` resets
to 0 and the new date is written. No scheduled job or background service is
required — the reset is lazy and event-driven.

### Cap vs. Achievement Coins

Achievement unlocks call `addCoins()` directly and never touch `KEY_ENDLESS_DAILY`.
This separation is intentional: achievement rewards are one-time bonuses, not
repeatable farm income.

### UI Feedback

When `actualAward == 0` (cap reached), the stage-clear screen omits the coin
award row or shows "Coin cap reached today". Stars and stats display normally.
The UI layer queries `dailyEarned >= CAP` from the repository before building
the clear screen, not the award result directly.

## Consequences

**Positive**
- Eliminates Endless farming as the dominant income path.
- Partial award (down to 1 coin) feels fairer than a hard floor-to-zero cutoff.
- Lazy reset requires no background jobs or alarm receivers.

**Negative / Trade-offs**
- Players who play past midnight may see the cap reset mid-session, which can
  feel like extra coins appearing unexpectedly. Acceptable — it is a genuine
  daily renewal.
- SharedPreferences write on every Endless clear (even cap-reached) adds a
  small I/O cost. Acceptable at game-loop timescales (triggered once per stage
  clear, not per frame).
