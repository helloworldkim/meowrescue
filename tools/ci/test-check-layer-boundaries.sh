#!/usr/bin/env bash
# tools/ci/test-check-layer-boundaries.sh
#
# Self-test for check-layer-boundaries.sh:
#   (a) Clean codebase → asserts exit 0
#   (b) Planted DAO-import violation → asserts exit 1
#   (c) Removes the temp file — idempotent on completion

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$SCRIPT_DIR/check-layer-boundaries.sh"
TMP="app/src/main/java/com/meowrescue/game/core/TmpViolator.kt"

# Guarantee cleanup even on early exit or interruption
trap 'rm -f "$TMP"' EXIT

echo "=== Layer boundary self-test ==="

# (a) Clean codebase must exit 0
echo "[1/2] Clean codebase check..."
if bash "$CHECK"; then
    echo "  PASS: clean codebase exits 0"
else
    echo "  FAIL: clean codebase exited non-zero — fix violations before running self-test"
    exit 1
fi

# (b) Planted violation must exit 1
echo "[2/2] Planted DAO-import violation check..."
mkdir -p "$(dirname "$TMP")"
cat > "$TMP" <<'KOTLIN'
package com.meowrescue.game.core
import com.meowrescue.game.data.dao.UserProgressDao  // planted violation
KOTLIN

violation_exit=0
bash "$CHECK" 2>&1 | grep -q "VIOLATION: direct_dao_access_outside_foundation" || violation_exit=$?
script_exit=0
bash "$CHECK" > /dev/null 2>&1 || script_exit=$?

rm -f "$TMP"  # remove before trap fires (trap still safe if already gone)

if [[ $script_exit -ne 0 ]]; then
    echo "  PASS: planted violation detected (exit $script_exit)"
else
    echo "  FAIL: planted violation was NOT detected (script exited 0)"
    exit 1
fi

echo ""
echo "=== Self-test PASSED ==="
exit 0
