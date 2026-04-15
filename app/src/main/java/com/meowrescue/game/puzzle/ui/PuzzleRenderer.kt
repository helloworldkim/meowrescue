package com.meowrescue.game.puzzle.ui

import android.graphics.*
import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.model.ExitDirection
import com.meowrescue.game.puzzle.model.PuzzleBlock
import com.meowrescue.game.puzzle.model.PuzzleState
import com.meowrescue.game.util.ScreenShake
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class PuzzleRenderer(private val view: PuzzleView, private val paints: PuzzlePaints) {

    // ── Reusable temp objects (avoid per-frame allocation) ──
    private val tmpRect1 = RectF()
    private val tmpRect2 = RectF()
    private val tmpSrcRect = Rect()
    private val tmpMatrix = Matrix()

    fun render(canvas: Canvas) {
        // Use world theme background
        canvas.drawColor(view.worldTheme.bgTop)
        val g = view.grid ?: return

        // Apply screen shake
        val shakeX = ScreenShake.offsetX
        val shakeY = ScreenShake.offsetY
        if (shakeX != 0f || shakeY != 0f) {
            canvas.save()
            canvas.translate(shakeX, shakeY)
        }

        val keyAtLock = g.let { grid ->
            if (!grid.hasKeyLock) true
            else grid.blocks.firstOrNull { it.isKey }?.let { it.row == grid.lockRow && it.col == grid.lockCol } ?: false
        }

        drawHud(canvas, g)
        drawHintBanner(canvas, g, keyAtLock)
        drawBoard(canvas, g)
        drawPortalCells(canvas, g)
        drawLockCell(canvas, g, keyAtLock)
        drawCheckpointCell(canvas, g)

        // Wall open animation progress for primary exit
        val wallOpenProgress = if (view.escapePhase >= 1 && view.escapePhase <= 3) {
            val elapsed = if (view.escapePhase == 1) {
                (System.currentTimeMillis() - view.escapeStartTime).toFloat() / PuzzleView.WALL_OPEN_MS
            } else 1f
            elapsed.coerceIn(0f, 1f)
        } else 0f

        drawExitArrow(canvas, g, g.exitDirection, g.exitRow, g.exitCol, PuzzleView.EXIT_COLOR, keyAtLock, wallOpenProgress)
        val dir2 = g.exitDirection2
        if (dir2 != null) {
            drawExitArrow(canvas, g, dir2, g.exitRow2, g.exitCol2, PuzzleView.EXIT2_COLOR, keyAtLock)
        }

        drawBlocks(canvas, g)
        if (view.magnetActive) drawMagnetHighlights(canvas, g)
        drawLockOverlay(canvas, g, keyAtLock)
        drawCheckpointOverlay(canvas, g)
        drawToolbar(canvas)

        // Screen flash effect
        if (view.screenFlashAlpha > 0f) {
            paints.flashPaint.color = Color.WHITE
            paints.flashPaint.alpha = (view.screenFlashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), paints.flashPaint)
        }

        // Draw particles on top
        if (view.escapePhase == 3 || view.particles.isNotEmpty()) {
            drawParticles(canvas)
        }

        // Restore shake transform
        if (shakeX != 0f || shakeY != 0f) {
            canvas.restore()
        }

        if (view.state == PuzzleState.SOLVED && view.victoryAlpha > 0f) {
            drawVictoryOverlay(canvas, g)
        }

        if (view.tutorialStep >= 0) {
            drawTutorialOverlay(canvas)
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────

    private fun drawHud(canvas: Canvas, g: PuzzleGrid) {
        val density = view.resources.displayMetrics.density
        val hudH    = (PuzzleView.HUD_HEIGHT_DP * density).toInt().toFloat()
        val hudTop  = 0f
        val w       = view.width.toFloat()

        paints.hudBgPaint.color = 0xFFFFF3E0.toInt()
        canvas.drawRect(0f, hudTop, w, hudTop + hudH, paints.hudBgPaint)

        paints.hudTextPaint.textSize = 18 * density
        paints.hudTextPaint.textAlign = Paint.Align.LEFT
        val stageLabel = if (view.isEndless) "Endless #${view.endlessCount}" else "Stage ${view.stageNumber}"
        canvas.drawText(stageLabel, 16 * density, hudTop + hudH * 0.45f, paints.hudTextPaint)

        // Coin display
        paints.coinTextPaint.textSize = 12 * density
        canvas.drawText("\uD83E\uDE99 ${view.displayCoins}", 16 * density, hudTop + hudH * 0.82f, paints.coinTextPaint)

        // Score display
        if (view.score > 0) {
            paints.scorePaint.textSize = 12 * density
            paints.scorePaint.color = 0xFFFFD600.toInt()
            paints.scorePaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Score: ${view.score}", 16 * density + 80 * density, hudTop + hudH * 0.82f, paints.scorePaint)
            paints.scorePaint.textAlign = Paint.Align.CENTER
        }

        // Move count + color based on star tracking
        val moves = g.getMoveCount()
        val star3Limit = view.optimalMoves
        val star2Limit = (view.optimalMoves * 1.5f).toInt()
        val moveColor = when {
            moves <= star3Limit -> 0xFF388E3C.toInt()  // green (on track for 3 stars)
            moves <= star2Limit -> 0xFFF57F17.toInt()  // amber (on track for 2 stars)
            else -> 0xFFD32F2F.toInt()                 // red (1 star)
        }

        paints.hudTextPaint.textAlign = Paint.Align.CENTER
        paints.hudTextPaint.color = moveColor
        canvas.drawText("Moves: $moves", w / 2f, hudTop + hudH * 0.45f, paints.hudTextPaint)
        paints.hudTextPaint.color = 0xFF4E342E.toInt()  // reset

        // Star thresholds
        paints.starInfoPaint.textSize = 11 * density
        canvas.drawText(
            "\u2605\u2605\u2605 \u2264$star3Limit   \u2605\u2605 \u2264$star2Limit   \u2605 $star2Limit+",
            w / 2f, hudTop + hudH * 0.82f, paints.starInfoPaint
        )

        val btnSize   = 40 * density
        val btnMargin = 8 * density
        val btnLeft   = w - btnSize - btnMargin
        val btnTop2   = hudTop + (hudH - btnSize) / 2f
        view.pauseRect.set(btnLeft, btnTop2, btnLeft + btnSize, btnTop2 + btnSize)

        val pb = view.pauseBitmap
        if (pb != null && !pb.isRecycled) {
            canvas.drawBitmap(pb, null, view.pauseRect, null)
        } else {
            val barW = btnSize * 0.22f
            val barH = btnSize * 0.55f
            val cx   = view.pauseRect.centerX()
            val cy   = view.pauseRect.centerY()
            canvas.drawRect(cx - barW * 1.5f, cy - barH / 2f, cx - barW * 0.5f, cy + barH / 2f, paints.pauseFallbackPaint)
            canvas.drawRect(cx + barW * 0.5f, cy - barH / 2f, cx + barW * 1.5f, cy + barH / 2f, paints.pauseFallbackPaint)
        }
    }

    // ── Hint banner (between HUD and board) ──────────────────────────────

    private fun drawHintBanner(canvas: Canvas, g: PuzzleGrid, keyAtLock: Boolean) {
        val hints = mutableListOf<String>()
        if (g.hasKeyLock) {
            if (!keyAtLock) hints.add("\uD83D\uDD11\u2192\uD83D\uDD12 열쇠를 자물쇠 칸에 놓으세요")
        }
        if (g.hasCheckpoint) {
            if (!g.checkpointReached) {
                hints.add("\u2B50 별을 먼저 지나가세요")
            } else {
                hints.add("\u2B50 \u2713")
            }
        }
        if (g.blocks.any { it.isWall }) hints.add("\uD83E\uDDF1 갈색 블록은 고정 장애물입니다")
        if (g.blocks.any { it.linkId >= 0 }) hints.add("\uD83D\uDD17 연결된 블록은 함께 움직입니다")
        if (g.portalA >= 0 && g.portalB >= 0) hints.add("\uD83C\uDF00 고양이가 포탈에 들어가면 반대편으로 이동")
        if (g.exitDirection2 != null) hints.add("\uD83D\uDC31\uD83D\uDC31 모든 고양이를 탈출시키세요")
        if (hints.isEmpty()) return

        val density = view.resources.displayMetrics.density
        val hudH = PuzzleView.HUD_HEIGHT_DP * density
        val bannerTop = hudH + 2 * density
        val textSize = 13 * density
        val bannerH = textSize * hints.size + 12 * density

        // Background
        paints.bannerBgPaint.alpha = 220
        canvas.drawRoundRect(
            RectF(8 * density, bannerTop, view.width - 8 * density, bannerTop + bannerH),
            8f, 8f, paints.bannerBgPaint
        )

        // Text
        paints.bannerTextPaint.textSize = textSize
        for ((i, hint) in hints.withIndex()) {
            canvas.drawText(
                hint, view.width / 2f,
                bannerTop + 8 * density + textSize * (i + 0.8f),
                paints.bannerTextPaint
            )
        }
    }

    // ── Board ─────────────────────────────────────────────────────────────

    private fun drawBoard(canvas: Canvas, g: PuzzleGrid) {
        // Apply world theme colors
        paints.gridBgPaint.color = view.worldTheme.gridBg
        paints.linePaint.color = view.worldTheme.cellLine

        tmpRect1.set(view.boardLeft, view.boardTop, view.boardLeft + view.boardSize, view.boardTop + view.boardSize)
        canvas.drawRoundRect(tmpRect1, 8f, 8f, paints.gridBgPaint)

        for (i in 0..g.rows) {
            val y = view.boardTop + i * view.cellSize
            canvas.drawLine(view.boardLeft, y, view.boardLeft + view.boardSize, y, paints.linePaint)
        }
        for (j in 0..g.cols) {
            val x = view.boardLeft + j * view.cellSize
            canvas.drawLine(x, view.boardTop, x, view.boardTop + view.boardSize, paints.linePaint)
        }
    }

    // ── Magnet highlight ─────────────────────────────────────────────────

    private fun drawMagnetHighlights(canvas: Canvas, g: PuzzleGrid) {
        val density = view.resources.displayMetrics.density
        val pulse = view.pulse(400.0, 0.3f, 0.7f)

        for (block in g.blocks) {
            if (block.isCat || block.isWall) continue
            // Check if block can move in any direction (both axes for 1-cell blocks)
            var canMove = g.canMoveInDir(block.id, 1, block.isHorizontal) ||
                          g.canMoveInDir(block.id, -1, block.isHorizontal)
            if (!canMove && block.length == 1) {
                canMove = g.canMoveInDir(block.id, 1, !block.isHorizontal) ||
                          g.canMoveInDir(block.id, -1, !block.isHorizontal)
            }
            if (!canMove) continue

            val left = view.boardLeft + block.col * view.cellSize
            val top = view.boardTop + block.row * view.cellSize
            val right = left + (if (block.isHorizontal) block.length else 1) * view.cellSize
            val bottom = top + (if (block.isHorizontal) 1 else block.length) * view.cellSize

            paints.hintGlowPaint.color = 0xFFFFD600.toInt()
            paints.hintGlowPaint.alpha = (pulse * 200).toInt()
            paints.hintGlowPaint.strokeWidth = 3f * density
            canvas.drawRoundRect(left + 2, top + 2, right - 2, bottom - 2,
                6f * density, 6f * density, paints.hintGlowPaint)
        }
    }

    // ── Portal cells ──────────────────────────────────────────────────────

    private fun drawPortalCells(canvas: Canvas, g: PuzzleGrid) {
        if (g.portalA < 0 || g.portalB < 0) return
        drawSinglePortal(canvas, g, g.portalA, PuzzleView.PORTAL_A_CLR, "A")
        drawSinglePortal(canvas, g, g.portalB, PuzzleView.PORTAL_B_CLR, "B")
    }

    private fun drawSinglePortal(canvas: Canvas, g: PuzzleGrid, pos: Int, color: Int, label: String) {
        val r = pos / g.cols; val c = pos % g.cols
        val cx = view.boardLeft + c * view.cellSize + view.cellSize / 2f
        val cy = view.boardTop + r * view.cellSize + view.cellSize / 2f
        val pulsed = view.pulse(400.0, 0.15f, 0.85f)
        val radius = view.cellSize * 0.35f * pulsed
        paints.portalPaint.color = color; paints.portalPaint.alpha = 100
        canvas.drawCircle(cx, cy, radius, paints.portalPaint)
        paints.portalInnerPaint.color = color; paints.portalInnerPaint.alpha = 180
        canvas.drawCircle(cx, cy, radius * 0.6f, paints.portalInnerPaint)
        paints.portalLabelPaint.textSize = view.cellSize * 0.25f
        canvas.drawText(label, cx, cy + paints.portalLabelPaint.textSize * 0.35f, paints.portalLabelPaint)
    }

    // ── Lock cell ────────────────────────────────────────────────────────

    private fun drawLockCell(canvas: Canvas, g: PuzzleGrid, keyAtLock: Boolean) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return
        val left = view.boardLeft + g.lockCol * view.cellSize
        val top  = view.boardTop  + g.lockRow * view.cellSize
        val padding = view.cellSize * 0.1f

        paints.lockCellPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else PuzzleView.LOCK_COLOR
        paints.lockCellPaint.alpha = if (keyAtLock) 180 else 120
        val rect = RectF(left + padding, top + padding,
                         left + view.cellSize - padding, top + view.cellSize - padding)
        canvas.drawRoundRect(rect, 6f, 6f, paints.lockCellPaint)

        // Lock icon text
        paints.lockIconPaint.textSize = view.cellSize * 0.35f
        paints.lockIconPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else PuzzleView.LOCK_COLOR
        paints.lockIconPaint.alpha = if (keyAtLock) 200 else 100
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, left + view.cellSize / 2f, top + view.cellSize / 2f + paints.lockIconPaint.textSize * 0.35f, paints.lockIconPaint)
    }

    // ── Lock overlay (drawn ON TOP of blocks so always visible) ──────────

    private fun drawLockOverlay(canvas: Canvas, g: PuzzleGrid, keyAtLock: Boolean) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return

        val left = view.boardLeft + g.lockCol * view.cellSize
        val top  = view.boardTop  + g.lockRow * view.cellSize
        val badgeSize = view.cellSize * 0.35f

        val badgeLeft = left + view.cellSize - badgeSize - view.cellSize * 0.05f
        val badgeTop  = top + view.cellSize * 0.05f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        paints.badgeBgPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
        paints.badgeBgPaint.alpha = 200
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, paints.badgeBgPaint)

        paints.badgeIconPaint.textSize = badgeSize * 0.7f
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + paints.badgeIconPaint.textSize * 0.3f, paints.badgeIconPaint)

        if (!keyAtLock) {
            val pulsed = view.pulse(600.0, 0.3f, 0.7f)
            paints.glowStrokePaint.color = PuzzleView.LOCK_COLOR
            paints.glowStrokePaint.alpha = (pulsed * 140).toInt()
            val pad = view.cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + view.cellSize - pad, top + view.cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, paints.glowStrokePaint)
        }
    }

    // ── Checkpoint overlay (drawn ON TOP of blocks so always visible) ───

    private fun drawCheckpointOverlay(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasCheckpoint) return

        val left = view.boardLeft + g.checkpointCol * view.cellSize
        val top  = view.boardTop  + g.checkpointRow * view.cellSize
        val reached = g.checkpointReached
        val badgeSize = view.cellSize * 0.35f

        val badgeLeft = left + view.cellSize * 0.05f
        val badgeTop  = top + view.cellSize * 0.05f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        paints.badgeBgPaint.color = if (reached) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
        paints.badgeBgPaint.alpha = 200
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, paints.badgeBgPaint)

        paints.badgeIconPaint.textSize = badgeSize * 0.7f
        val icon = if (reached) "\u2713" else "\u2B50"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + paints.badgeIconPaint.textSize * 0.3f, paints.badgeIconPaint)

        if (!reached) {
            val pulsed = view.pulse(500.0, 0.3f, 0.7f)
            paints.glowStrokePaint.color = PuzzleView.CHECKPOINT_COLOR
            paints.glowStrokePaint.alpha = (pulsed * 140).toInt()
            val pad = view.cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + view.cellSize - pad, top + view.cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, paints.glowStrokePaint)
        }
    }

    // ── Checkpoint cell ──────────────────────────────────────────────────

    private fun drawCheckpointCell(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasCheckpoint) return
        val left = view.boardLeft + g.checkpointCol * view.cellSize
        val top  = view.boardTop  + g.checkpointRow * view.cellSize
        val cx = left + view.cellSize / 2f
        val cy = top + view.cellSize / 2f
        val reached = g.checkpointReached

        // Detect transition for glow
        if (reached && !view.prevCheckpointReached) {
            view.checkpointGlowStartTime = System.currentTimeMillis()
        }
        view.prevCheckpointReached = reached

        val bgRadius = view.cellSize * 0.38f
        paints.checkpointCirclePaint.color = if (reached) 0xFFFFD700.toInt() else 0xFF9E9E9E.toInt()
        paints.checkpointCirclePaint.alpha = if (reached) 60 else 30
        canvas.drawCircle(cx, cy, bgRadius, paints.checkpointCirclePaint)

        paints.checkpointBorderPaint.color = if (reached) 0xFFFFD700.toInt() else 0xFF757575.toInt()
        paints.checkpointBorderPaint.alpha = if (reached) 180 else 80
        canvas.drawCircle(cx, cy, bgRadius, paints.checkpointBorderPaint)

        paints.checkpointStarPaint.textSize = view.cellSize * 0.55f
        paints.checkpointStarPaint.alpha = if (reached) 255 else 120
        canvas.drawText("\u2B50", cx, cy + paints.checkpointStarPaint.textSize * 0.25f, paints.checkpointStarPaint)

        if (!reached) {
            val pulsed = view.pulse(500.0, 0.4f, 0.6f)
            paints.checkpointPulsePaint.color = PuzzleView.CHECKPOINT_COLOR
            paints.checkpointPulsePaint.alpha = (pulsed * 100).toInt()
            canvas.drawCircle(cx, cy, bgRadius + 3f, paints.checkpointPulsePaint)
        }

        if (reached && view.checkpointGlowStartTime > 0L) {
            val elapsed = System.currentTimeMillis() - view.checkpointGlowStartTime
            if (elapsed < 400L) {
                val progress = elapsed / 400f
                paints.checkpointRingPaint.color = PuzzleView.CHECKPOINT_COLOR
                paints.checkpointRingPaint.alpha = ((1f - progress) * 180).toInt()
                val radius = view.cellSize * 0.3f + view.cellSize * 0.3f * progress
                canvas.drawCircle(cx, cy, radius, paints.checkpointRingPaint)
            }
        }
    }

    // ── Unified exit arrow ───────────────────────────────────────────────

    private fun drawExitArrow(
        canvas: Canvas, g: PuzzleGrid,
        dir: ExitDirection, row: Int, col: Int,
        color: Int, keyAtLock: Boolean,
        wallOpenProgress: Float = 0f
    ) {
        val arrowW = view.cellSize * 0.5f
        val arrowH = view.cellSize * 0.6f
        val isPrimary = (color == PuzzleView.EXIT_COLOR)

        if (isPrimary) {
            paints.exitPaint.color = color
            paints.exitPaint.style = Paint.Style.FILL
            paints.exitPaint.alpha = if (keyAtLock) 255 else 80
            paints.gapPaint.color = color
            paints.gapPaint.alpha = if (keyAtLock) 80 else 40
        } else {
            paints.exit2Paint.color = color
            paints.exit2Paint.style = Paint.Style.FILL
            paints.exit2GapPaint.color = color
            paints.exit2GapPaint.alpha = 80
        }

        val arrowPaint = if (isPrimary) paints.exitPaint else paints.exit2Paint
        val gapUsePaint = if (isPrimary) paints.gapPaint else paints.exit2GapPaint
        val gapExtra = wallOpenProgress * view.cellSize * 0.5f

        when (dir) {
            ExitDirection.RIGHT -> {
                val exitY = view.boardTop + row * view.cellSize
                val cx = view.boardLeft + view.boardSize + arrowW * 0.4f
                val cy = exitY + view.cellSize / 2f
                paints.arrowPath.reset()
                paints.arrowPath.moveTo(cx, cy - arrowH / 2f)
                paints.arrowPath.lineTo(cx + arrowW, cy)
                paints.arrowPath.lineTo(cx, cy + arrowH / 2f)
                paints.arrowPath.close()
                canvas.drawPath(paints.arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(view.boardLeft + view.boardSize - 4f, exitY - gapExtra, view.boardLeft + view.boardSize + 4f, exitY + view.cellSize + gapExtra, gapUsePaint)
                } else {
                    canvas.drawRect(view.boardLeft + view.boardSize - 4f, exitY, view.boardLeft + view.boardSize + 4f, exitY + view.cellSize, gapUsePaint)
                }
            }
            ExitDirection.LEFT -> {
                val exitY = view.boardTop + row * view.cellSize
                val cx = view.boardLeft - arrowW * 0.4f
                val cy = exitY + view.cellSize / 2f
                paints.arrowPath.reset()
                paints.arrowPath.moveTo(cx, cy - arrowH / 2f)
                paints.arrowPath.lineTo(cx - arrowW, cy)
                paints.arrowPath.lineTo(cx, cy + arrowH / 2f)
                paints.arrowPath.close()
                canvas.drawPath(paints.arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(view.boardLeft - 4f, exitY - gapExtra, view.boardLeft + 4f, exitY + view.cellSize + gapExtra, gapUsePaint)
                } else {
                    canvas.drawRect(view.boardLeft - 4f, exitY, view.boardLeft + 4f, exitY + view.cellSize, gapUsePaint)
                }
            }
            ExitDirection.TOP -> {
                val exitX = view.boardLeft + col * view.cellSize
                val cx = exitX + view.cellSize / 2f
                val cy = view.boardTop - arrowW * 0.4f
                paints.arrowPath.reset()
                paints.arrowPath.moveTo(cx - arrowH / 2f, cy)
                paints.arrowPath.lineTo(cx, cy - arrowW)
                paints.arrowPath.lineTo(cx + arrowH / 2f, cy)
                paints.arrowPath.close()
                canvas.drawPath(paints.arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(exitX - gapExtra, view.boardTop - 4f, exitX + view.cellSize + gapExtra, view.boardTop + 4f, gapUsePaint)
                } else {
                    canvas.drawRect(exitX, view.boardTop - 4f, exitX + view.cellSize, view.boardTop + 4f, gapUsePaint)
                }
            }
            ExitDirection.BOTTOM -> {
                val exitX = view.boardLeft + col * view.cellSize
                val cx = exitX + view.cellSize / 2f
                val cy = view.boardTop + view.boardSize + arrowW * 0.4f
                paints.arrowPath.reset()
                paints.arrowPath.moveTo(cx - arrowH / 2f, cy)
                paints.arrowPath.lineTo(cx, cy + arrowW)
                paints.arrowPath.lineTo(cx + arrowH / 2f, cy)
                paints.arrowPath.close()
                canvas.drawPath(paints.arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(exitX - gapExtra, view.boardTop + view.boardSize - 4f, exitX + view.cellSize + gapExtra, view.boardTop + view.boardSize + 4f, gapUsePaint)
                } else {
                    canvas.drawRect(exitX, view.boardTop + view.boardSize - 4f, exitX + view.cellSize, view.boardTop + view.boardSize + 4f, gapUsePaint)
                }
            }
        }
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private fun drawWallBlock(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, cr: Float, padding: Float) {
        paints.blockPaint.color = PuzzleView.WALL_COLOR
        tmpRect1.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect1, cr, cr, paints.blockPaint)
        canvas.drawLine(left + padding, top + padding, right - padding, bottom - padding, paints.wallXPaint)
        canvas.drawLine(right - padding, top + padding, left + padding, bottom - padding, paints.wallXPaint)
    }

    private fun drawCatBlock(canvas: Canvas, g: PuzzleGrid, block: PuzzleBlock,
                              left: Float, top: Float, right: Float, bottom: Float, isDragging: Boolean) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val cellW = right - left
        val cellH = bottom - top
        val bmp = view.catBitmap

        val scale = if (isDragging) 1.05f else 1.0f
        val imgSize = min(cellW, cellH) * 0.92f * scale

        if (isDragging) {
            paints.catShadowPaint.color = 0xFF000000.toInt()
            paints.catShadowPaint.alpha = 25
            paints.catShadowPaint.maskFilter = paints.catDragBlur
            canvas.drawCircle(cx + 3f, cy + 4f, imgSize * 0.35f, paints.catShadowPaint)
        }

        val cats = g.blocks.filter { it.isCat }
        val isSecondCat = g.exitDirection2 != null && cats.size >= 2 && block.id != cats.first().id
        if (isSecondCat) {
            paints.catRingPaint.color = PuzzleView.EXIT2_COLOR
            paints.catRingPaint.strokeWidth = 3f * view.resources.displayMetrics.density
            canvas.drawCircle(cx, cy, imgSize * 0.48f, paints.catRingPaint)
        } else if (g.exitDirection2 != null && cats.size >= 2) {
            paints.catRingPaint.color = PuzzleView.EXIT_COLOR
            paints.catRingPaint.strokeWidth = 3f * view.resources.displayMetrics.density
            canvas.drawCircle(cx, cy, imgSize * 0.48f, paints.catRingPaint)
        }

        if (bmp != null && !bmp.isRecycled) {
            tmpRect1.set(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, tmpRect1, null)
        } else {
            canvas.drawCircle(cx, cy, imgSize * 0.45f, paints.catFallbackPaint)
            paints.textPaint.textSize = imgSize * 0.35f
            canvas.drawText("\uD83D\uDC31", cx, cy + paints.textPaint.textSize * 0.3f, paints.textPaint)
        }
    }

    private fun drawNormalBlock(canvas: Canvas, block: PuzzleBlock,
                                 left: Float, top: Float, right: Float, bottom: Float,
                                 cr: Float, isDragging: Boolean, isSnapping: Boolean) {
        val color = when {
            block.isKey -> PuzzleView.KEY_COLOR
            block.linkId >= 0 -> PuzzleView.LINK_COLOR
            else -> view.worldTheme.blockColors[(block.id - 1) % view.worldTheme.blockColors.size]
        }

        if (isDragging || isSnapping) {
            paints.blockShadow.alpha = 80
            tmpRect2.set(left + 6f, top + 6f, right + 6f, bottom + 6f)
            canvas.drawRoundRect(tmpRect2, cr, cr, paints.blockShadow)
        }

        paints.blockPaint.color = color
        tmpRect1.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect1, cr, cr, paints.blockPaint)

        // Highlight gloss using cached gradient + matrix translate
        val grad = paints.glossGradient
        if (grad != null) {
            tmpMatrix.reset()
            tmpMatrix.setTranslate(left, top)
            grad.setLocalMatrix(tmpMatrix)
            paints.glossPaint.shader = grad
        } else {
            paints.glossPaint.shader = LinearGradient(
                left, top, left, top + (bottom - top) * 0.4f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(tmpRect1, cr, cr, paints.glossPaint)

        if (block.isKey) {
            paints.textPaint.textSize = view.cellSize * 0.55f
            canvas.drawText(
                "\uD83D\uDD11",
                (left + right) / 2f,
                (top + bottom) / 2f + paints.textPaint.textSize * 0.3f,
                paints.textPaint
            )
        }

        if (block.linkId >= 0) {
            paints.chainIconPaint.textSize = view.cellSize * 0.3f
            canvas.drawText("\uD83D\uDD17", (left + right) / 2f,
                (top + bottom) / 2f + paints.chainIconPaint.textSize * 0.3f, paints.chainIconPaint)
        }
    }

    private fun drawHintGlow(canvas: Canvas, block: PuzzleBlock,
                              left: Float, top: Float, right: Float, bottom: Float, cr: Float) {
        val elapsed = System.currentTimeMillis() - view.hintStartTime
        val glowAlpha = if (elapsed > 3000L) {
            val fade = 1f - ((elapsed - 3000L) / 1500f).coerceIn(0f, 1f)
            if (fade <= 0f) view.hintBlockId = -1
            fade
        } else 1f
        if (glowAlpha <= 0f) return

        val glowPulse = view.pulse(300.0, 0.4f, 0.6f)
        paints.hintGlowPaint.strokeWidth = view.cellSize * 0.12f
        paints.hintGlowPaint.color = android.graphics.Color.argb(
            (glowAlpha * glowPulse * 255).toInt().coerceIn(0, 255),
            0xFF, 0xD7, 0x00
        )
        paints.hintGlowPaint.maskFilter = paints.hintGlowBlur
        val padding = view.cellSize * 0.07f
        tmpRect1.set(left - padding * 0.5f, top - padding * 0.5f,
            right + padding * 0.5f, bottom + padding * 0.5f)
        canvas.drawRoundRect(tmpRect1, cr, cr, paints.hintGlowPaint)

        paints.hintArrowPaint.color = android.graphics.Color.argb(
            (glowAlpha * glowPulse * 230).toInt().coerceIn(0, 255),
            0xFF, 0xD7, 0x00
        )
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val arrowLen = view.cellSize * 0.35f
        val arrowHead = view.cellSize * 0.18f
        val dx = if (view.hintDCol > 0) 1f else if (view.hintDCol < 0) -1f else 0f
        val dy = if (view.hintDRow > 0) 1f else if (view.hintDRow < 0) -1f else 0f
        val tipX = cx + dx * arrowLen
        val tipY = cy + dy * arrowLen
        val tailX = cx - dx * arrowLen * 0.3f
        val tailY = cy - dy * arrowLen * 0.3f

        paints.hintShaftPaint.color = paints.hintArrowPaint.color
        paints.hintShaftPaint.strokeWidth = view.cellSize * 0.07f
        canvas.drawLine(tailX, tailY, tipX, tipY, paints.hintShaftPaint)

        val perpX = -dy * arrowHead * 0.5f
        val perpY = dx * arrowHead * 0.5f
        paints.arrowPath.reset()
        paints.arrowPath.moveTo(tipX, tipY)
        paints.arrowPath.lineTo(tipX - dx * arrowHead + perpX, tipY - dy * arrowHead + perpY)
        paints.arrowPath.lineTo(tipX - dx * arrowHead - perpX, tipY - dy * arrowHead - perpY)
        paints.arrowPath.close()
        canvas.drawPath(paints.arrowPath, paints.hintArrowPaint)
    }

    private fun drawBlocks(canvas: Canvas, g: PuzzleGrid) {
        val padding = view.cellSize * 0.07f
        val cr      = min(view.cellSize * 0.22f, 24f)

        val draggedBlock = if (view.dragBlockId >= 0) g.blocks.firstOrNull { it.id == view.dragBlockId } else null
        val dragPartnerId = if (draggedBlock != null && draggedBlock.linkId >= 0) {
            g.blocks.firstOrNull { it.linkId == draggedBlock.linkId && it.id != view.dragBlockId }?.id ?: -1
        } else -1

        for (block in g.blocks) {
            val isDragging = (block.id == view.dragBlockId || block.id == dragPartnerId)
            val isSnapping = (block.id == view.snapBlockId && view.snapAnimating)

            if (block.isCat && view.escapePhase == 2) {
                val rawElapsed = (System.currentTimeMillis() - view.escapeStartTime).toFloat()
                val t = (rawElapsed * view.timeScale / PuzzleView.CAT_SLIDE_MS).coerceIn(0f, 1f)
                val easeT = t * t
                drawCatSlideOut(canvas, g, block, padding, cr, easeT)
                continue
            }
            if (block.isCat && view.escapePhase >= 3) continue

            var left = view.boardLeft  + block.col * view.cellSize + padding
            var top  = view.boardTop   + block.row * view.cellSize + padding
            val right: Float
            val bottom: Float

            if (block.isHorizontal) {
                right  = view.boardLeft + (block.col + block.length) * view.cellSize - padding
                bottom = view.boardTop  + (block.row + 1) * view.cellSize - padding
            } else {
                right  = view.boardLeft + (block.col + 1) * view.cellSize - padding
                bottom = view.boardTop  + (block.row + block.length) * view.cellSize - padding
            }

            if (isSnapping) {
                val snapElapsed = (System.currentTimeMillis() - view.snapStartTime).toFloat() / PuzzleView.SNAP_DURATION_MS
                val t = snapElapsed.coerceIn(0f, 1f)
                val easeT = 1f - (1f - t) * (1f - t)
                val curCol = view.snapFromCol + (block.col - view.snapFromCol) * easeT
                val curRow = view.snapFromRow + (block.row - view.snapFromRow) * easeT
                left = view.boardLeft + curCol * view.cellSize + padding
                top  = view.boardTop  + curRow * view.cellSize + padding
            } else if (isDragging && view.dragAxis != 0) {
                left += view.dragSmoothX
                top  += view.dragSmoothY
            }

            val widthCells  = if (block.isHorizontal || block.length == 1) block.length else 1
            val heightCells = if (!block.isHorizontal || block.length == 1) block.length else 1
            val visualRight  = if (isDragging || isSnapping) left + (widthCells * view.cellSize - 2 * padding) else right
            val visualBottom = if (isDragging || isSnapping) top  + (heightCells * view.cellSize - 2 * padding) else bottom

            if (block.id == view.hintBlockId) {
                drawHintGlow(canvas, block, left, top, visualRight, visualBottom, cr)
            }

            if (block.isWall) {
                drawWallBlock(canvas, left, top, visualRight, visualBottom, cr, padding)
                continue
            }

            if (block.isCat) {
                drawCatBlock(canvas, g, block, left, top, visualRight, visualBottom, isDragging)
            } else {
                drawNormalBlock(canvas, block, left, top, visualRight, visualBottom, cr, isDragging, isSnapping)
            }
        }
    }

    private fun drawCatSlideOut(canvas: Canvas, g: PuzzleGrid, block: PuzzleBlock,
                                 padding: Float, cr: Float, progress: Float) {
        var left = view.boardLeft + block.col * view.cellSize + padding
        var top  = view.boardTop  + block.row * view.cellSize + padding
        val right: Float
        val bottom: Float

        if (block.isHorizontal) {
            right  = view.boardLeft + (block.col + block.length) * view.cellSize - padding
            bottom = view.boardTop  + (block.row + 1) * view.cellSize - padding
        } else {
            right  = view.boardLeft + (block.col + 1) * view.cellSize - padding
            bottom = view.boardTop  + (block.row + block.length) * view.cellSize - padding
        }

        val cats = g.blocks.filter { it.isCat }
        val slideDir = if (g.exitDirection2 != null && cats.size >= 2 && block.id != cats.first().id) {
            g.exitDirection2
        } else g.exitDirection
        val slideDistance = view.cellSize * 3f * progress
        when (slideDir) {
            ExitDirection.RIGHT  -> left += slideDistance
            ExitDirection.LEFT   -> left -= slideDistance
            ExitDirection.BOTTOM -> top  += slideDistance
            ExitDirection.TOP    -> top  -= slideDistance
        }
        val slideRight  = left + (right - (view.boardLeft + block.col * view.cellSize + padding))
        val slideBottom = top + (bottom - (view.boardTop + block.row * view.cellSize + padding))

        val cx = (left + slideRight) / 2f
        val cy = (top + slideBottom) / 2f
        val imgSize = min(slideRight - left, slideBottom - top) * 0.92f

        val bmp = view.catBitmap
        if (bmp != null && !bmp.isRecycled) {
            val imgRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, imgRect, null)
        } else {
            canvas.drawCircle(cx, cy, imgSize * 0.45f, paints.slideOutFallbackPaint)
            paints.textPaint.textSize = imgSize * 0.35f
            canvas.drawText("\uD83D\uDC31", cx, cy + paints.textPaint.textSize * 0.3f, paints.textPaint)
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────

    private fun drawParticles(canvas: Canvas) {
        for (p in view.particles) {
            if (p.alpha <= 0f) continue
            paints.particlePaint.color = p.color
            paints.particlePaint.alpha = (p.alpha * 255).toInt()
            when (p.shape) {
                1 -> drawStarShape(canvas, p.x, p.y, p.radius, paints.particlePaint)
                2 -> {
                    canvas.save()
                    canvas.rotate(p.rotation, p.x, p.y)
                    canvas.drawRect(
                        p.x - p.radius, p.y - p.radius * 0.4f,
                        p.x + p.radius, p.y + p.radius * 0.4f,
                        paints.particlePaint
                    )
                    canvas.restore()
                }
                else -> canvas.drawCircle(p.x, p.y, p.radius, paints.particlePaint)
            }
        }
    }

    private fun drawStarShape(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        paints.starPath.reset()
        val inner = radius * 0.45f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else inner
            val angle = Math.PI / 5.0 * i - Math.PI / 2.0
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) paints.starPath.moveTo(x, y) else paints.starPath.lineTo(x, y)
        }
        paints.starPath.close()
        canvas.drawPath(paints.starPath, paint)
    }

    // ── Tutorial overlay ──────────────────────────────────────────────────

    private fun drawTutorialOverlay(canvas: Canvas) {
        val density = view.resources.displayMetrics.density
        val w = view.width.toFloat()
        val h = view.height.toFloat()

        val overlayAlpha = (0.55f + 0.08f * sin(view.tutorialPulse.toDouble())).toFloat()
        paints.tutBgPaint.color = Color.BLACK
        paints.tutBgPaint.alpha = (overlayAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, paints.tutBgPaint)

        val panelW = w * 0.85f
        val panelH = 160 * density
        val panelL = (w - panelW) / 2f
        val panelT = h * 0.35f
        paints.tutPanelPaint.color = 0xFFFFF8F0.toInt()
        canvas.drawRoundRect(
            RectF(panelL, panelT, panelL + panelW, panelT + panelH),
            20 * density, 20 * density, paints.tutPanelPaint
        )

        paints.tutTitlePaint.textSize = 22 * density
        paints.tutBodyPaint.textSize = 15 * density
        val cx = w / 2f

        when (view.tutorialStep) {
            0 -> {
                canvas.drawText("Welcome!", cx, panelT + 48 * density, paints.tutTitlePaint)
                canvas.drawText("Swipe blocks to clear", cx, panelT + 80 * density, paints.tutBodyPaint)
                canvas.drawText("a path for the cat!", cx, panelT + 102 * density, paints.tutBodyPaint)
            }
            1 -> {
                canvas.drawText("Goal", cx, panelT + 48 * density, paints.tutTitlePaint)
                canvas.drawText("Move the cat to the", cx, panelT + 80 * density, paints.tutBodyPaint)
                canvas.drawText("green exit to rescue it!", cx, panelT + 102 * density, paints.tutBodyPaint)
            }
            2 -> {
                canvas.drawText("Stars", cx, panelT + 48 * density, paints.tutTitlePaint)
                canvas.drawText("Use fewer moves to", cx, panelT + 80 * density, paints.tutBodyPaint)
                canvas.drawText("earn more stars!", cx, panelT + 102 * density, paints.tutBodyPaint)
            }
            10 -> {
                canvas.drawText("New Mechanic!", cx, panelT + 48 * density, paints.tutTitlePaint)
                canvas.drawText("Keys unlock blocked paths.", cx, panelT + 80 * density, paints.tutBodyPaint)
                canvas.drawText("Move the key to the lock!", cx, panelT + 102 * density, paints.tutBodyPaint)
            }
            20 -> {
                canvas.drawText("Checkpoints!", cx, panelT + 48 * density, paints.tutTitlePaint)
                canvas.drawText("Pass through the star", cx, panelT + 80 * density, paints.tutBodyPaint)
                canvas.drawText("before reaching the exit!", cx, panelT + 102 * density, paints.tutBodyPaint)
            }
        }

        if (view.tutorialAutoDismissAt == 0L) {
            val pulsed = (sin(view.tutorialPulse * 2.0) * 0.3 + 0.7).toFloat()
            paints.tutHintPaint.textSize = 13 * density
            paints.tutHintPaint.alpha = (pulsed * 255).toInt()
            canvas.drawText("Tap to continue", cx, panelT + panelH - 18 * density, paints.tutHintPaint)
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private fun drawToolbar(canvas: Canvas) {
        val density    = view.resources.displayMetrics.density
        val arrowExtra = if (view.grid?.exitDirection == ExitDirection.BOTTOM) PuzzleView.ARROW_AREA_DP * density else 0f
        val toolbarTop = view.boardTop + view.boardSize + arrowExtra + view.cellSize * 0.8f
        val btnH       = 46 * density
        val btnW       = (view.width * 0.21f)
        val margin     = (view.width - btnW * 4) / 5f

        paints.buttonTextPaint.textSize = 13 * density

        view.undoRect.set(margin, toolbarTop, margin + btnW, toolbarTop + btnH)
        paints.buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(view.undoRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            "Undo",
            view.undoRect.centerX(), view.undoRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        val hintLeft = margin * 2 + btnW
        view.hintRect.set(hintLeft, toolbarTop, hintLeft + btnW, toolbarTop + btnH)
        val hintPulse = view.pulse(500.0, 0.15f, 0.85f)
        val hintR = (0xFF * hintPulse).toInt().coerceIn(0, 255)
        val hintG = (0xC0 * hintPulse).toInt().coerceIn(0, 255)
        paints.buttonPaint.color = android.graphics.Color.argb(255, hintR, hintG, 0)
        canvas.drawRoundRect(view.hintRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            "Hint${if (view.hintCount > 0) " ${view.hintCount}" else ""}",
            view.hintRect.centerX(), view.hintRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        val solveLeft = margin * 3 + btnW * 2
        view.solveRect.set(solveLeft, toolbarTop, solveLeft + btnW, toolbarTop + btnH)
        paints.buttonPaint.color = if (view.autoSolving) 0xFF7E57C2.toInt() else 0xFF9575CD.toInt()
        canvas.drawRoundRect(view.solveRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            if (view.autoSolving) "..." else "Solve",
            view.solveRect.centerX(), view.solveRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        val resetLeft = margin * 4 + btnW * 3
        view.resetRect.set(resetLeft, toolbarTop, resetLeft + btnW, toolbarTop + btnH)
        paints.buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(view.resetRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            "Reset",
            view.resetRect.centerX(), view.resetRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        // ── Power-up buttons (below toolbar) ─────────────────────────
        if (view.state == PuzzleState.PLAYING && !view.isEndless) {
            val puTop = toolbarTop + btnH + 8 * density
            val puSize = 38 * density
            val puGap = 10 * density
            val puTotalW = puSize * 3 + puGap * 2
            var puX = (view.width - puTotalW) / 2f

            val powerUps = arrayOf(
                Triple("\uD83E\uDDF2", "30", 0xFF42A5F5.toInt()),  // Magnet
                Triple("\u2744", "40", 0xFF4DD0E1.toInt()),         // Ice
                Triple("\uD83D\uDD00", "50", 0xFFAB47BC.toInt())   // Shuffle
            )

            paints.powerUpIconPaint.textSize = 16 * density
            paints.powerUpLabelPaint.textSize = 9 * density

            for (i in powerUps.indices) {
                val (icon, cost, color) = powerUps[i]
                view.powerUpRects[i].set(puX, puTop, puX + puSize, puTop + puSize)

                paints.powerUpBgPaint.color = color
                paints.powerUpBgPaint.alpha = if (view.displayCoins >= cost.toInt()) 255 else 100
                canvas.drawRoundRect(view.powerUpRects[i], 10 * density, 10 * density, paints.powerUpBgPaint)

                canvas.drawText(icon, puX + puSize / 2f, puTop + puSize * 0.5f, paints.powerUpIconPaint)
                canvas.drawText("\uD83E\uDE99$cost", puX + puSize / 2f, puTop + puSize * 0.85f, paints.powerUpLabelPaint)

                puX += puSize + puGap
            }
        }
    }

    // ── Victory overlay ───────────────────────────────────────────────────

    private fun drawVictoryOverlay(canvas: Canvas, g: PuzzleGrid) {
        val alpha = (view.victoryAlpha * 200).toInt()
        paints.overlayPaint.color = Color.argb(alpha, 255, 248, 240)
        canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), paints.overlayPaint)

        if (view.victoryAlpha < 0.5f) return

        val density = view.resources.displayMetrics.density
        val cx      = view.width / 2f
        val cy      = view.height * 0.36f

        val panelW  = view.width * 0.82f
        val panelH  = view.height * 0.55f
        val panelL  = cx - panelW / 2f
        val panelT  = cy - panelH / 2f
        canvas.drawRoundRect(
            RectF(panelL, panelT, panelL + panelW, panelT + panelH),
            24 * density, 24 * density, paints.victoryPanelPaint
        )

        paints.victoryTitlePaint.textSize = 28 * density
        val clearTitle = if (view.isEndless) "Endless #${view.endlessCount} Clear!" else "Stage Clear!"
        canvas.drawText(clearTitle, cx, panelT + 46 * density, paints.victoryTitlePaint)

        paints.victoryMovePaint.textSize = 16 * density
        canvas.drawText("Moves: ${g.getMoveCount()}", cx, panelT + 70 * density, paints.victoryMovePaint)

        // Score display
        paints.scorePaint.textSize = 18 * density
        paints.scorePaint.color = 0xFFFFD600.toInt()
        canvas.drawText("Score: ${view.score}", cx, panelT + 90 * density, paints.scorePaint)

        // NEW RECORD banner
        if (view.isNewRecord && view.starAnimPhase > 1f) {
            val recAlpha = ((view.starAnimPhase - 1f) / 0.5f).coerceIn(0f, 1f)
            val pulse = 1f + 0.05f * sin(view.starAnimPhase * 4.0).toFloat()
            paints.newRecordPaint.textSize = 14 * density * pulse
            paints.newRecordPaint.alpha = (recAlpha * 255).toInt()
            canvas.drawText("\u2605 NEW RECORD! \u2605", cx, panelT + 108 * density, paints.newRecordPaint)
        }

        val starSize = 34 * density
        val starGap  = 8 * density
        val totalW   = 3 * starSize + 2 * starGap
        var starX    = cx - totalW / 2f
        val starY    = panelT + 116 * density
        val pulseAmp = if (view.victoryStars == 3) 0.18f else 0.08f
        val pulsed   = 1f + pulseAmp * sin(view.starAnimPhase.toDouble()).toFloat()

        if (view.victoryStars == 3) {
            val shimmerAlpha = ((sin(view.starAnimPhase.toDouble()) * 0.3 + 0.5) * 255).toInt().coerceIn(0, 255)
            paints.victoryShimmerPaint.maskFilter = BlurMaskFilter(starSize * 1.2f, BlurMaskFilter.Blur.NORMAL)
            paints.victoryShimmerPaint.alpha = shimmerAlpha
            canvas.drawCircle(cx, starY + starSize / 2f, totalW * 0.65f, paints.victoryShimmerPaint)
        }

        for (i in 1..3) {
            val earned  = i <= view.victoryStars
            val starDelay = (i - 1) * 0.4f
            val starPhase = (view.starAnimPhase - starDelay).coerceAtLeast(0f)
            val popScale = when {
                starPhase < 0.01f -> 0f
                starPhase < 0.3f  -> (starPhase / 0.3f) * 1.25f
                starPhase < 0.5f  -> 1.25f - ((starPhase - 0.3f) / 0.2f) * 0.25f
                else              -> if (earned) pulsed else 1f
            }
            val scale   = if (earned) popScale else minOf(popScale, 1f)
            val scaledS = starSize * scale
            val offsetX2 = (scaledS - starSize) / 2f
            val offsetY2 = (scaledS - starSize) / 2f
            val rect    = RectF(
                starX - offsetX2, starY - offsetY2,
                starX + starSize + offsetX2, starY + starSize + offsetY2
            )
            if (scale > 0f) {
                val bmp = if (earned) view.starFullBitmap else view.starEmptyBitmap
                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, null, rect, null)
                } else {
                    paints.victoryStarPaint.color = if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt()
                    canvas.drawCircle(rect.centerX(), rect.centerY(), starSize / 2f * scale, paints.victoryStarPaint)
                }
            }
            starX += starSize + starGap
        }

        if (view.victoryStars == 3 && view.starAnimPhase > 1.5f) {
            val perfectAlpha = ((view.starAnimPhase - 1.5f) / 0.5f).coerceIn(0f, 1f)
            paints.victoryPerfectPaint.textSize = 22 * density
            paints.victoryPerfectPaint.alpha = (perfectAlpha * 255).toInt()
            canvas.drawText("PERFECT!", cx, starY - 10 * density, paints.victoryPerfectPaint)
        }

        val btnW  = panelW * 0.7f
        val btnH2 = 40 * density
        val btnGap = 8 * density
        val btnL  = cx - btnW / 2f
        var btnY  = starY + starSize + 16 * density

        view.nextStageRect.set(btnL, btnY, btnL + btnW, btnY + btnH2)
        paints.buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(view.nextStageRect, 12 * density, 12 * density, paints.buttonPaint)
        paints.buttonTextPaint.textSize = 16 * density
        canvas.drawText(
            if (view.isEndless) "Next Puzzle  \u25B6" else "Next Stage  \u25B6",
            view.nextStageRect.centerX(),
            view.nextStageRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        btnY += btnH2 + btnGap
        view.retryRect.set(btnL, btnY, btnL + btnW, btnY + btnH2)
        paints.buttonPaint.color = 0xFF26A69A.toInt()
        canvas.drawRoundRect(view.retryRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            "\u21BB  Retry",
            view.retryRect.centerX(),
            view.retryRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        btnY += btnH2 + btnGap

        // Share button
        val shareBtnW = btnW * 0.48f
        val menuBtnW = btnW * 0.48f
        val shareL = cx - btnW / 2f
        val menuL = shareL + shareBtnW + btnGap

        view.shareRect.set(shareL, btnY, shareL + shareBtnW, btnY + btnH2)
        paints.shareBtnPaint.color = 0xFF42A5F5.toInt()
        canvas.drawRoundRect(view.shareRect, 12 * density, 12 * density, paints.shareBtnPaint)
        canvas.drawText(
            "\uD83D\uDCE4 Share",
            view.shareRect.centerX(),
            view.shareRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )

        view.levelSelectRect.set(menuL, btnY, menuL + menuBtnW, btnY + btnH2)
        paints.buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(view.levelSelectRect, 12 * density, 12 * density, paints.buttonPaint)
        canvas.drawText(
            if (view.isEndless) "Menu" else "Levels",
            view.levelSelectRect.centerX(),
            view.levelSelectRect.centerY() + paints.buttonTextPaint.textSize * 0.35f,
            paints.buttonTextPaint
        )
    }
}
