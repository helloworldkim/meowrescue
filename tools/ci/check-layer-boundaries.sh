#!/usr/bin/env bash
# tools/ci/check-layer-boundaries.sh
#
# Enforces 9 forbidden patterns from docs/registry/architecture.yaml via grep.
# ADR-0006 § Enforcement Mechanism. Registry patterns enforced here:
#
#   1. direct_dao_access_outside_foundation          (ADR-0001)
#   2. direct_sharedprefs_outside_foundation         (ADR-0001)
#   3. cross_feature_import                          (ADR-0006)
#   4. upward_layer_dependency — ui.* in core/       (ADR-0006)
#   5. bypass_puzzle_service — outside core+puzzle   (ADR-0007)
#   6. raw_body_reference_outside_core — jbox2d      (ADR-0008)
#   7. canvas_types_outside_presentation             (ADR-0010)
#   8. bypass_expansion_purchase_services            (ADR-0009)
#   9. cosmetic_cache_direct_import_in_core          (ADR-0009)
#
# Pattern 10 (non_stateflow_coin_display) is code-review only — see story 004.
#
# Usage: bash tools/ci/check-layer-boundaries.sh [SRC_ROOT]
#   SRC_ROOT defaults to app/src/main/java/com/meowrescue/game

set -euo pipefail

ROOT="${1:-app/src/main/java/com/meowrescue/game}"
TOTAL=0

# ── Search tool selection ──────────────────────────────────────────────────────
if command -v rg &>/dev/null; then
    USE_RG=1
else
    USE_RG=0
fi

# search_files PATTERN SCOPE [EXCLUDE_PATH...]
# Prints matching "file:line:content" lines; returns 0 whether or not matches found.
search_files() {
    local pattern="$1" scope="$2"
    shift 2
    if [[ $USE_RG -eq 1 ]]; then
        local excludes=()
        for ex in "$@"; do excludes+=(--glob "!${ex}"); done
        rg -n --glob '*.kt' "$pattern" "$scope" "${excludes[@]}" 2>/dev/null || true
    else
        local result
        result=$(grep -rn --include='*.kt' -E "$pattern" "$scope" 2>/dev/null || true)
        # Apply exclusions via grep -v
        for ex in "$@"; do
            ex_plain="${ex#\!}"   # strip leading !
            ex_plain="${ex_plain//\*\*\//}"  # simplify glob to substring
            ex_plain="${ex_plain//\*/}"
            result=$(echo "$result" | grep -v "$ex_plain" || true)
        done
        echo "$result"
    fi
}

# check NAME PATTERN SCOPE [EXCLUDE...]
# Counts violations and prints VIOLATION lines.
check() {
    local name="$1" pattern="$2" scope="$3"
    shift 3
    local matches
    matches=$(search_files "$pattern" "$scope" "$@")
    if [[ -n "$matches" ]]; then
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "VIOLATION: $name at $line"
            TOTAL=$((TOTAL + 1))
        done <<< "$matches"
    fi
}

# ── Pattern 1: direct_dao_access_outside_foundation ───────────────────────────
# Only foundation/data layer may import DAO interfaces.
check "direct_dao_access_outside_foundation" \
    '^import .+\.data\.dao\.' \
    "$ROOT" \
    "**/data/**"

# ── Pattern 2: direct_sharedprefs_outside_foundation ─────────────────────────
# SharedPreferences must be accessed only through SharedPrefsWrapper in data/.
check "direct_sharedprefs_outside_foundation" \
    'getSharedPreferences|import android\.content\.SharedPreferences' \
    "$ROOT" \
    "**/data/**"

# ── Pattern 3: cross_feature_import ──────────────────────────────────────────
# puzzle/ must not import launch/ and vice versa.
check "cross_feature_import__puzzle_in_launch" \
    '^import .+\.puzzle\.' \
    "$ROOT/launch"

check "cross_feature_import__launch_in_puzzle" \
    '^import .+\.launch\.' \
    "$ROOT/puzzle"

# ── Pattern 4: upward_layer_dependency ───────────────────────────────────────
# core/ must not import UI layer packages (presentation leaking into core).
# Note: core→puzzle/launch feature imports are pre-existing architectural debt
# tracked in backlog; enforced here only for the clearest ui-layer violation.
check "upward_layer_dependency__ui_in_core" \
    '^import .+\.(ui)\.' \
    "$ROOT/core"

# ── Pattern 5: bypass_puzzle_service ─────────────────────────────────────────
# PuzzleGenerator/PuzzleSolver/FallbackLayouts must not be used directly
# from launch/ or top-level ui/ (presentation code).
check "bypass_puzzle_service" \
    'PuzzleGenerator|PuzzleSolver|FallbackLayouts' \
    "$ROOT/launch"

check "bypass_puzzle_service__ui" \
    'PuzzleGenerator|PuzzleSolver|FallbackLayouts' \
    "$ROOT/ui"

# ── Pattern 6: raw_body_reference_outside_core ───────────────────────────────
# jbox2d may only be referenced in launch/physics, launch/model (physics bodies),
# and launch/ui (LaunchInputHandler converts touch→Vec2 — known architecture exception).
# Catches unrelated code (puzzle/, core/, top-level ui/) importing jbox2d directly.
check "raw_body_reference_outside_core" \
    '^import org\.jbox2d' \
    "$ROOT" \
    "**/launch/physics/**" \
    "**/launch/model/**" \
    "**/launch/ui/**"

# ── Pattern 7: canvas_types_outside_presentation ─────────────────────────────
# Canvas/Paint/SurfaceHolder/SurfaceView must only be imported in */ui/* files.
# (Bitmap is excluded — allowed as opaque return type in non-UI code.)
check "canvas_types_outside_presentation" \
    '^import android\.graphics\.(Canvas|Paint|SurfaceHolder)|^import android\.view\.SurfaceView' \
    "$ROOT" \
    "**/ui/**"

# ── Pattern 8: bypass_expansion_purchase_services ────────────────────────────
# EconomyManager.spendCoins/refundCoins must only be called from core/expansion
# or core/economy. Matches actual call sites (with opening paren), not KDoc refs.
check "bypass_expansion_purchase_services" \
    'EconomyManager\.(spendCoins|refundCoins)\s*\(' \
    "$ROOT" \
    "**/core/expansion/**" \
    "**/core/economy/**" \
    "**/core/powerup/**"

# ── Pattern 9: cosmetic_cache_direct_import_in_core ──────────────────────────
# CosmeticBitmapCache is a UI-layer cache and must not be referenced in core/.
check "cosmetic_cache_direct_import_in_core" \
    'CosmeticBitmapCache' \
    "$ROOT/core"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
if (( TOTAL > 0 )); then
    echo "FAIL: $TOTAL layer-boundary violation(s). See lines above."
    exit 1
else
    echo "PASS: 0 violations."
    exit 0
fi
