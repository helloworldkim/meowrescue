package com.meowrescue.game.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import com.meowrescue.game.R
import com.meowrescue.game.puzzle.PuzzleGrid
import com.meowrescue.game.util.SoundManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PuzzleState { PLAYING, SOLVED, PAUSED }

class PuzzleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        val BLOCK_COLORS = intArrayOf(
            0xFF78909C.toInt(),  // dusty blue
            0xFF81C784.toInt(),  // sage green
            0xFFFFD54F.toInt(),  // warm yellow
            0xFFCE93D8.toInt(),  // soft purple
            0xFFF48FB1.toInt(),  // soft pink
            0xFF4FC3F7.toInt(),  // sky blue
        )
        val CAT_COLOR      = 0xFFFF7043.toInt()
        val GRID_BG        = 0xFFEFEBE9.toInt()
        val CELL_LINE      = 0xFFD7CCC8.toInt()
        val BG_COLOR       = 0xFFFFF8F0.toInt()
        val EXIT_COLOR     = 0xFF66BB6A.toInt()

        private const val TARGET_FPS = 60L
        private const val FRAME_MS   = 1000L / TARGET_FPS
    }

    // ── Callbacks ──────────────────────────────────────────────────────────
    var onStageClear: ((moves: Int, stars: Int) -> Unit)? = null
    var onPauseClicked: (() -> Unit)? = null

    // ── State ──────────────────────────────────────────────────────────────
    private var state = PuzzleState.PLAYING
    private var grid: PuzzleGrid? = null
    private var initialGrid: PuzzleGrid? = null
    private var stageNumber = 1
    private var optimalMoves = 1

    // ── Layout ─────────────────────────────────────────────────────────────
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop  = 0f
    private var boardSize = 0f   // square board

    // ── Victory animation ──────────────────────────────────────────────────
    private var victoryAlpha  = 0f      // 0..1
    private var victoryStars  = 0
    private var starAnimPhase = 0f      // drives pulsing

    // ── Drag tracking ─────────────────────────────────────────────────────
    private var dragBlockId  = -1
    private var dragStartX   = 0f
    private var dragStartY   = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f

    // ── HUD button rects (for hit-testing) ────────────────────────────────
    private val pauseRect  = RectF()
    private val undoRect   = RectF()
    private val resetRect  = RectF()

    // ── Resources ─────────────────────────────────────────────────────────
    private var pauseBitmap: Bitmap? = null
    private var starFullBitmap: Bitmap? = null
    private var starEmptyBitmap: Bitmap? = null

    // ── Paints (reused across frames) ─────────────────────────────────────
    private val gridBgPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRID_BG }
    private val linePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CELL_LINE; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val blockPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blockShadow   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000; style = Paint.Style.FILL
    }
    private val textPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val hudTextPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val hudBgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF3E0.toInt()
    }
    private val exitPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXIT_COLOR }
    private val overlayPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt()
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    // glossPaint for block highlight — reused each frame
    private val glossPaint    = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Render thread ──────────────────────────────────────────────────────
    private var renderThread: Thread? = null
    @Volatile private var running = false

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
        loadBitmaps()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    fun setGrid(grid: PuzzleGrid, stage: Int, optimalMoves: Int) {
        this.grid         = grid
        this.initialGrid  = grid.clone()
        this.stageNumber  = stage
        this.optimalMoves = optimalMoves
        this.state        = PuzzleState.PLAYING
        this.victoryAlpha = 0f
        this.dragBlockId  = -1
        recalcLayout()
    }

    fun undoMove() {
        if (state != PuzzleState.PLAYING) return
        val moved = grid?.undoLastMove() ?: false
        if (moved) SoundManager.playButtonTap()
    }

    fun resetPuzzle() {
        val init = initialGrid ?: return
        grid = init.clone()
        state = PuzzleState.PLAYING
        victoryAlpha = 0f
        dragBlockId  = -1
        SoundManager.playButtonTap()
    }

    fun pause() {
        if (state == PuzzleState.PLAYING) state = PuzzleState.PAUSED
    }

    fun resume() {
        if (state == PuzzleState.PAUSED) state = PuzzleState.PLAYING
    }

    fun recycleBitmaps() {
        pauseBitmap?.recycle();   pauseBitmap   = null
        starFullBitmap?.recycle(); starFullBitmap = null
        starEmptyBitmap?.recycle(); starEmptyBitmap = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // SurfaceHolder.Callback
    // ──────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) {
        recalcLayout()
        startRenderThread()
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        recalcLayout()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopRenderThread()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Touch
    // ──────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleDown(x, y)
            MotionEvent.ACTION_MOVE -> handleMove(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(x, y)
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        // HUD button hit-test first
        if (pauseRect.contains(x, y)) {
            SoundManager.playButtonTap()
            onPauseClicked?.invoke()
            return
        }
        if (state != PuzzleState.PLAYING) {
            if (state == PuzzleState.SOLVED) {
                if (undoRect.contains(x, y) || resetRect.contains(x, y)) return
            }
            return
        }
        if (undoRect.contains(x, y)) {
            undoMove(); return
        }
        if (resetRect.contains(x, y)) {
            resetPuzzle(); return
        }

        // Find touched block
        val g = grid ?: return
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        if (col < 0 || col >= g.cols || row < 0 || row >= g.rows) return
        val gridSnap = g.getGrid()
        val blockId = gridSnap[row][col]
        if (blockId == -1) return

        dragBlockId  = blockId
        dragStartX   = x
        dragStartY   = y
        dragCurrentX = x
        dragCurrentY = y
    }

    private fun handleMove(x: Float, y: Float) {
        if (dragBlockId == -1) return
        dragCurrentX = x
        dragCurrentY = y
    }

    private fun handleUp(x: Float, y: Float) {
        if (dragBlockId == -1) return
        val g = grid ?: run { dragBlockId = -1; return }
        val block = g.blocks.firstOrNull { it.id == dragBlockId }
        if (block == null) { dragBlockId = -1; return }

        val dx = x - dragStartX
        val dy = y - dragStartY

        if (block.isHorizontal) {
            val rawSteps = (dx / cellSize).roundToInt()
            if (rawSteps != 0) attemptMove(g, dragBlockId, rawSteps)
        } else {
            val rawSteps = (dy / cellSize).roundToInt()
            if (rawSteps != 0) attemptMove(g, dragBlockId, rawSteps)
        }

        dragBlockId = -1
    }

    private fun attemptMove(g: PuzzleGrid, blockId: Int, steps: Int) {
        // Clamp steps to maximum valid
        val direction = if (steps > 0) 1 else -1
        var validSteps = 0
        for (s in 1..abs(steps)) {
            if (g.canMove(blockId, direction * s)) validSteps = direction * s
            else break
        }
        if (validSteps == 0) return

        val moved = g.moveBlock(blockId, validSteps)
        if (moved) {
            SoundManager.playCageDestroy()
            if (g.isSolved()) triggerVictory(g)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Victory
    // ──────────────────────────────────────────────────────────────────────

    private fun triggerVictory(g: PuzzleGrid) {
        val moves = g.getMoveCount()
        victoryStars = when {
            moves <= optimalMoves             -> 3
            moves <= (optimalMoves * 1.5f).toInt() -> 2
            else                              -> 1
        }
        state = PuzzleState.SOLVED
        SoundManager.playLevelClear()
        onStageClear?.invoke(moves, victoryStars)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Render loop
    // ──────────────────────────────────────────────────────────────────────

    private fun startRenderThread() {
        running = true
        renderThread = Thread {
            while (running) {
                val start = System.currentTimeMillis()
                drawFrame()
                val elapsed = System.currentTimeMillis() - start
                val sleep   = FRAME_MS - elapsed
                if (sleep > 0) Thread.sleep(sleep)
            }
        }.also { it.name = "PuzzleRenderThread"; it.start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.join(500)
        renderThread = null
    }

    private fun drawFrame() {
        val h = holder
        if (!h.surface.isValid) return
        val canvas = h.lockCanvas() ?: return
        try {
            update()
            render(canvas)
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }

    private fun update() {
        if (state == PuzzleState.SOLVED) {
            victoryAlpha = min(1f, victoryAlpha + 0.04f)
            starAnimPhase += 0.05f
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────

    private fun render(canvas: Canvas) {
        canvas.drawColor(BG_COLOR)
        val g = grid ?: return

        drawHud(canvas, g)
        drawBoard(canvas, g)
        drawExit(canvas, g)
        drawBlocks(canvas, g)
        drawToolbar(canvas)

        if (state == PuzzleState.SOLVED && victoryAlpha > 0f) {
            drawVictoryOverlay(canvas, g)
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────

    private fun drawHud(canvas: Canvas, g: PuzzleGrid) {
        val density = resources.displayMetrics.density
        val hudH    = (56 * density).toInt().toFloat()
        val hudTop  = 0f
        val w       = width.toFloat()

        // HUD background
        hudBgPaint.color = 0xFFFFF3E0.toInt()
        canvas.drawRect(0f, hudTop, w, hudTop + hudH, hudBgPaint)

        // Stage label (left)
        hudTextPaint.textSize = 18 * density
        hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Stage $stageNumber", 16 * density, hudTop + hudH * 0.65f, hudTextPaint)

        // Move counter (center)
        hudTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Moves: ${g.getMoveCount()}", w / 2f, hudTop + hudH * 0.65f, hudTextPaint)

        // Pause button (right)
        val btnSize   = 40 * density
        val btnMargin = 8 * density
        val btnLeft   = w - btnSize - btnMargin
        val btnTop2   = hudTop + (hudH - btnSize) / 2f
        pauseRect.set(btnLeft, btnTop2, btnLeft + btnSize, btnTop2 + btnSize)

        val pb = pauseBitmap
        if (pb != null && !pb.isRecycled) {
            canvas.drawBitmap(pb, null, pauseRect, null)
        } else {
            // fallback: draw two vertical bars
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4E342E.toInt() }
            val barW = btnSize * 0.22f
            val barH = btnSize * 0.55f
            val cx   = pauseRect.centerX()
            val cy   = pauseRect.centerY()
            canvas.drawRect(cx - barW * 1.5f, cy - barH / 2f, cx - barW * 0.5f, cy + barH / 2f, p)
            canvas.drawRect(cx + barW * 0.5f, cy - barH / 2f, cx + barW * 1.5f, cy + barH / 2f, p)
        }
    }

    // ── Board ─────────────────────────────────────────────────────────────

    private fun drawBoard(canvas: Canvas, g: PuzzleGrid) {
        val r = RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize)
        canvas.drawRoundRect(r, 8f, 8f, gridBgPaint)

        // Grid lines
        for (i in 0..g.rows) {
            val y = boardTop + i * cellSize
            canvas.drawLine(boardLeft, y, boardLeft + boardSize, y, linePaint)
        }
        for (j in 0..g.cols) {
            val x = boardLeft + j * cellSize
            canvas.drawLine(x, boardTop, x, boardTop + boardSize, linePaint)
        }
    }

    // ── Exit indicator ────────────────────────────────────────────────────

    private fun drawExit(canvas: Canvas, g: PuzzleGrid) {
        val exitY  = boardTop + g.exitRow * cellSize
        val arrowW = cellSize * 0.5f
        val arrowH = cellSize * 0.6f
        val cx     = boardLeft + boardSize + arrowW * 0.4f
        val cy     = exitY + cellSize / 2f

        // Draw a simple right-pointing triangle arrow
        exitPaint.style = Paint.Style.FILL
        val path = Path().apply {
            moveTo(cx, cy - arrowH / 2f)
            lineTo(cx + arrowW, cy)
            lineTo(cx, cy + arrowH / 2f)
            close()
        }
        canvas.drawPath(path, exitPaint)

        // Highlight exit row gap on board edge
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXIT_COLOR; alpha = 80
        }
        canvas.drawRect(
            boardLeft + boardSize - 4f, exitY,
            boardLeft + boardSize + 4f, exitY + cellSize,
            gapPaint
        )
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private fun drawBlocks(canvas: Canvas, g: PuzzleGrid) {
        val padding = cellSize * 0.07f
        val cr      = min(cellSize * 0.22f, 24f)   // corner radius scales with cell

        for (block in g.blocks) {
            val isDragging = (block.id == dragBlockId)

            // Calculate visual position (offset by drag delta)
            var left = boardLeft  + block.col * cellSize + padding
            var top  = boardTop   + block.row * cellSize + padding
            val right: Float
            val bottom: Float

            if (block.isHorizontal) {
                right  = boardLeft + (block.col + block.length) * cellSize - padding
                bottom = boardTop  + (block.row + 1) * cellSize - padding
                if (isDragging) {
                    val delta = (dragCurrentX - dragStartX).coerceIn(
                        -block.col * cellSize,
                        (g.cols - block.col - block.length) * cellSize
                    )
                    left  += delta
                    // right stays relative to left
                }
            } else {
                right  = boardLeft + (block.col + 1) * cellSize - padding
                bottom = boardTop  + (block.row + block.length) * cellSize - padding
                if (isDragging) {
                    val delta = (dragCurrentY - dragStartY).coerceIn(
                        -block.row * cellSize,
                        (g.rows - block.row - block.length) * cellSize
                    )
                    top += delta
                }
            }

            val visualRight  = if (block.isHorizontal && isDragging) left + (block.length * cellSize - 2 * padding) else right
            val visualBottom = if (!block.isHorizontal && isDragging) top  + (block.length * cellSize - 2 * padding) else bottom

            val color = if (block.isCat) CAT_COLOR
                        else BLOCK_COLORS[(block.id - 1) % BLOCK_COLORS.size]

            // Shadow (slightly offset)
            if (isDragging) {
                blockShadow.alpha = 80
                val shadowRect = RectF(left + 6f, top + 6f, visualRight + 6f, visualBottom + 6f)
                canvas.drawRoundRect(shadowRect, cr, cr, blockShadow)
            }

            // Block body
            blockPaint.color = color
            val blockRect = RectF(left, top, visualRight, visualBottom)
            canvas.drawRoundRect(blockRect, cr, cr, blockPaint)

            // Highlight (top-left gloss)
            glossPaint.shader = LinearGradient(
                left, top, left, top + (visualBottom - top) * 0.4f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(blockRect, cr, cr, glossPaint)

            // Label
            if (block.isCat) {
                textPaint.textSize = min(cellSize * 0.45f, 20f)
                canvas.drawText(
                    "CAT",
                    (left + visualRight) / 2f,
                    (top + visualBottom) / 2f + textPaint.textSize * 0.35f,
                    textPaint
                )
            }
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private fun drawToolbar(canvas: Canvas) {
        val density    = resources.displayMetrics.density
        val toolbarTop = boardTop + boardSize + cellSize * 0.3f
        val btnH       = 48 * density
        val btnW       = (width * 0.35f)
        val margin     = (width - btnW * 2) / 3f

        // Undo button
        undoRect.set(margin, toolbarTop, margin + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(undoRect, 12 * density, 12 * density, buttonPaint)
        buttonTextPaint.textSize = 16 * density
        canvas.drawText(
            "\u21A9 Undo",
            undoRect.centerX(), undoRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // Reset button
        val resetLeft = margin * 2 + btnW
        resetRect.set(resetLeft, toolbarTop, resetLeft + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(resetRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "\u21BB Reset",
            resetRect.centerX(), resetRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )
    }

    // ── Victory overlay ───────────────────────────────────────────────────

    private fun drawVictoryOverlay(canvas: Canvas, g: PuzzleGrid) {
        val alpha = (victoryAlpha * 200).toInt()
        overlayPaint.color = Color.argb(alpha, 255, 248, 240)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        if (victoryAlpha < 0.5f) return   // wait for fade-in before showing text

        val density = resources.displayMetrics.density
        val cx      = width / 2f
        val cy      = height * 0.38f

        // Panel
        val panelW  = width * 0.78f
        val panelH  = height * 0.38f
        val panelL  = cx - panelW / 2f
        val panelT  = cy - panelH / 2f
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF8F0.toInt()
            setShadowLayer(12f, 0f, 4f, 0x44000000)
        }
        canvas.drawRoundRect(
            RectF(panelL, panelT, panelL + panelW, panelT + panelH),
            24 * density, 24 * density, panelPaint
        )

        // "Stage Clear!" text
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF7043.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = 28 * density
        }
        canvas.drawText("Stage Clear!", cx, panelT + 56 * density, titlePaint)

        // Move count
        val movePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4E342E.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = 16 * density
        }
        canvas.drawText("Moves: ${g.getMoveCount()}", cx, panelT + 84 * density, movePaint)

        // Stars
        val starSize = 36 * density
        val starGap  = 8 * density
        val totalW   = 3 * starSize + 2 * starGap
        var starX    = cx - totalW / 2f
        val starY    = panelT + 110 * density
        val pulse    = 1f + 0.08f * kotlin.math.sin(starAnimPhase.toDouble()).toFloat()

        for (i in 1..3) {
            val earned  = i <= victoryStars
            val bmp     = if (earned) starFullBitmap else starEmptyBitmap
            val scale   = if (earned) pulse else 1f
            val scaledS = starSize * scale
            val offsetX = (scaledS - starSize) / 2f
            val offsetY = (scaledS - starSize) / 2f
            val rect    = RectF(
                starX - offsetX, starY - offsetY,
                starX + starSize + offsetX, starY + starSize + offsetY
            )
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, null, rect, null)
            } else {
                // Fallback: filled/empty circle
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt()
                }
                canvas.drawCircle(rect.centerX(), rect.centerY(), starSize / 2f * scale, starPaint)
            }
            starX += starSize + starGap
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Layout helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun recalcLayout() {
        val g = grid ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density    = resources.displayMetrics.density
        val hudH       = 56 * density
        val toolbarH   = 80 * density
        val arrowArea  = 48 * density   // space for exit arrow on right

        val availableW = w - arrowArea
        val availableH = h - hudH - toolbarH - (24 * density)

        val maxBoard = min(availableW, availableH)
        // Board occupies 60-70% of screen height, capped by width
        val targetH  = (h * 0.65f).coerceIn(availableH * 0.55f, availableH)
        boardSize    = min(maxBoard, targetH)

        cellSize  = boardSize / max(g.rows, g.cols)
        boardLeft = (availableW - boardSize) / 2f
        boardTop  = hudH + (availableH - boardSize) / 2f
    }

    // ──────────────────────────────────────────────────────────────────────
    // Resource loading
    // ──────────────────────────────────────────────────────────────────────

    private fun loadBitmaps() {
        val density = resources.displayMetrics.density
        val iconSz  = (40 * density).toInt()

        try {
            val pauseDrawable = ResourcesCompat.getDrawable(resources, R.drawable.icon_pause, null)
            pauseDrawable?.let {
                pauseBitmap = Bitmap.createBitmap(iconSz, iconSz, Bitmap.Config.ARGB_8888)
                val c = Canvas(pauseBitmap!!)
                it.setBounds(0, 0, iconSz, iconSz)
                it.draw(c)
            }
        } catch (_: Exception) { /* icon_pause.png missing → fallback drawn in drawHud */ }

        try {
            val starFullDrawable  = ResourcesCompat.getDrawable(resources, R.drawable.star_full, null)
            val starEmptyDrawable = ResourcesCompat.getDrawable(resources, R.drawable.star_empty, null)
            val starSz = (36 * density).toInt()
            starFullDrawable?.let {
                starFullBitmap = Bitmap.createBitmap(starSz, starSz, Bitmap.Config.ARGB_8888)
                val c = Canvas(starFullBitmap!!)
                it.setBounds(0, 0, starSz, starSz); it.draw(c)
            }
            starEmptyDrawable?.let {
                starEmptyBitmap = Bitmap.createBitmap(starSz, starSz, Bitmap.Config.ARGB_8888)
                val c = Canvas(starEmptyBitmap!!)
                it.setBounds(0, 0, starSz, starSz); it.draw(c)
            }
        } catch (_: Exception) { /* fallback circles drawn in drawVictoryOverlay */ }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }
}
