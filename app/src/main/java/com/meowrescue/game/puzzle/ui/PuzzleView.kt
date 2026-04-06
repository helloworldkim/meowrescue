package com.meowrescue.game.puzzle.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import com.meowrescue.game.R
import com.meowrescue.game.puzzle.engine.PuzzleGrid
import com.meowrescue.game.puzzle.engine.PuzzleSolver
import com.meowrescue.game.puzzle.model.PuzzleState
import com.meowrescue.game.util.HapticManager
import com.meowrescue.game.util.SoundManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class PuzzleParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var radius: Float, var color: Int, var alpha: Float,
    var rotation: Float = 0f, var rotSpeed: Float = 0f,
    var shape: Int = 0  // 0=circle, 1=star, 2=confetti
)

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
        val KEY_COLOR       = 0xFFFFD700.toInt()
        val LOCK_COLOR      = 0xFFDAA520.toInt()
        val CHECKPOINT_COLOR = 0xFFFFD700.toInt()
        val GRID_BG        = 0xFFEFEBE9.toInt()
        val CELL_LINE      = 0xFFD7CCC8.toInt()
        val BG_COLOR       = 0xFFFFF8F0.toInt()
        val EXIT_COLOR     = 0xFF66BB6A.toInt()
        val WALL_COLOR     = 0xFF5D4037.toInt()
        val PORTAL_A_CLR   = 0xFF7C4DFF.toInt()
        val PORTAL_B_CLR   = 0xFF00BFA5.toInt()
        val LINK_COLOR     = 0xFFFF6F00.toInt()
        val EXIT2_COLOR    = 0xFF42A5F5.toInt()

        val PARTICLE_COLORS = intArrayOf(
            0xFFFF7043.toInt(),  // coral
            0xFFFFD600.toInt(),  // yellow
            0xFF66BB6A.toInt(),  // green
            0xFF4FC3F7.toInt(),  // sky blue
            0xFFCE93D8.toInt(),  // purple
            0xFFF48FB1.toInt(),  // pink
            0xFFFFB74D.toInt(),  // amber
            0xFF80DEEA.toInt(),  // cyan
            0xFFFFFFFF.toInt(),  // white
        )

        private const val TARGET_FPS = 60L
        private const val FRAME_MS   = 1000L / TARGET_FPS
        internal const val SNAP_DURATION_MS = 150L
        internal const val WALL_OPEN_MS = 200L
        internal const val CAT_SLIDE_MS = 300L
        private const val PARTICLE_MS  = 800L

        // ── Layout constants ──────────────────────────────────────────────
        internal const val HUD_HEIGHT_DP    = 56f
        private const val TOOLBAR_HEIGHT_DP = 80f
        internal const val ARROW_AREA_DP    = 48f
        private const val AUTO_SOLVE_DELAY_MS = 350L
    }

    // ── Callbacks ──────────────────────────────────────────────────────────
    var onStageClear: ((moves: Int, stars: Int) -> Unit)? = null
    var onNextStageClicked: (() -> Unit)? = null
    var onRetryClicked: (() -> Unit)? = null
    var onLevelSelectClicked: (() -> Unit)? = null
    var onPauseClicked: (() -> Unit)? = null
    var onHintClicked: (() -> Unit)? = null
    var onSolveClicked: (() -> Unit)? = null

    // ── Hint state ─────────────────────────────────────────────────────────
    internal var hintBlockId: Int = -1
    internal var hintDRow: Int = 0
    internal var hintDCol: Int = 0
    internal var hintStartTime: Long = 0L
    var hintCount: Int = 3

    /** Show a golden glow + direction arrow on the block indicated by the hint step. */
    fun showHint(step: PuzzleSolver.MoveStep) {
        synchronized(lock) {
            hintBlockId = step.blockId
            hintDRow = step.dRow
            hintDCol = step.dCol
            hintStartTime = System.currentTimeMillis()
        }
    }

    /** Clear the hint highlight. */
    fun clearHint() {
        synchronized(lock) { hintBlockId = -1 }
    }

    // ── Lock for thread-safe access between UI thread and render thread ────
    private val lock = Any()

    // ── State ──────────────────────────────────────────────────────────────
    internal var state = PuzzleState.PLAYING
    internal var grid: PuzzleGrid? = null
    private var initialGrid: PuzzleGrid? = null
    internal var stageNumber = 1
    internal var optimalMoves = 1
    internal var isEndless = false
    internal var endlessCount = 0

    // ── Checkpoint glow animation ────────────────────────────────────────
    internal var prevCheckpointReached = false
    internal var checkpointGlowStartTime = 0L

    // ── Tutorial overlay ─────────────────────────────────────────────────
    var tutorialStep = -1
    var tutorialAutoDismissAt = 0L
    var onTutorialDismissed: (() -> Unit)? = null
    private val tutorialTapRect = RectF()
    internal var tutorialPulse = 0f

    // ── Layout ─────────────────────────────────────────────────────────────
    internal var cellSize = 0f
    internal var boardLeft = 0f
    internal var boardTop  = 0f
    internal var boardSize = 0f

    // ── Victory animation ──────────────────────────────────────────────────
    internal var victoryAlpha  = 0f
    internal var victoryStars  = 0
    internal var starAnimPhase = 0f

    // ── Drag tracking ─────────────────────────────────────────────────────
    internal var dragBlockId  = -1
    internal var dragStartX   = 0f
    internal var dragStartY   = 0f
    internal var dragCurrentX = 0f
    internal var dragCurrentY = 0f

    // ── Drag constraint cache ────────────────────────────────────────────
    internal var dragAxis: Int = 0
    internal var dragMaxNegPx: Float = 0f
    internal var dragMaxPosPx: Float = 0f

    // ── Smooth drag visual interpolation ─────────────────────────────────
    internal var dragSmoothX = 0f
    internal var dragSmoothY = 0f
    private var lastFrameTime = 0L
    private var frameMs = 16.67f

    // ── Snap animation ────────────────────────────────────────────────────
    internal var snapAnimating = false
    internal var snapBlockId   = -1
    internal var snapFromCol   = 0f
    internal var snapFromRow   = 0f
    internal var snapStartTime = 0L
    internal var snapPendingSolveCheck = false

    // ── Cat escape animation ──────────────────────────────────────────────
    internal var escapePhase = 0
    internal var escapeStartTime = 0L
    private var escapeMoves = 0
    private var escapeStars = 0

    // ── Particles ─────────────────────────────────────────────────────────
    internal val particles = mutableListOf<PuzzleParticle>()
    private var particleStartTime = 0L
    internal var screenFlashAlpha = 0f

    // ── Auto-solve ───────────────────────────────────────────────────────
    internal var autoSolving = false
    private var autoSolveSteps: List<PuzzleSolver.MoveStep> = emptyList()
    private var autoSolveIndex = 0
    private var autoSolveNextTime = 0L

    // ── HUD button rects ────────────────────────────────────────────────
    internal val pauseRect      = RectF()
    internal val undoRect       = RectF()
    internal val resetRect      = RectF()
    internal val hintRect       = RectF()
    internal val solveRect      = RectF()
    internal val nextStageRect    = RectF()
    internal val retryRect        = RectF()
    internal val levelSelectRect  = RectF()

    // ── Resources ─────────────────────────────────────────────────────────
    internal var pauseBitmap: Bitmap? = null
    internal var starFullBitmap: Bitmap? = null
    internal var starEmptyBitmap: Bitmap? = null
    internal var catBitmap: Bitmap? = null

    // ── Helper classes ────────────────────────────────────────────────────
    internal val paints = PuzzlePaints(resources.displayMetrics.density)
    internal val renderer = PuzzleRenderer(this, paints)
    internal val inputHandler = PuzzleInputHandler(this)

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

    fun setGrid(grid: PuzzleGrid, stage: Int, optimalMoves: Int, endless: Boolean = false, endlessCount: Int = 0) {
        synchronized(lock) {
            this.grid         = grid
            this.initialGrid  = grid.clone()
            this.stageNumber  = stage
            this.optimalMoves = optimalMoves
            this.isEndless    = endless
            this.endlessCount = endlessCount
            resetAnimationState()
        }
        recalcLayout()
    }

    /** Returns a snapshot of the current live grid state for hint computation. */
    fun getCurrentGrid(): PuzzleGrid? = synchronized(lock) { grid }

    fun startAutoSolve(steps: List<PuzzleSolver.MoveStep>) {
        synchronized(lock) {
            autoSolving = true
            autoSolveSteps = steps
            autoSolveIndex = 0
            autoSolveNextTime = System.currentTimeMillis() + 300L
            hintBlockId = -1
        }
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
        } catch (e: Exception) { android.util.Log.w("PuzzleView", "Failed to load bitmap", e) }
    }

    fun undoMove() {
        synchronized(lock) {
            if (state != PuzzleState.PLAYING || snapAnimating) return
            val g = grid ?: return
            val blocksBefore = g.blocks.associateBy { it.id }
            val moved = g.undoLastMove()
            if (!moved) return
            val movedBlock = g.blocks.firstOrNull { b ->
                val before = blocksBefore[b.id]
                before != null && (before.col != b.col || before.row != b.row)
            }
            if (movedBlock != null) {
                val before = blocksBefore[movedBlock.id]!!
                snapAnimating = true
                snapBlockId   = movedBlock.id
                snapFromCol   = before.col.toFloat()
                snapFromRow   = before.row.toFloat()
                snapStartTime = System.currentTimeMillis()
                snapPendingSolveCheck = false
            }
        }
        SoundManager.playButtonTap()
    }

    fun resetPuzzle() {
        val init = initialGrid ?: return
        synchronized(lock) {
            grid = init.clone()
            resetAnimationState()
        }
        SoundManager.playButtonTap()
    }

    fun pause() {
        synchronized(lock) { if (state == PuzzleState.PLAYING) state = PuzzleState.PAUSED }
    }

    fun resume() {
        synchronized(lock) { if (state == PuzzleState.PAUSED) state = PuzzleState.PLAYING }
    }

    fun recycleBitmaps() {
        pauseBitmap?.recycle();   pauseBitmap   = null
        starFullBitmap?.recycle(); starFullBitmap = null
        starEmptyBitmap?.recycle(); starEmptyBitmap = null
        catBitmap?.recycle(); catBitmap = null
    }

    // ── Shared animation state reset ──────────────────────────────────────
    private fun resetAnimationState() {
        state = PuzzleState.PLAYING; victoryAlpha = 0f
        dragBlockId = -1; dragSmoothX = 0f; dragSmoothY = 0f
        snapAnimating = false; escapePhase = 0; particles.clear()
        prevCheckpointReached = false; checkpointGlowStartTime = 0L
        hintBlockId = -1; autoSolving = false
        autoSolveSteps = emptyList(); autoSolveIndex = 0
        autoSolveNextTime = 0L; screenFlashAlpha = 0f
    }

    // ── Pulse utility ─────────────────────────────────────────────────────
    internal fun pulse(periodMs: Double, amplitude: Float, offset: Float): Float =
        (sin(System.currentTimeMillis() / periodMs) * amplitude + offset).toFloat()

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
                MotionEvent.ACTION_DOWN -> inputHandler.handleDown(x, y)
                MotionEvent.ACTION_MOVE -> { inputHandler.handleMove(x, y); null }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { inputHandler.handleUp(x, y); null }
                else -> null
            }
        }
        callback?.invoke()
        return true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Escape sequence
    // ──────────────────────────────────────────────────────────────────────

    private fun startEscapeSequence(g: PuzzleGrid) {
        autoSolving = false
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
        val count = if (escapeStars == 3) 80 else 50

        repeat(count) {
            val angle = rng.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 3f + rng.nextFloat() * 11f
            val spread = if (rng.nextFloat() < 0.4f) cellSize * 1.2f else cellSize * 0.4f
            val shapeRoll = rng.nextFloat()
            val shape = when {
                shapeRoll < 0.25f -> 1  // star
                shapeRoll < 0.50f -> 2  // confetti
                else -> 0              // circle
            }
            particles.add(PuzzleParticle(
                x = cx + (rng.nextFloat() - 0.5f) * spread,
                y = cy + (rng.nextFloat() - 0.5f) * spread,
                vx = kotlin.math.cos(angle.toDouble()).toFloat() * speed,
                vy = kotlin.math.sin(angle.toDouble()).toFloat() * speed - 3f,
                radius = 3f + rng.nextFloat() * 14f,
                color = PARTICLE_COLORS[rng.nextInt(PARTICLE_COLORS.size)],
                alpha = 1f,
                rotation = rng.nextFloat() * 360f,
                rotSpeed = (rng.nextFloat() - 0.5f) * 12f,
                shape = shape
            ))
        }
        particleStartTime = System.currentTimeMillis()
        screenFlashAlpha = if (escapeStars == 3) 0.7f else 0.4f
    }

    // ──────────────────────────────────────────────────────────────────────
    // Render loop
    // ──────────────────────────────────────────────────────────────────────

    private fun startRenderThread() {
        running = true
        renderThread = Thread {
            while (running) {
                val start = System.currentTimeMillis()
                val prevLast = lastFrameTime
                if (prevLast > 0L) frameMs = (start - prevLast).coerceIn(1L, 100L).toFloat()
                lastFrameTime = start
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
        var tutorialDismissed = false
        try {
            synchronized(lock) {
                pendingStageClear = null
                pendingTutorialDismiss = false
                update()
                renderer.render(canvas)
                stageClearData = pendingStageClear
                tutorialDismissed = pendingTutorialDismiss
            }
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
        stageClearData?.let { (moves, stars) ->
            onStageClear?.invoke(moves, stars)
        }
        if (tutorialDismissed) {
            onTutorialDismissed?.invoke()
        }
    }

    // ── Update sub-methods ────────────────────────────────────────────────

    private fun updateDragInterpolation() {
        if (dragBlockId >= 0 && dragAxis != 0) {
            val rawOffset = if (dragAxis == 1) dragCurrentX - dragStartX else dragCurrentY - dragStartY
            val clamped = rawOffset.coerceIn(dragMaxNegPx, dragMaxPosPx)
            val targetX = if (dragAxis == 1) clamped else 0f
            val targetY = if (dragAxis == 2) clamped else 0f
            val dt = frameMs.coerceIn(1f, 32f)
            val alpha = 1f - Math.pow((1.0 - 0.40), (dt / 16.67)).toFloat()
            dragSmoothX += (targetX - dragSmoothX) * alpha
            dragSmoothY += (targetY - dragSmoothY) * alpha
        }
    }

    private fun updateSnapAnimation() {
        if (snapAnimating) {
            val now = System.currentTimeMillis()
            val elapsed = now - snapStartTime
            if (elapsed >= SNAP_DURATION_MS) {
                snapAnimating = false
                SoundManager.playBlockMatch()
                HapticManager.vibrateBlockMove()
                if (snapPendingSolveCheck) {
                    snapPendingSolveCheck = false
                    val g = grid
                    if (g != null && g.isSolved()) {
                        startEscapeSequence(g)
                    }
                }
            }
        }
    }

    private fun updateEscapeSequence() {
        if (state == PuzzleState.ESCAPING) {
            val now = System.currentTimeMillis()
            val elapsed = now - escapeStartTime
            when (escapePhase) {
                1 -> {
                    if (elapsed >= WALL_OPEN_MS) {
                        escapePhase = 2
                        escapeStartTime = now
                    }
                }
                2 -> {
                    if (elapsed >= CAT_SLIDE_MS) {
                        escapePhase = 3
                        escapeStartTime = now
                        spawnParticles()
                        SoundManager.playLevelClear()
                        HapticManager.vibrateStageClear()
                    }
                }
                3 -> {
                    for (p in particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.15f
                        p.alpha = max(0f, p.alpha - 0.015f)
                        p.rotation += p.rotSpeed
                    }
                    if (elapsed >= PARTICLE_MS) {
                        escapePhase = 0
                        particles.clear()
                        triggerVictory()
                    }
                }
            }
        }
    }

    private fun updateVictoryFade() {
        if (state == PuzzleState.SOLVED) {
            victoryAlpha = min(1f, victoryAlpha + 0.04f)
            starAnimPhase += 0.05f
        }
    }

    private fun updateTutorial() {
        if (tutorialStep >= 0) {
            val now = System.currentTimeMillis()
            tutorialPulse += 0.07f
            if (tutorialAutoDismissAt > 0 && now >= tutorialAutoDismissAt) {
                tutorialStep = -1
                tutorialAutoDismissAt = 0
                pendingTutorialDismiss = true
            }
        }
    }

    private fun updateScreenFlash() {
        if (screenFlashAlpha > 0f) {
            screenFlashAlpha = max(0f, screenFlashAlpha - 0.04f)
        }
    }

    private fun updateAutoSolve() {
        if (autoSolving && !snapAnimating && state == PuzzleState.PLAYING) {
            val now = System.currentTimeMillis()
            if (now >= autoSolveNextTime && autoSolveIndex < autoSolveSteps.size) {
                val step = autoSolveSteps[autoSolveIndex]
                val g = grid
                if (g != null) {
                    val block = g.blocks.firstOrNull { it.id == step.blockId }
                    if (block != null) {
                        val oldRow = block.row
                        val oldCol = block.col
                        val horizontal = step.dCol != 0
                        val moveSteps = if (horizontal) step.dCol else step.dRow
                        val moved = g.moveBlockInDir(block.id, moveSteps, horizontal)
                        if (!moved) {
                            autoSolving = false
                            return
                        }
                        val newBlock = g.blocks.firstOrNull { it.id == step.blockId }
                        if (newBlock != null && (newBlock.row != oldRow || newBlock.col != oldCol)) {
                            snapAnimating = true
                            snapBlockId = step.blockId
                            snapFromCol = oldCol.toFloat()
                            snapFromRow = oldRow.toFloat()
                            snapStartTime = now
                            snapPendingSolveCheck = true
                        }
                    }
                }
                autoSolveIndex++
                if (autoSolveIndex >= autoSolveSteps.size) {
                    autoSolving = false
                } else {
                    autoSolveNextTime = now + AUTO_SOLVE_DELAY_MS
                }
            }
        }
    }

    private fun update() {
        updateDragInterpolation()
        updateSnapAnimation()
        updateEscapeSequence()
        updateVictoryFade()
        updateTutorial()
        updateScreenFlash()
        updateAutoSolve()
    }

    internal var pendingTutorialDismiss = false

    /** Called inside synchronized(lock). Sets pending callback data instead of invoking directly. */
    internal var pendingStageClear: Pair<Int, Int>? = null

    private fun triggerVictory() {
        victoryStars = escapeStars
        state = PuzzleState.SOLVED
        pendingStageClear = Pair(escapeMoves, escapeStars)
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
        val hudH       = HUD_HEIGHT_DP * density
        val toolbarH   = TOOLBAR_HEIGHT_DP * density
        val arrowArea  = ARROW_AREA_DP * density

        val availableW = w - arrowArea * 2
        val availableH = h - hudH - toolbarH - arrowArea * 2 - (24 * density)

        val maxBoard = min(availableW, availableH)
        val targetH  = (h * 0.65f).coerceIn(availableH * 0.55f, availableH)
        boardSize    = min(maxBoard, targetH)

        cellSize  = boardSize / max(g.rows, g.cols)
        boardLeft = arrowArea + (availableW - boardSize) / 2f
        boardTop  = hudH + arrowArea + (availableH - boardSize) / 2f

        paints.updateLayout(cellSize)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val bT = boardTop.toInt()
            val bB = (boardTop + boardSize).toInt()
            systemGestureExclusionRects = listOf(
                android.graphics.Rect(0, bT, w.toInt(), bB)
            )
        }
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
        } catch (e: Exception) { android.util.Log.w("PuzzleView", "Failed to load bitmap", e) }

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
        } catch (e: Exception) { android.util.Log.w("PuzzleView", "Failed to load bitmap", e) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }
}
