package com.meowrescue.game.puzzle.ui

import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.PuzzleState
import com.meowrescue.game.util.SoundManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PuzzleInputHandler(private val view: PuzzleView) {

    /** Returns a callback to invoke outside synchronized(lock), or null. */
    fun handleDown(x: Float, y: Float): (() -> Unit)? {
        // Tutorial tap-to-advance (only for tap-to-dismiss steps, not auto-dismiss)
        if (view.tutorialStep >= 0 && view.tutorialAutoDismissAt == 0L) {
            view.tutorialStep++
            if (view.tutorialStep > 2) {
                // All stage-1 steps done
                view.tutorialStep = -1
                view.pendingTutorialDismiss = true
            }
            SoundManager.playButtonTap()
            return null
        }
        if (view.pauseRect.contains(x, y)) {
            SoundManager.playButtonTap()
            val cb = view.onPauseClicked
            return { cb?.invoke() }
        }
        if (view.state == PuzzleState.SOLVED) {
            if (view.nextStageRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = view.onNextStageClicked
                return { cb?.invoke() }
            }
            if (view.retryRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = view.onRetryClicked
                return { cb?.invoke() }
            }
            if (view.levelSelectRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = view.onLevelSelectClicked
                return { cb?.invoke() }
            }
            return null
        }
        if (view.state != PuzzleState.PLAYING || view.snapAnimating) return null
        if (view.undoRect.contains(x, y)) {
            SoundManager.playButtonTap()
            return { view.undoMove() }
        }
        if (view.resetRect.contains(x, y)) {
            SoundManager.playButtonTap()
            return { view.resetPuzzle() }
        }
        if (view.hintRect.contains(x, y)) {
            SoundManager.playButtonTap()
            val cb = view.onHintClicked
            return { cb?.invoke() }
        }
        if (view.solveRect.contains(x, y) && !view.autoSolving) {
            SoundManager.playButtonTap()
            val cb = view.onSolveClicked
            return { cb?.invoke() }
        }

        if (view.autoSolving) return null  // Block drag during auto-solve

        val g = view.grid ?: return null
        val col = ((x - view.boardLeft) / view.cellSize).toInt()
        val row = ((y - view.boardTop)  / view.cellSize).toInt()
        if (col < 0 || col >= g.cols || row < 0 || row >= g.rows) return null
        val blockId = g.blockIdAt(row, col)
        if (blockId == -1) return null

        val block = g.blocks.firstOrNull { it.id == blockId } ?: return null
        if (block.isWall) return null  // Walls cannot be dragged
        view.dragBlockId  = blockId
        view.dragStartX   = x
        view.dragStartY   = y
        view.dragCurrentX = x
        view.dragCurrentY = y
        view.dragSmoothX  = 0f
        view.dragSmoothY  = 0f

        // Pre-compute valid drag range
        if (block.length == 1) {
            view.dragAxis = 0  // decide after threshold
            view.dragMaxNegPx = 0f
            view.dragMaxPosPx = 0f
        } else {
            view.dragAxis = if (block.isHorizontal) 1 else 2
            computeDragRange(g, blockId, block.isHorizontal)
        }
        return null
    }

    fun handleMove(x: Float, y: Float) {
        if (view.dragBlockId == -1 || view.snapAnimating) return
        view.dragCurrentX = x
        view.dragCurrentY = y

        // Lock axis for 1-cell blocks after movement threshold
        if (view.dragAxis == 0) {
            val dx = abs(x - view.dragStartX)
            val dy = abs(y - view.dragStartY)
            val threshold = max(view.cellSize * 0.06f, 8f * view.resources.displayMetrics.density)
            if (dx > threshold || dy > threshold) {
                val g = view.grid ?: return
                view.dragAxis = if (dx >= dy) 1 else 2
                computeDragRange(g, view.dragBlockId, view.dragAxis == 1)
            }
        }
    }

    fun handleUp(x: Float, y: Float) {
        if (view.dragBlockId == -1 || view.snapAnimating) return
        val g = view.grid ?: run { view.dragBlockId = -1; return }
        val block = g.blocks.firstOrNull { it.id == view.dragBlockId }
        if (block == null) { view.dragBlockId = -1; return }

        if (view.dragAxis == 0) {
            // Axis never determined (very small drag) -- no move
            view.dragBlockId = -1
            view.dragMaxNegPx = 0f; view.dragMaxPosPx = 0f
            return
        }

        val moveHorizontal = view.dragAxis == 1
        val rawOffset = if (moveHorizontal) view.dragCurrentX - view.dragStartX else view.dragCurrentY - view.dragStartY
        val clamped = rawOffset.coerceIn(view.dragMaxNegPx, view.dragMaxPosPx)
        val moveSteps = (clamped / view.cellSize).roundToInt()

        if (moveSteps != 0) {
            val oldCol = block.col.toFloat()
            val oldRow = block.row.toFloat()

            // Safety validation + apply
            val direction = if (moveSteps > 0) 1 else -1
            var validSteps = 0
            for (s in 1..abs(moveSteps)) {
                if (g.canMoveInDir(view.dragBlockId, direction * s, moveHorizontal)) validSteps = direction * s
                else break
            }
            if (validSteps != 0) {
                g.moveBlockInDir(view.dragBlockId, validSteps, moveHorizontal)

                // Snap from smoothed visual position to final grid position
                view.snapAnimating = true
                view.snapBlockId = view.dragBlockId
                view.snapFromCol = oldCol + view.dragSmoothX / view.cellSize
                view.snapFromRow = oldRow + view.dragSmoothY / view.cellSize
                view.snapStartTime = System.currentTimeMillis()
                view.snapPendingSolveCheck = true
            }
        }

        view.dragBlockId = -1
        view.dragAxis = 0; view.dragMaxNegPx = 0f; view.dragMaxPosPx = 0f
        view.dragSmoothX = 0f; view.dragSmoothY = 0f
    }

    private fun computeDragRange(g: PuzzleGrid, blockId: Int, isHorizontal: Boolean) {
        val maxDist = if (isHorizontal) g.cols else g.rows
        var maxPos = 0
        var maxNeg = 0
        for (s in 1..maxDist) {
            if (g.canMoveInDir(blockId, s, isHorizontal)) maxPos = s else break
        }
        for (s in 1..maxDist) {
            if (g.canMoveInDir(blockId, -s, isHorizontal)) maxNeg = -s else break
        }
        view.dragMaxNegPx = maxNeg * view.cellSize
        view.dragMaxPosPx = maxPos * view.cellSize
    }
}
