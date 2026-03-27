package com.meowrescue.game.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import com.meowrescue.game.R
import com.meowrescue.game.puzzle.ExitDirection
import com.meowrescue.game.puzzle.PuzzleGrid
import com.meowrescue.game.util.SoundManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PuzzleState { PLAYING, ESCAPING, SOLVED, PAUSED }

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
        private const val SNAP_DURATION_MS = 120L
        private const val WALL_OPEN_MS = 200L
        private const val CAT_SLIDE_MS = 300L
        private const val PARTICLE_MS  = 500L
    }

    // ── Callbacks ──────────────────────────────────────────────────────────
    var onStageClear: ((moves: Int, stars: Int) -> Unit)? = null
    var onNextStageClicked: (() -> Unit)? = null
    var onPauseClicked: (() -> Unit)? = null

    // ── Lock for thread-safe access between UI thread and render thread ────
    private val lock = Any()

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
    private var boardSize = 0f

    // ── Victory animation ──────────────────────────────────────────────────
    private var victoryAlpha  = 0f
    private var victoryStars  = 0
    private var starAnimPhase = 0f

    // ── Drag tracking ─────────────────────────────────────────────────────
    private var dragBlockId  = -1
    private var dragStartX   = 0f
    private var dragStartY   = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f

    // ── Drag constraint cache ────────────────────────────────────────────
    private var dragAxis: Int = 0          // 0=undecided, 1=horizontal, 2=vertical
    private var dragMaxNegPx: Float = 0f   // max negative drag offset (pixels)
    private var dragMaxPosPx: Float = 0f   // max positive drag offset (pixels)

    // ── Snap animation ────────────────────────────────────────────────────
    private var snapAnimating = false
    private var snapBlockId   = -1
    private var snapFromCol   = 0f
    private var snapFromRow   = 0f
    private var snapStartTime = 0L
    private var snapPendingSolveCheck = false

    // ── Cat escape animation ──────────────────────────────────────────────
    private var escapePhase = 0  // 0=none, 1=wall open, 2=cat slide, 3=particles
    private var escapeStartTime = 0L
    private var escapeMoves = 0
    private var escapeStars = 0

    // ── Particles ─────────────────────────────────────────────────────────
    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var color: Int, var alpha: Float
    )
    private val particles = mutableListOf<Particle>()
    private var particleStartTime = 0L

    // ── HUD button rects ────────────────────────────────────────────────
    private val pauseRect      = RectF()
    private val undoRect       = RectF()
    private val resetRect      = RectF()
    private val nextStageRect  = RectF()

    // ── Resources ─────────────────────────────────────────────────────────
    private var pauseBitmap: Bitmap? = null
    private var starFullBitmap: Bitmap? = null
    private var starEmptyBitmap: Bitmap? = null
    private var catBitmap: Bitmap? = null

    // ── Paints ─────────────────────────────────────────────────────────────
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
    private val glossPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        synchronized(lock) {
            this.grid         = grid
            this.initialGrid  = grid.clone()
            this.stageNumber  = stage
            this.optimalMoves = optimalMoves
            this.state        = PuzzleState.PLAYING
            this.victoryAlpha = 0f
            this.dragBlockId  = -1
            this.snapAnimating = false
            this.escapePhase  = 0
            this.particles.clear()
        }
        recalcLayout()
    }

    fun setCatBitmap(resId: Int) {
        try {
            val density = resources.displayMetrics.density
            val sz = (48 * density).toInt()
            val drawable = ResourcesCompat.getDrawable(resources, resId, null)
            drawable?.let {
                catBitmap?.recycle()
                catBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                val c = Canvas(catBitmap!!)
                it.setBounds(0, 0, sz, sz)
                it.draw(c)
            }
        } catch (_: Exception) { /* fallback to text */ }
    }

    fun undoMove() {
        val moved = synchronized(lock) {
            if (state != PuzzleState.PLAYING || snapAnimating) return
            grid?.undoLastMove() ?: false
        }
        if (moved) SoundManager.playButtonTap()
    }

    fun resetPuzzle() {
        val init = initialGrid ?: return
        synchronized(lock) {
            grid = init.clone()
            state = PuzzleState.PLAYING
            victoryAlpha = 0f
            dragBlockId  = -1
            snapAnimating = false
            escapePhase  = 0
            particles.clear()
        }
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
        catBitmap?.recycle(); catBitmap = null
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
        val callback: (() -> Unit)? = synchronized(lock) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handleDown(x, y)
                MotionEvent.ACTION_MOVE -> { handleMove(x, y); null }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handleUp(x, y); null }
                else -> null
            }
        }
        callback?.invoke()
        return true
    }

    /** Returns a callback to invoke outside synchronized(lock), or null. */
    private fun handleDown(x: Float, y: Float): (() -> Unit)? {
        if (pauseRect.contains(x, y)) {
            SoundManager.playButtonTap()
            val cb = onPauseClicked
            return { cb?.invoke() }
        }
        if (state == PuzzleState.SOLVED) {
            if (nextStageRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = onNextStageClicked
                return { cb?.invoke() }
            }
            return null
        }
        if (state != PuzzleState.PLAYING || snapAnimating) return null
        if (undoRect.contains(x, y)) {
            SoundManager.playButtonTap()
            return { undoMove() }
        }
        if (resetRect.contains(x, y)) {
            SoundManager.playButtonTap()
            return { resetPuzzle() }
        }

        val g = grid ?: return null
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        if (col < 0 || col >= g.cols || row < 0 || row >= g.rows) return null
        val gridSnap = g.getGrid()
        val blockId = gridSnap[row][col]
        if (blockId == -1) return null

        val block = g.blocks.firstOrNull { it.id == blockId } ?: return null
        dragBlockId  = blockId
        dragStartX   = x
        dragStartY   = y
        dragCurrentX = x
        dragCurrentY = y

        // Pre-compute valid drag range
        if (block.length == 1) {
            dragAxis = 0  // decide after threshold
            dragMaxNegPx = 0f
            dragMaxPosPx = 0f
        } else {
            dragAxis = if (block.isHorizontal) 1 else 2
            computeDragRange(g, blockId, block.isHorizontal)
        }
        return null
    }

    private fun handleMove(x: Float, y: Float) {
        if (dragBlockId == -1 || snapAnimating) return
        dragCurrentX = x
        dragCurrentY = y

        // Lock axis for 1-cell blocks after movement threshold
        if (dragAxis == 0) {
            val dx = abs(x - dragStartX)
            val dy = abs(y - dragStartY)
            val threshold = cellSize * 0.12f
            if (dx > threshold || dy > threshold) {
                val g = grid ?: return
                dragAxis = if (dx >= dy) 1 else 2
                computeDragRange(g, dragBlockId, dragAxis == 1)
            }
        }
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
        dragMaxNegPx = maxNeg * cellSize
        dragMaxPosPx = maxPos * cellSize
    }

    private fun handleUp(x: Float, y: Float) {
        if (dragBlockId == -1 || snapAnimating) return
        val g = grid ?: run { dragBlockId = -1; return }
        val block = g.blocks.firstOrNull { it.id == dragBlockId }
        if (block == null) { dragBlockId = -1; return }

        if (dragAxis == 0) {
            // Axis never determined (very small drag) — no move
            dragBlockId = -1
            dragMaxNegPx = 0f; dragMaxPosPx = 0f
            return
        }

        val moveHorizontal = dragAxis == 1
        val rawOffset = if (moveHorizontal) dragCurrentX - dragStartX else dragCurrentY - dragStartY
        val clamped = rawOffset.coerceIn(dragMaxNegPx, dragMaxPosPx)
        val moveSteps = (clamped / cellSize).roundToInt()

        if (moveSteps != 0) {
            val oldCol = block.col.toFloat()
            val oldRow = block.row.toFloat()

            // Safety validation + apply
            val direction = if (moveSteps > 0) 1 else -1
            var validSteps = 0
            for (s in 1..abs(moveSteps)) {
                if (g.canMoveInDir(dragBlockId, direction * s, moveHorizontal)) validSteps = direction * s
                else break
            }
            if (validSteps != 0) {
                g.moveBlockInDir(dragBlockId, validSteps, moveHorizontal)

                // Snap from visual position (clamped drag) to final grid position
                val snapOffset = if (validSteps == moveSteps) clamped / cellSize else validSteps.toFloat()
                snapAnimating = true
                snapBlockId = dragBlockId
                snapFromCol = oldCol + (if (moveHorizontal) snapOffset else 0f)
                snapFromRow = oldRow + (if (!moveHorizontal) snapOffset else 0f)
                snapStartTime = System.currentTimeMillis()
                snapPendingSolveCheck = true
            }
        }

        dragBlockId = -1
        dragAxis = 0; dragMaxNegPx = 0f; dragMaxPosPx = 0f
    }

    // ──────────────────────────────────────────────────────────────────────
    // Escape sequence
    // ──────────────────────────────────────────────────────────────────────

    private fun startEscapeSequence(g: PuzzleGrid) {
        val moves = g.getMoveCount()
        escapeStars = when {
            moves <= optimalMoves             -> 3
            moves <= (optimalMoves * 1.5f).toInt() -> 2
            else                              -> 1
        }
        escapeMoves = moves
        state = PuzzleState.ESCAPING
        escapePhase = 1
        escapeStartTime = System.currentTimeMillis()
        SoundManager.playCatRescue()
    }

    private fun spawnParticles() {
        particles.clear()
        val g = grid ?: return
        val cat = g.blocks.firstOrNull { it.isCat } ?: return
        val cx = boardLeft + (cat.col + 0.5f) * cellSize
        val cy = boardTop + (cat.row + 0.5f) * cellSize
        val rng = java.util.Random()

        repeat(30) {
            val angle = rng.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 2f + rng.nextFloat() * 6f
            particles.add(Particle(
                x = cx + (rng.nextFloat() - 0.5f) * cellSize,
                y = cy + (rng.nextFloat() - 0.5f) * cellSize,
                vx = kotlin.math.cos(angle.toDouble()).toFloat() * speed,
                vy = kotlin.math.sin(angle.toDouble()).toFloat() * speed - 2f,
                radius = 4f + rng.nextFloat() * 10f,
                color = Theme.PARTICLE_COLORS[rng.nextInt(Theme.PARTICLE_COLORS.size)],
                alpha = 1f
            ))
        }
        particleStartTime = System.currentTimeMillis()
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
        var stageClearData: Pair<Int, Int>? = null
        try {
            synchronized(lock) {
                pendingStageClear = null
                update()
                render(canvas)
                stageClearData = pendingStageClear
            }
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
        stageClearData?.let { (moves, stars) ->
            onStageClear?.invoke(moves, stars)
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()

        // Snap animation update
        if (snapAnimating) {
            val elapsed = now - snapStartTime
            if (elapsed >= SNAP_DURATION_MS) {
                snapAnimating = false
                SoundManager.playBlockMatch()
                if (snapPendingSolveCheck) {
                    snapPendingSolveCheck = false
                    val g = grid
                    if (g != null && g.isSolved()) {
                        startEscapeSequence(g)
                    }
                }
            }
        }

        // Escape sequence update
        if (state == PuzzleState.ESCAPING) {
            val elapsed = now - escapeStartTime
            when (escapePhase) {
                1 -> { // Wall open
                    if (elapsed >= WALL_OPEN_MS) {
                        escapePhase = 2
                        escapeStartTime = now
                    }
                }
                2 -> { // Cat slide out
                    if (elapsed >= CAT_SLIDE_MS) {
                        escapePhase = 3
                        escapeStartTime = now
                        spawnParticles()
                        SoundManager.playLevelClear()
                    }
                }
                3 -> { // Particles
                    // Update particles
                    for (p in particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.15f  // gravity
                        p.alpha = max(0f, p.alpha - 0.018f)
                    }
                    if (elapsed >= PARTICLE_MS) {
                        escapePhase = 0
                        particles.clear()
                        triggerVictory()
                    }
                }
            }
        }

        // Victory overlay fade
        if (state == PuzzleState.SOLVED) {
            victoryAlpha = min(1f, victoryAlpha + 0.04f)
            starAnimPhase += 0.05f
        }
    }

    /** Called inside synchronized(lock). Sets pending callback data instead of invoking directly. */
    private var pendingStageClear: Pair<Int, Int>? = null

    private fun triggerVictory() {
        victoryStars = escapeStars
        state = PuzzleState.SOLVED
        pendingStageClear = Pair(escapeMoves, escapeStars)
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

        // Draw particles on top
        if (escapePhase == 3 || particles.isNotEmpty()) {
            drawParticles(canvas)
        }

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

        hudBgPaint.color = 0xFFFFF3E0.toInt()
        canvas.drawRect(0f, hudTop, w, hudTop + hudH, hudBgPaint)

        hudTextPaint.textSize = 18 * density
        hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Stage $stageNumber", 16 * density, hudTop + hudH * 0.65f, hudTextPaint)

        hudTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Moves: ${g.getMoveCount()}", w / 2f, hudTop + hudH * 0.65f, hudTextPaint)

        val btnSize   = 40 * density
        val btnMargin = 8 * density
        val btnLeft   = w - btnSize - btnMargin
        val btnTop2   = hudTop + (hudH - btnSize) / 2f
        pauseRect.set(btnLeft, btnTop2, btnLeft + btnSize, btnTop2 + btnSize)

        val pb = pauseBitmap
        if (pb != null && !pb.isRecycled) {
            canvas.drawBitmap(pb, null, pauseRect, null)
        } else {
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
        val arrowW = cellSize * 0.5f
        val arrowH = cellSize * 0.6f
        exitPaint.style = Paint.Style.FILL
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXIT_COLOR; alpha = 80
        }

        // Wall open animation: widen the gap
        val wallOpenProgress = if (escapePhase >= 1 && escapePhase <= 3) {
            val elapsed = if (escapePhase == 1) {
                (System.currentTimeMillis() - escapeStartTime).toFloat() / WALL_OPEN_MS
            } else 1f
            elapsed.coerceIn(0f, 1f)
        } else 0f
        val gapExtra = wallOpenProgress * cellSize * 0.5f

        when (g.exitDirection) {
            ExitDirection.RIGHT -> {
                val exitY = boardTop + g.exitRow * cellSize
                val cx = boardLeft + boardSize + arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                canvas.drawPath(Path().apply {
                    moveTo(cx, cy - arrowH / 2f); lineTo(cx + arrowW, cy); lineTo(cx, cy + arrowH / 2f); close()
                }, exitPaint)
                canvas.drawRect(boardLeft + boardSize - 4f, exitY - gapExtra, boardLeft + boardSize + 4f, exitY + cellSize + gapExtra, gapPaint)
            }
            ExitDirection.LEFT -> {
                val exitY = boardTop + g.exitRow * cellSize
                val cx = boardLeft - arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                canvas.drawPath(Path().apply {
                    moveTo(cx, cy - arrowH / 2f); lineTo(cx - arrowW, cy); lineTo(cx, cy + arrowH / 2f); close()
                }, exitPaint)
                canvas.drawRect(boardLeft - 4f, exitY - gapExtra, boardLeft + 4f, exitY + cellSize + gapExtra, gapPaint)
            }
            ExitDirection.TOP -> {
                val exitX = boardLeft + g.exitCol * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop - arrowW * 0.4f
                canvas.drawPath(Path().apply {
                    moveTo(cx - arrowH / 2f, cy); lineTo(cx, cy - arrowW); lineTo(cx + arrowH / 2f, cy); close()
                }, exitPaint)
                canvas.drawRect(exitX - gapExtra, boardTop - 4f, exitX + cellSize + gapExtra, boardTop + 4f, gapPaint)
            }
            ExitDirection.BOTTOM -> {
                val exitX = boardLeft + g.exitCol * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop + boardSize + arrowW * 0.4f
                canvas.drawPath(Path().apply {
                    moveTo(cx - arrowH / 2f, cy); lineTo(cx, cy + arrowW); lineTo(cx + arrowH / 2f, cy); close()
                }, exitPaint)
                canvas.drawRect(exitX - gapExtra, boardTop + boardSize - 4f, exitX + cellSize + gapExtra, boardTop + boardSize + 4f, gapPaint)
            }
        }
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private fun drawBlocks(canvas: Canvas, g: PuzzleGrid) {
        val padding = cellSize * 0.07f
        val cr      = min(cellSize * 0.22f, 24f)

        for (block in g.blocks) {
            val isDragging = (block.id == dragBlockId)
            val isSnapping = (block.id == snapBlockId && snapAnimating)

            // Cat slide-out: hide cat during slide phase
            if (block.isCat && escapePhase == 2) {
                val elapsed = (System.currentTimeMillis() - escapeStartTime).toFloat() / CAT_SLIDE_MS
                val t = elapsed.coerceIn(0f, 1f)
                val easeT = t * t  // ease-in (accelerating)
                drawCatSlideOut(canvas, g, block, padding, cr, easeT)
                continue
            }
            if (block.isCat && escapePhase >= 3) continue  // cat already gone

            var left = boardLeft  + block.col * cellSize + padding
            var top  = boardTop   + block.row * cellSize + padding
            val right: Float
            val bottom: Float

            if (block.isHorizontal) {
                right  = boardLeft + (block.col + block.length) * cellSize - padding
                bottom = boardTop  + (block.row + 1) * cellSize - padding
            } else {
                right  = boardLeft + (block.col + 1) * cellSize - padding
                bottom = boardTop  + (block.row + block.length) * cellSize - padding
            }

            // Snap animation: interpolate from old position to current
            if (isSnapping) {
                val elapsed = (System.currentTimeMillis() - snapStartTime).toFloat() / SNAP_DURATION_MS
                val t = elapsed.coerceIn(0f, 1f)
                val easeT = 1f - (1f - t) * (1f - t)  // ease-out quadratic

                val curCol = snapFromCol + (block.col - snapFromCol) * easeT
                val curRow = snapFromRow + (block.row - snapFromRow) * easeT

                left = boardLeft + curCol * cellSize + padding
                top  = boardTop  + curRow * cellSize + padding
            } else if (isDragging && dragAxis != 0) {
                val rawOffset = if (dragAxis == 1) dragCurrentX - dragStartX else dragCurrentY - dragStartY
                val clamped = rawOffset.coerceIn(dragMaxNegPx, dragMaxPosPx)
                if (dragAxis == 1) left += clamped else top += clamped
            }

            val widthCells  = if (block.isHorizontal || block.length == 1) block.length else 1
            val heightCells = if (!block.isHorizontal || block.length == 1) block.length else 1
            val visualRight  = if (isDragging || isSnapping) left + (widthCells * cellSize - 2 * padding) else right
            val visualBottom = if (isDragging || isSnapping) top  + (heightCells * cellSize - 2 * padding) else bottom

            val color = if (block.isCat) CAT_COLOR
                        else BLOCK_COLORS[(block.id - 1) % BLOCK_COLORS.size]

            // Shadow
            if (isDragging || isSnapping) {
                blockShadow.alpha = 80
                val shadowRect = RectF(left + 6f, top + 6f, visualRight + 6f, visualBottom + 6f)
                canvas.drawRoundRect(shadowRect, cr, cr, blockShadow)
            }

            // Block body
            blockPaint.color = color
            val blockRect = RectF(left, top, visualRight, visualBottom)
            canvas.drawRoundRect(blockRect, cr, cr, blockPaint)

            // Highlight gloss
            glossPaint.shader = LinearGradient(
                left, top, left, top + (visualBottom - top) * 0.4f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(blockRect, cr, cr, glossPaint)

            // Cat bitmap or label
            if (block.isCat) {
                val bmp = catBitmap
                if (bmp != null && !bmp.isRecycled) {
                    val imgSize = min(visualRight - left, visualBottom - top) * 0.7f
                    val cx = (left + visualRight) / 2f
                    val cy = (top + visualBottom) / 2f
                    val imgRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
                    canvas.drawBitmap(bmp, null, imgRect, null)
                } else {
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
    }

    private fun drawCatSlideOut(canvas: Canvas, g: PuzzleGrid, block: com.meowrescue.game.puzzle.PuzzleBlock,
                                 padding: Float, cr: Float, progress: Float) {
        var left = boardLeft + block.col * cellSize + padding
        var top  = boardTop  + block.row * cellSize + padding
        val right: Float
        val bottom: Float

        if (block.isHorizontal) {
            right  = boardLeft + (block.col + block.length) * cellSize - padding
            bottom = boardTop  + (block.row + 1) * cellSize - padding
        } else {
            right  = boardLeft + (block.col + 1) * cellSize - padding
            bottom = boardTop  + (block.row + block.length) * cellSize - padding
        }

        // Slide offset based on exit direction
        val slideDistance = cellSize * 3f * progress
        when (g.exitDirection) {
            ExitDirection.RIGHT  -> left += slideDistance
            ExitDirection.LEFT   -> left -= slideDistance
            ExitDirection.BOTTOM -> top  += slideDistance
            ExitDirection.TOP    -> top  -= slideDistance
        }
        val slideRight  = left + (right - (boardLeft + block.col * cellSize + padding))
        val slideBottom = top + (bottom - (boardTop + block.row * cellSize + padding))

        blockPaint.color = CAT_COLOR
        val blockRect = RectF(left, top, slideRight, slideBottom)
        canvas.drawRoundRect(blockRect, cr, cr, blockPaint)

        // Cat bitmap on sliding block
        val bmp = catBitmap
        if (bmp != null && !bmp.isRecycled) {
            val imgSize = min(slideRight - left, slideBottom - top) * 0.7f
            val cx = (left + slideRight) / 2f
            val cy = (top + slideBottom) / 2f
            val imgRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, imgRect, null)
        } else {
            textPaint.textSize = min(cellSize * 0.45f, 20f)
            canvas.drawText("CAT", (left + slideRight) / 2f,
                (top + slideBottom) / 2f + textPaint.textSize * 0.35f, textPaint)
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            if (p.alpha <= 0f) continue
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private fun drawToolbar(canvas: Canvas) {
        val density    = resources.displayMetrics.density
        val arrowExtra = if (grid?.exitDirection == ExitDirection.BOTTOM) 48 * density else 0f
        val toolbarTop = boardTop + boardSize + arrowExtra + cellSize * 0.3f
        val btnH       = 48 * density
        val btnW       = (width * 0.35f)
        val margin     = (width - btnW * 2) / 3f

        undoRect.set(margin, toolbarTop, margin + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(undoRect, 12 * density, 12 * density, buttonPaint)
        buttonTextPaint.textSize = 16 * density
        canvas.drawText(
            "\u21A9 Undo",
            undoRect.centerX(), undoRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

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

        if (victoryAlpha < 0.5f) return

        val density = resources.displayMetrics.density
        val cx      = width / 2f
        val cy      = height * 0.38f

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

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF7043.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = 28 * density
        }
        canvas.drawText("Stage Clear!", cx, panelT + 56 * density, titlePaint)

        val movePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4E342E.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = 16 * density
        }
        canvas.drawText("Moves: ${g.getMoveCount()}", cx, panelT + 84 * density, movePaint)

        val starSize = 36 * density
        val starGap  = 8 * density
        val totalW   = 3 * starSize + 2 * starGap
        var starX    = cx - totalW / 2f
        val starY    = panelT + 110 * density
        val pulse    = 1f + 0.08f * sin(starAnimPhase.toDouble()).toFloat()

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
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt()
                }
                canvas.drawCircle(rect.centerX(), rect.centerY(), starSize / 2f * scale, starPaint)
            }
            starX += starSize + starGap
        }

        // "Next Stage" button
        val btnW = panelW * 0.6f
        val btnH2 = 48 * density
        val btnL = cx - btnW / 2f
        val btnT = starY + starSize + 24 * density
        nextStageRect.set(btnL, btnT, btnL + btnW, btnT + btnH2)

        buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(nextStageRect, 12 * density, 12 * density, buttonPaint)
        buttonTextPaint.textSize = 18 * density
        canvas.drawText(
            "Next Stage \u25B6",
            nextStageRect.centerX(),
            nextStageRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )
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
        val arrowArea  = 48 * density

        val arrowL = if (g.exitDirection == ExitDirection.LEFT) arrowArea else 0f
        val arrowR = if (g.exitDirection == ExitDirection.RIGHT) arrowArea else 0f
        val arrowT = if (g.exitDirection == ExitDirection.TOP) arrowArea else 0f
        val arrowB = if (g.exitDirection == ExitDirection.BOTTOM) arrowArea else 0f

        val availableW = w - arrowL - arrowR
        val availableH = h - hudH - toolbarH - arrowT - arrowB - (24 * density)

        val maxBoard = min(availableW, availableH)
        val targetH  = (h * 0.65f).coerceIn(availableH * 0.55f, availableH)
        boardSize    = min(maxBoard, targetH)

        cellSize  = boardSize / max(g.rows, g.cols)
        boardLeft = arrowL + (availableW - boardSize) / 2f
        boardTop  = hudH + arrowT + (availableH - boardSize) / 2f
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
        } catch (_: Exception) { }

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
        } catch (_: Exception) { }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }
}
