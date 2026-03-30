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
import com.meowrescue.game.puzzle.PuzzleGenerator
import com.meowrescue.game.puzzle.PuzzleGrid
import com.meowrescue.game.util.HapticManager
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
        private const val SNAP_DURATION_MS = 150L
        private const val WALL_OPEN_MS = 200L
        private const val CAT_SLIDE_MS = 300L
        private const val PARTICLE_MS  = 800L
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
    private var hintBlockId: Int = -1
    private var hintDRow: Int = 0      // direction: positive = down/right
    private var hintDCol: Int = 0
    private var hintStartTime: Long = 0L
    var hintCount: Int = 3             // remaining hints this stage (set by Activity)

    /** Show a golden glow + direction arrow on the block indicated by the hint step. */
    fun showHint(step: PuzzleGenerator.MoveStep) {
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
    private var state = PuzzleState.PLAYING
    private var grid: PuzzleGrid? = null
    private var initialGrid: PuzzleGrid? = null
    private var stageNumber = 1
    private var optimalMoves = 1
    private var isEndless = false
    private var endlessCount = 0

    // ── Checkpoint glow animation ────────────────────────────────────────
    private var prevCheckpointReached = false
    private var checkpointGlowStartTime = 0L

    // ── Tutorial overlay ─────────────────────────────────────────────────
    // -1 = inactive; 0/1/2 = stage-1 tap-to-advance steps; 10 = stage-2 tip; 20 = stage-3 tip
    var tutorialStep = -1
    var tutorialAutoDismissAt = 0L  // epoch ms; 0 = tap-to-dismiss
    var onTutorialDismissed: (() -> Unit)? = null
    private val tutorialTapRect = RectF()
    private var tutorialPulse = 0f  // 0..2π for pulsing animation

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

    // ── Smooth drag visual interpolation ─────────────────────────────────
    private var dragSmoothX = 0f           // smoothed visual offset (pixels)
    private var dragSmoothY = 0f

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
        var radius: Float, var color: Int, var alpha: Float,
        var rotation: Float = 0f, var rotSpeed: Float = 0f,
        var shape: Int = 0  // 0=circle, 1=star, 2=confetti
    )
    private val particles = mutableListOf<Particle>()
    private var particleStartTime = 0L
    private var screenFlashAlpha = 0f

    // ── Auto-solve ───────────────────────────────────────────────────────
    private var autoSolving = false
    private var autoSolveSteps: List<PuzzleGenerator.MoveStep> = emptyList()
    private var autoSolveIndex = 0
    private var autoSolveNextTime = 0L

    // ── HUD button rects ────────────────────────────────────────────────
    private val pauseRect      = RectF()
    private val undoRect       = RectF()
    private val resetRect      = RectF()
    private val hintRect       = RectF()
    private val solveRect      = RectF()
    private val nextStageRect    = RectF()
    private val retryRect        = RectF()
    private val levelSelectRect  = RectF()

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
    private val flashPaint    = Paint()
    private val tutBgPaint    = Paint()
    private val tutPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tutTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val tutBodyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); textAlign = Paint.Align.CENTER
    }
    private val tutHintPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9E9E9E.toInt(); textAlign = Paint.Align.CENTER
    }

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
            this.state        = PuzzleState.PLAYING
            this.victoryAlpha = 0f
            this.dragBlockId  = -1
            this.dragSmoothX  = 0f; this.dragSmoothY = 0f
            this.snapAnimating = false
            this.escapePhase  = 0
            this.particles.clear()
            this.prevCheckpointReached = false
            this.checkpointGlowStartTime = 0L
            this.hintBlockId  = -1
            this.autoSolving  = false
            this.autoSolveSteps = emptyList()
            this.autoSolveIndex = 0
            this.autoSolveNextTime = 0L
            this.screenFlashAlpha = 0f
        }
        recalcLayout()
    }

    /** Returns a snapshot of the current live grid state for hint computation. */
    fun getCurrentGrid(): PuzzleGrid? = synchronized(lock) { grid }

    fun startAutoSolve(steps: List<PuzzleGenerator.MoveStep>) {
        synchronized(lock) {
            autoSolving = true
            autoSolveSteps = steps
            autoSolveIndex = 0
            autoSolveNextTime = System.currentTimeMillis() + 300L
            hintBlockId = -1  // clear hint inline to avoid nested lock
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
        } catch (_: Exception) { /* fallback to text */ }
    }

    fun undoMove() {
        synchronized(lock) {
            if (state != PuzzleState.PLAYING || snapAnimating) return
            val g = grid ?: return
            // Save current positions before undo so we can animate from old → new
            val blocksBefore = g.blocks.associateBy { it.id }
            val moved = g.undoLastMove()
            if (!moved) return
            // Find which block changed position and start snap animation
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
            state = PuzzleState.PLAYING
            victoryAlpha = 0f
            dragBlockId  = -1
            dragSmoothX  = 0f; dragSmoothY = 0f
            snapAnimating = false
            escapePhase  = 0
            particles.clear()
            prevCheckpointReached = false
            checkpointGlowStartTime = 0L
            hintBlockId = -1
            autoSolving = false
            autoSolveSteps = emptyList()
            autoSolveIndex = 0
            autoSolveNextTime = 0L
            screenFlashAlpha = 0f
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
        // Tutorial tap-to-advance (only for tap-to-dismiss steps, not auto-dismiss)
        if (tutorialStep >= 0 && tutorialAutoDismissAt == 0L) {
            tutorialStep++
            if (tutorialStep > 2) {
                // All stage-1 steps done
                tutorialStep = -1
                pendingTutorialDismiss = true
            }
            SoundManager.playButtonTap()
            return null
        }
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
            if (retryRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = onRetryClicked
                return { cb?.invoke() }
            }
            if (levelSelectRect.contains(x, y)) {
                SoundManager.playButtonTap()
                val cb = onLevelSelectClicked
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
        if (hintRect.contains(x, y)) {
            SoundManager.playButtonTap()
            val cb = onHintClicked
            return { cb?.invoke() }
        }
        if (solveRect.contains(x, y) && !autoSolving) {
            SoundManager.playButtonTap()
            val cb = onSolveClicked
            return { cb?.invoke() }
        }

        if (autoSolving) return null  // Block drag during auto-solve

        val g = grid ?: return null
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop)  / cellSize).toInt()
        if (col < 0 || col >= g.cols || row < 0 || row >= g.rows) return null
        val gridSnap = g.getGrid()
        val blockId = gridSnap[row][col]
        if (blockId == -1) return null

        val block = g.blocks.firstOrNull { it.id == blockId } ?: return null
        if (block.isWall) return null  // Walls cannot be dragged
        dragBlockId  = blockId
        dragStartX   = x
        dragStartY   = y
        dragCurrentX = x
        dragCurrentY = y
        dragSmoothX  = 0f
        dragSmoothY  = 0f

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
            val threshold = cellSize * 0.06f
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

                // Snap from smoothed visual position to final grid position
                snapAnimating = true
                snapBlockId = dragBlockId
                snapFromCol = oldCol + dragSmoothX / cellSize
                snapFromRow = oldRow + dragSmoothY / cellSize
                snapStartTime = System.currentTimeMillis()
                snapPendingSolveCheck = true
            }
        }

        dragBlockId = -1
        dragAxis = 0; dragMaxNegPx = 0f; dragMaxPosPx = 0f
        dragSmoothX = 0f; dragSmoothY = 0f
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
            particles.add(Particle(
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
                render(canvas)
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

    private fun update() {
        val now = System.currentTimeMillis()

        // Smooth drag interpolation (lerp toward target each frame)
        if (dragBlockId >= 0 && dragAxis != 0) {
            val rawOffset = if (dragAxis == 1) dragCurrentX - dragStartX else dragCurrentY - dragStartY
            val clamped = rawOffset.coerceIn(dragMaxNegPx, dragMaxPosPx)
            val targetX = if (dragAxis == 1) clamped else 0f
            val targetY = if (dragAxis == 2) clamped else 0f
            dragSmoothX += (targetX - dragSmoothX) * 0.40f
            dragSmoothY += (targetY - dragSmoothY) * 0.40f
        }

        // Snap animation update
        if (snapAnimating) {
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
                        HapticManager.vibrateStageClear()
                    }
                }
                3 -> { // Particles
                    for (p in particles) {
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.15f  // gravity
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

        // Victory overlay fade
        if (state == PuzzleState.SOLVED) {
            victoryAlpha = min(1f, victoryAlpha + 0.04f)
            starAnimPhase += 0.05f
        }

        // Tutorial pulse animation + auto-dismiss
        if (tutorialStep >= 0) {
            tutorialPulse += 0.07f
            if (tutorialAutoDismissAt > 0 && now >= tutorialAutoDismissAt) {
                tutorialStep = -1
                tutorialAutoDismissAt = 0
                pendingTutorialDismiss = true
            }
        }

        // Screen flash decay
        if (screenFlashAlpha > 0f) {
            screenFlashAlpha = max(0f, screenFlashAlpha - 0.04f)
        }

        // Auto-solve stepping
        if (autoSolving && !snapAnimating && state == PuzzleState.PLAYING) {
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
                    autoSolveNextTime = now + 350L
                }
            }
        }
    }

    private var pendingTutorialDismiss = false

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
        drawHintBanner(canvas, g)
        drawBoard(canvas, g)
        drawPortalCells(canvas, g)
        drawLockCell(canvas, g)
        drawCheckpointCell(canvas, g)
        drawExit(canvas, g)
        drawSecondExit(canvas, g)
        drawBlocks(canvas, g)
        drawLockOverlay(canvas, g)
        drawCheckpointOverlay(canvas, g)
        drawToolbar(canvas)

        // Screen flash effect
        if (screenFlashAlpha > 0f) {
            flashPaint.color = Color.WHITE
            flashPaint.alpha = (screenFlashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }

        // Draw particles on top
        if (escapePhase == 3 || particles.isNotEmpty()) {
            drawParticles(canvas)
        }

        if (state == PuzzleState.SOLVED && victoryAlpha > 0f) {
            drawVictoryOverlay(canvas, g)
        }

        if (tutorialStep >= 0) {
            drawTutorialOverlay(canvas)
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
        val stageLabel = if (isEndless) "Endless #$endlessCount" else "Stage $stageNumber"
        canvas.drawText(stageLabel, 16 * density, hudTop + hudH * 0.65f, hudTextPaint)

        // Move count + color based on star tracking
        val moves = g.getMoveCount()
        val star3Limit = optimalMoves
        val star2Limit = (optimalMoves * 1.5f).toInt()
        val moveColor = when {
            moves <= star3Limit -> 0xFF388E3C.toInt()  // green (on track for 3 stars)
            moves <= star2Limit -> 0xFFF57F17.toInt()  // amber (on track for 2 stars)
            else -> 0xFFD32F2F.toInt()                 // red (1 star)
        }

        hudTextPaint.textAlign = Paint.Align.CENTER
        hudTextPaint.color = moveColor
        canvas.drawText("Moves: $moves", w / 2f, hudTop + hudH * 0.45f, hudTextPaint)
        hudTextPaint.color = 0xFF4E342E.toInt()  // reset

        // Star thresholds
        val starInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * density
            textAlign = Paint.Align.CENTER
            color = 0xFF8D6E63.toInt()
        }
        canvas.drawText(
            "\u2605\u2605\u2605 \u2264$star3Limit   \u2605\u2605 \u2264$star2Limit   \u2605 $star2Limit+",
            w / 2f, hudTop + hudH * 0.82f, starInfoPaint
        )

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

    // ── Hint banner (between HUD and board) ──────────────────────────────

    private fun drawHintBanner(canvas: Canvas, g: PuzzleGrid) {
        val hints = mutableListOf<String>()
        if (g.hasKeyLock) {
            val keyAtLock = g.blocks.firstOrNull { it.isKey }?.let {
                it.row == g.lockRow && it.col == g.lockCol
            } ?: false
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

        val density = resources.displayMetrics.density
        val hudH = 56 * density
        val bannerTop = hudH + 2 * density
        val textSize = 13 * density
        val bannerH = textSize * hints.size + 12 * density

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF3E0.toInt()
            alpha = 220
        }
        canvas.drawRoundRect(
            RectF(8 * density, bannerTop, width - 8 * density, bannerTop + bannerH),
            8f, 8f, bgPaint
        )

        // Text
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            color = 0xFF5D4037.toInt()
        }
        for ((i, hint) in hints.withIndex()) {
            canvas.drawText(
                hint, width / 2f,
                bannerTop + 8 * density + textSize * (i + 0.8f),
                tp
            )
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


    // ── Portal cells ──────────────────────────────────────────────────────

    private fun drawPortalCells(canvas: Canvas, g: PuzzleGrid) {
        if (g.portalA < 0 || g.portalB < 0) return
        drawSinglePortal(canvas, g, g.portalA, PORTAL_A_CLR, "A")
        drawSinglePortal(canvas, g, g.portalB, PORTAL_B_CLR, "B")
    }

    private fun drawSinglePortal(canvas: Canvas, g: PuzzleGrid, pos: Int, color: Int, label: String) {
        val r = pos / g.cols; val c = pos % g.cols
        val cx = boardLeft + c * cellSize + cellSize / 2f
        val cy = boardTop + r * cellSize + cellSize / 2f
        val time = System.currentTimeMillis()
        val pulse = (sin(time / 400.0) * 0.15 + 0.85).toFloat()
        val radius = cellSize * 0.35f * pulse
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 100 }
        canvas.drawCircle(cx, cy, radius, paint)
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 180 }
        canvas.drawCircle(cx, cy, radius * 0.6f, innerPaint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cellSize * 0.25f; textAlign = Paint.Align.CENTER; this.color = Color.WHITE
        }
        canvas.drawText(label, cx, cy + tp.textSize * 0.35f, tp)
    }

    // ── Lock cell ────────────────────────────────────────────────────────

    private fun drawLockCell(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return
        val left = boardLeft + g.lockCol * cellSize
        val top  = boardTop  + g.lockRow * cellSize
        val padding = cellSize * 0.1f

        val keyAtLock = g.blocks.firstOrNull { it.isKey }?.let {
            it.row == g.lockRow && it.col == g.lockCol
        } ?: false

        val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (keyAtLock) 0xFF66BB6A.toInt() else LOCK_COLOR
            alpha = if (keyAtLock) 180 else 120
        }
        val rect = RectF(left + padding, top + padding,
                         left + cellSize - padding, top + cellSize - padding)
        canvas.drawRoundRect(rect, 6f, 6f, lockPaint)

        // Lock icon text
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cellSize * 0.35f
            textAlign = Paint.Align.CENTER
            color = if (keyAtLock) 0xFF66BB6A.toInt() else LOCK_COLOR
            alpha = if (keyAtLock) 200 else 100
        }
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, left + cellSize / 2f, top + cellSize / 2f + iconPaint.textSize * 0.35f, iconPaint)
    }

    // ── Lock overlay (drawn ON TOP of blocks so always visible) ──────────

    private fun drawLockOverlay(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return

        val keyAtLock = g.blocks.firstOrNull { it.isKey }?.let {
            it.row == g.lockRow && it.col == g.lockCol
        } ?: false

        val left = boardLeft + g.lockCol * cellSize
        val top  = boardTop  + g.lockRow * cellSize
        val badgeSize = cellSize * 0.35f

        // Small badge in top-right corner of lock cell
        val badgeLeft = left + cellSize - badgeSize - cellSize * 0.05f
        val badgeTop  = top + cellSize * 0.05f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        // Badge background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (keyAtLock) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
            alpha = 200
        }
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, bgPaint)

        // Badge icon
        val badgeIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgeSize * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + badgeIcon.textSize * 0.3f, badgeIcon)

        // Pulsing border glow when lock not satisfied (to draw attention)
        if (!keyAtLock) {
            val time = System.currentTimeMillis()
            val pulse = (sin(time / 600.0) * 0.3 + 0.7).toFloat()
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = LOCK_COLOR
                alpha = (pulse * 140).toInt()
            }
            val pad = cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + cellSize - pad, top + cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, glowPaint)
        }
    }

    // ── Checkpoint overlay (drawn ON TOP of blocks so always visible) ───

    private fun drawCheckpointOverlay(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasCheckpoint) return

        val left = boardLeft + g.checkpointCol * cellSize
        val top  = boardTop  + g.checkpointRow * cellSize
        val reached = g.checkpointReached
        val badgeSize = cellSize * 0.35f

        // Small badge in top-left corner of checkpoint cell
        val badgeLeft = left + cellSize * 0.05f
        val badgeTop  = top + cellSize * 0.05f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        // Badge background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (reached) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
            alpha = 200
        }
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, bgPaint)

        // Badge icon
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgeSize * 0.7f
            textAlign = Paint.Align.CENTER
        }
        val icon = if (reached) "\u2713" else "\u2B50"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + iconPaint.textSize * 0.3f, iconPaint)

        // Pulsing border when not reached
        if (!reached) {
            val time = System.currentTimeMillis()
            val pulse = (sin(time / 500.0) * 0.3 + 0.7).toFloat()
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = CHECKPOINT_COLOR
                alpha = (pulse * 140).toInt()
            }
            val pad = cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + cellSize - pad, top + cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, glowPaint)
        }
    }

    // ── Checkpoint cell ──────────────────────────────────────────────────

    private fun drawCheckpointCell(canvas: Canvas, g: PuzzleGrid) {
        if (!g.hasCheckpoint) return
        val left = boardLeft + g.checkpointCol * cellSize
        val top  = boardTop  + g.checkpointRow * cellSize
        val cx = left + cellSize / 2f
        val cy = top + cellSize / 2f
        val reached = g.checkpointReached

        // Detect transition for glow
        if (reached && !prevCheckpointReached) {
            checkpointGlowStartTime = System.currentTimeMillis()
        }
        prevCheckpointReached = reached

        // Circular background highlight
        val bgRadius = cellSize * 0.38f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (reached) 0xFFFFD700.toInt() else 0xFF9E9E9E.toInt()
            alpha = if (reached) 60 else 30
        }
        canvas.drawCircle(cx, cy, bgRadius, circlePaint)

        // Dashed border ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (reached) 0xFFFFD700.toInt() else 0xFF757575.toInt()
            alpha = if (reached) 180 else 80
        }
        canvas.drawCircle(cx, cy, bgRadius, borderPaint)

        // Star emoji (bigger)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cellSize * 0.55f
            textAlign = Paint.Align.CENTER
            alpha = if (reached) 255 else 120
        }
        canvas.drawText("\u2B50", cx, cy + starPaint.textSize * 0.25f, starPaint)

        // Pulsing ring when NOT reached (draw attention)
        if (!reached) {
            val time = System.currentTimeMillis()
            val pulse = (sin(time / 500.0) * 0.4 + 0.6).toFloat()
            val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = CHECKPOINT_COLOR
                alpha = (pulse * 100).toInt()
            }
            canvas.drawCircle(cx, cy, bgRadius + 3f, pulsePaint)
        }

        // Pulse ring on reach transition
        if (reached && checkpointGlowStartTime > 0L) {
            val elapsed = System.currentTimeMillis() - checkpointGlowStartTime
            if (elapsed < 400L) {
                val progress = elapsed / 400f
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = CHECKPOINT_COLOR
                    alpha = ((1f - progress) * 180).toInt()
                }
                val radius = cellSize * 0.3f + cellSize * 0.3f * progress
                canvas.drawCircle(cx, cy, radius, ringPaint)
            }
        }
    }

    // ── Exit indicator ────────────────────────────────────────────────────

    private fun drawExit(canvas: Canvas, g: PuzzleGrid) {
        val arrowW = cellSize * 0.5f
        val arrowH = cellSize * 0.6f
        // Dim exit if key-lock not satisfied
        val keyAtLock = if (g.hasKeyLock) {
            val key = g.blocks.firstOrNull { it.isKey }
            key != null && key.row == g.lockRow && key.col == g.lockCol
        } else true
        exitPaint.style = Paint.Style.FILL
        exitPaint.alpha = if (keyAtLock) 255 else 80
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXIT_COLOR; alpha = if (keyAtLock) 80 else 40
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

    // ── Second exit (multi-cat) ──────────────────────────────────────────

    private fun drawSecondExit(canvas: Canvas, g: PuzzleGrid) {
        val dir2 = g.exitDirection2 ?: return
        val eRow2 = g.exitRow2
        val eCol2 = g.exitCol2
        val arrowW = cellSize * 0.5f
        val arrowH = cellSize * 0.6f
        val ePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXIT2_COLOR; style = Paint.Style.FILL
        }
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXIT2_COLOR; alpha = 80 }
        when (dir2) {
            ExitDirection.RIGHT -> {
                val exitY = boardTop + eRow2 * cellSize
                val cx = boardLeft + boardSize + arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                canvas.drawPath(Path().apply {
                    moveTo(cx, cy - arrowH / 2f); lineTo(cx + arrowW, cy); lineTo(cx, cy + arrowH / 2f); close()
                }, ePaint)
                canvas.drawRect(boardLeft + boardSize - 4f, exitY, boardLeft + boardSize + 4f, exitY + cellSize, gapPaint)
            }
            ExitDirection.LEFT -> {
                val exitY = boardTop + eRow2 * cellSize
                val cx = boardLeft - arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                canvas.drawPath(Path().apply {
                    moveTo(cx, cy - arrowH / 2f); lineTo(cx - arrowW, cy); lineTo(cx, cy + arrowH / 2f); close()
                }, ePaint)
                canvas.drawRect(boardLeft - 4f, exitY, boardLeft + 4f, exitY + cellSize, gapPaint)
            }
            ExitDirection.TOP -> {
                val exitX = boardLeft + eCol2 * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop - arrowW * 0.4f
                canvas.drawPath(Path().apply {
                    moveTo(cx - arrowH / 2f, cy); lineTo(cx, cy - arrowW); lineTo(cx + arrowH / 2f, cy); close()
                }, ePaint)
                canvas.drawRect(exitX, boardTop - 4f, exitX + cellSize, boardTop + 4f, gapPaint)
            }
            ExitDirection.BOTTOM -> {
                val exitX = boardLeft + eCol2 * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop + boardSize + arrowW * 0.4f
                canvas.drawPath(Path().apply {
                    moveTo(cx - arrowH / 2f, cy); lineTo(cx, cy + arrowW); lineTo(cx + arrowH / 2f, cy); close()
                }, ePaint)
                canvas.drawRect(exitX, boardTop + boardSize - 4f, exitX + cellSize, boardTop + boardSize + 4f, gapPaint)
            }
        }
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private fun drawBlocks(canvas: Canvas, g: PuzzleGrid) {
        val padding = cellSize * 0.07f
        val cr      = min(cellSize * 0.22f, 24f)

        // Find linked partner of dragged block for visual offset
        val draggedBlock = if (dragBlockId >= 0) g.blocks.firstOrNull { it.id == dragBlockId } else null
        val dragPartnerId = if (draggedBlock != null && draggedBlock.linkId >= 0) {
            g.blocks.firstOrNull { it.linkId == draggedBlock.linkId && it.id != dragBlockId }?.id ?: -1
        } else -1

        for (block in g.blocks) {
            val isDragging = (block.id == dragBlockId || block.id == dragPartnerId)
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
                // Use smoothed visual offset for fluid motion
                left += dragSmoothX
                top  += dragSmoothY
            }

            val widthCells  = if (block.isHorizontal || block.length == 1) block.length else 1
            val heightCells = if (!block.isHorizontal || block.length == 1) block.length else 1
            val visualRight  = if (isDragging || isSnapping) left + (widthCells * cellSize - 2 * padding) else right
            val visualBottom = if (isDragging || isSnapping) top  + (heightCells * cellSize - 2 * padding) else bottom

            // ── Hint glow: golden pulsing outline + direction arrow ──
            if (block.id == hintBlockId) {
                val elapsed = System.currentTimeMillis() - hintStartTime
                val glowAlpha = if (elapsed > 3000L) {
                    val fade = 1f - ((elapsed - 3000L) / 1500f).coerceIn(0f, 1f)
                    if (fade <= 0f) hintBlockId = -1
                    fade
                } else 1f
                if (glowAlpha > 0f) {
                    val glowPulse = (sin(System.currentTimeMillis() / 300.0) * 0.4 + 0.6).toFloat()
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = cellSize * 0.12f
                        color = android.graphics.Color.argb(
                            (glowAlpha * glowPulse * 255).toInt().coerceIn(0, 255),
                            0xFF, 0xD7, 0x00
                        )
                        maskFilter = BlurMaskFilter(cellSize * 0.18f, BlurMaskFilter.Blur.OUTER)
                    }
                    val glowRect = RectF(left - padding * 0.5f, top - padding * 0.5f,
                        visualRight + padding * 0.5f, visualBottom + padding * 0.5f)
                    canvas.drawRoundRect(glowRect, cr, cr, glowPaint)

                    // Direction arrow pointing toward the move
                    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(
                            (glowAlpha * glowPulse * 230).toInt().coerceIn(0, 255),
                            0xFF, 0xD7, 0x00
                        )
                        style = Paint.Style.FILL
                    }
                    val cx = (left + visualRight) / 2f
                    val cy = (top + visualBottom) / 2f
                    val arrowLen = cellSize * 0.35f
                    val arrowHead = cellSize * 0.18f
                    // Determine arrow direction from hint deltas
                    val dx = if (hintDCol > 0) 1f else if (hintDCol < 0) -1f else 0f
                    val dy = if (hintDRow > 0) 1f else if (hintDRow < 0) -1f else 0f
                    val tipX = cx + dx * arrowLen
                    val tipY = cy + dy * arrowLen
                    val tailX = cx - dx * arrowLen * 0.3f
                    val tailY = cy - dy * arrowLen * 0.3f
                    // Arrow shaft
                    val shaftPaint = Paint(arrowPaint).apply { strokeWidth = cellSize * 0.07f; style = Paint.Style.STROKE }
                    canvas.drawLine(tailX, tailY, tipX, tipY, shaftPaint)
                    // Arrowhead triangle
                    val perpX = -dy * arrowHead * 0.5f
                    val perpY = dx * arrowHead * 0.5f
                    val arrowPath = android.graphics.Path().apply {
                        moveTo(tipX, tipY)
                        lineTo(tipX - dx * arrowHead + perpX, tipY - dy * arrowHead + perpY)
                        lineTo(tipX - dx * arrowHead - perpX, tipY - dy * arrowHead - perpY)
                        close()
                    }
                    canvas.drawPath(arrowPath, arrowPaint)
                }
            }

            // ── Wall block: immovable obstacle ──
            if (block.isWall) {
                blockPaint.color = WALL_COLOR
                val blockRect = RectF(left, top, visualRight, visualBottom)
                canvas.drawRoundRect(blockRect, cr, cr, blockPaint)
                val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFBCAAA4.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE
                }
                canvas.drawLine(left + padding, top + padding, visualRight - padding, visualBottom - padding, xPaint)
                canvas.drawLine(visualRight - padding, top + padding, left + padding, visualBottom - padding, xPaint)
                continue
            }

            // ── Cat: image-only rendering (no block background) ──
            if (block.isCat) {
                val cx = (left + visualRight) / 2f
                val cy = (top + visualBottom) / 2f
                val cellW = visualRight - left
                val cellH = visualBottom - top
                val bmp = catBitmap

                // Scale up slightly when dragging for tactile feedback
                val scale = if (isDragging) 1.05f else 1.0f
                val imgSize = min(cellW, cellH) * 0.92f * scale

                // Subtle drop shadow under cat (only when dragging)
                if (isDragging) {
                    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF000000.toInt()
                        alpha = 25
                        maskFilter = BlurMaskFilter(imgSize * 0.15f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawCircle(cx + 3f, cy + 4f, imgSize * 0.35f, shadowPaint)
                }

                // Colored ring for multi-cat: 2nd cat gets blue ring matching EXIT2_COLOR
                val cats = g.blocks.filter { it.isCat }
                val isSecondCat = g.exitDirection2 != null && cats.size >= 2 && block.id != cats.first().id
                if (isSecondCat) {
                    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = EXIT2_COLOR; style = Paint.Style.STROKE
                        strokeWidth = 3f * resources.displayMetrics.density
                    }
                    canvas.drawCircle(cx, cy, imgSize * 0.48f, ringPaint)
                } else if (g.exitDirection2 != null && cats.size >= 2) {
                    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = EXIT_COLOR; style = Paint.Style.STROKE
                        strokeWidth = 3f * resources.displayMetrics.density
                    }
                    canvas.drawCircle(cx, cy, imgSize * 0.48f, ringPaint)
                }

                if (bmp != null && !bmp.isRecycled) {
                    val imgRect = RectF(
                        cx - imgSize / 2f, cy - imgSize / 2f,
                        cx + imgSize / 2f, cy + imgSize / 2f
                    )
                    canvas.drawBitmap(bmp, null, imgRect, null)
                } else {
                    // Fallback: draw a soft circle + text
                    val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = CAT_COLOR; alpha = 200
                    }
                    canvas.drawCircle(cx, cy, imgSize * 0.45f, fallbackPaint)
                    textPaint.textSize = imgSize * 0.35f
                    canvas.drawText("\uD83D\uDC31", cx, cy + textPaint.textSize * 0.3f, textPaint)
                }
            } else {
                // ── Normal block rendering ──
                val color = when {
                    block.isKey -> KEY_COLOR
                    block.linkId >= 0 -> LINK_COLOR
                    else -> BLOCK_COLORS[(block.id - 1) % BLOCK_COLORS.size]
                }

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

                // Key label
                if (block.isKey) {
                    textPaint.textSize = cellSize * 0.55f
                    canvas.drawText(
                        "\uD83D\uDD11",
                        (left + visualRight) / 2f,
                        (top + visualBottom) / 2f + textPaint.textSize * 0.3f,
                        textPaint
                    )
                }

                // Link chain icon
                if (block.linkId >= 0) {
                    val chainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    chainPaint.textSize = cellSize * 0.3f
                    chainPaint.textAlign = Paint.Align.CENTER
                    chainPaint.color = Color.WHITE
                    canvas.drawText("\uD83D\uDD17", (left + visualRight) / 2f,
                        (top + visualBottom) / 2f + chainPaint.textSize * 0.3f, chainPaint)
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

        // Slide offset based on exit direction (use correct exit for multi-cat)
        val cats = g.blocks.filter { it.isCat }
        val slideDir = if (g.exitDirection2 != null && cats.size >= 2 && block.id != cats.first().id) {
            g.exitDirection2
        } else g.exitDirection
        val slideDistance = cellSize * 3f * progress
        when (slideDir) {
            ExitDirection.RIGHT  -> left += slideDistance
            ExitDirection.LEFT   -> left -= slideDistance
            ExitDirection.BOTTOM -> top  += slideDistance
            ExitDirection.TOP    -> top  -= slideDistance
        }
        val slideRight  = left + (right - (boardLeft + block.col * cellSize + padding))
        val slideBottom = top + (bottom - (boardTop + block.row * cellSize + padding))

        // Cat image only (no block background)
        val cx = (left + slideRight) / 2f
        val cy = (top + slideBottom) / 2f
        val imgSize = min(slideRight - left, slideBottom - top) * 0.92f

        val bmp = catBitmap
        if (bmp != null && !bmp.isRecycled) {
            val imgRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, imgRect, null)
        } else {
            val fallbackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CAT_COLOR; alpha = 200 }
            canvas.drawCircle(cx, cy, imgSize * 0.45f, fallbackP)
            textPaint.textSize = imgSize * 0.35f
            canvas.drawText("\uD83D\uDC31", cx, cy + textPaint.textSize * 0.3f, textPaint)
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            if (p.alpha <= 0f) continue
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt()
            when (p.shape) {
                1 -> drawStarShape(canvas, p.x, p.y, p.radius, particlePaint)
                2 -> {
                    canvas.save()
                    canvas.rotate(p.rotation, p.x, p.y)
                    canvas.drawRect(
                        p.x - p.radius, p.y - p.radius * 0.4f,
                        p.x + p.radius, p.y + p.radius * 0.4f,
                        particlePaint
                    )
                    canvas.restore()
                }
                else -> canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
            }
        }
    }

    private val starPath = Path()
    private fun drawStarShape(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        starPath.reset()
        val inner = radius * 0.45f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else inner
            val angle = Math.PI / 5.0 * i - Math.PI / 2.0
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    // ── Tutorial overlay ──────────────────────────────────────────────────

    private fun drawTutorialOverlay(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()

        // Semi-transparent overlay
        val overlayAlpha = (0.55f + 0.08f * sin(tutorialPulse.toDouble())).toFloat()
        tutBgPaint.color = Color.BLACK
        tutBgPaint.alpha = (overlayAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, tutBgPaint)

        // Panel
        val panelW = w * 0.85f
        val panelH = 160 * density
        val panelL = (w - panelW) / 2f
        val panelT = h * 0.35f
        tutPanelPaint.color = 0xFFFFF8F0.toInt()
        tutPanelPaint.setShadowLayer(8f, 0f, 4f, 0x44000000)
        canvas.drawRoundRect(
            RectF(panelL, panelT, panelL + panelW, panelT + panelH),
            20 * density, 20 * density, tutPanelPaint
        )

        tutTitlePaint.textSize = 22 * density
        tutBodyPaint.textSize = 15 * density
        val cx = w / 2f

        when (tutorialStep) {
            0 -> {
                canvas.drawText("Welcome!", cx, panelT + 48 * density, tutTitlePaint)
                canvas.drawText("Swipe blocks to clear", cx, panelT + 80 * density, tutBodyPaint)
                canvas.drawText("a path for the cat!", cx, panelT + 102 * density, tutBodyPaint)
            }
            1 -> {
                canvas.drawText("Goal", cx, panelT + 48 * density, tutTitlePaint)
                canvas.drawText("Move the cat to the", cx, panelT + 80 * density, tutBodyPaint)
                canvas.drawText("green exit to rescue it!", cx, panelT + 102 * density, tutBodyPaint)
            }
            2 -> {
                canvas.drawText("Stars", cx, panelT + 48 * density, tutTitlePaint)
                canvas.drawText("Use fewer moves to", cx, panelT + 80 * density, tutBodyPaint)
                canvas.drawText("earn more stars!", cx, panelT + 102 * density, tutBodyPaint)
            }
            10 -> {
                canvas.drawText("New Mechanic!", cx, panelT + 48 * density, tutTitlePaint)
                canvas.drawText("Keys unlock blocked paths.", cx, panelT + 80 * density, tutBodyPaint)
                canvas.drawText("Move the key to the lock!", cx, panelT + 102 * density, tutBodyPaint)
            }
            20 -> {
                canvas.drawText("Checkpoints!", cx, panelT + 48 * density, tutTitlePaint)
                canvas.drawText("Pass through the star", cx, panelT + 80 * density, tutBodyPaint)
                canvas.drawText("before reaching the exit!", cx, panelT + 102 * density, tutBodyPaint)
            }
        }

        // Tap to continue prompt
        if (tutorialAutoDismissAt == 0L) {
            val pulse = (sin(tutorialPulse * 2.0) * 0.3 + 0.7).toFloat()
            tutHintPaint.textSize = 13 * density
            tutHintPaint.alpha = (pulse * 255).toInt()
            canvas.drawText("Tap to continue", cx, panelT + panelH - 18 * density, tutHintPaint)
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private fun drawToolbar(canvas: Canvas) {
        val density    = resources.displayMetrics.density
        val arrowExtra = if (grid?.exitDirection == ExitDirection.BOTTOM) 48 * density else 0f
        val toolbarTop = boardTop + boardSize + arrowExtra + cellSize * 0.8f
        val btnH       = 46 * density
        val btnW       = (width * 0.21f)
        val margin     = (width - btnW * 4) / 5f

        buttonTextPaint.textSize = 13 * density

        // Undo button
        undoRect.set(margin, toolbarTop, margin + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(undoRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "Undo",
            undoRect.centerX(), undoRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // Hint button (gold, pulsing)
        val hintLeft = margin * 2 + btnW
        hintRect.set(hintLeft, toolbarTop, hintLeft + btnW, toolbarTop + btnH)
        val hintPulse = (sin(System.currentTimeMillis() / 500.0) * 0.15 + 0.85).toFloat()
        val hintR = (0xFF * hintPulse).toInt().coerceIn(0, 255)
        val hintG = (0xC0 * hintPulse).toInt().coerceIn(0, 255)
        buttonPaint.color = android.graphics.Color.argb(255, hintR, hintG, 0)
        canvas.drawRoundRect(hintRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "Hint${if (hintCount > 0) " $hintCount" else ""}",
            hintRect.centerX(), hintRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // Solve button (purple, shows auto-solve state)
        val solveLeft = margin * 3 + btnW * 2
        solveRect.set(solveLeft, toolbarTop, solveLeft + btnW, toolbarTop + btnH)
        buttonPaint.color = if (autoSolving) 0xFF7E57C2.toInt() else 0xFF9575CD.toInt()
        canvas.drawRoundRect(solveRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            if (autoSolving) "..." else "Solve",
            solveRect.centerX(), solveRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // Reset button
        val resetLeft = margin * 4 + btnW * 3
        resetRect.set(resetLeft, toolbarTop, resetLeft + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(resetRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "Reset",
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
        val cy      = height * 0.36f

        val panelW  = width * 0.82f
        val panelH  = height * 0.48f
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
        val clearTitle = if (isEndless) "Endless #$endlessCount Clear!" else "Stage Clear!"
        canvas.drawText(clearTitle, cx, panelT + 52 * density, titlePaint)

        val movePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4E342E.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize  = 16 * density
        }
        canvas.drawText("Moves: ${g.getMoveCount()}", cx, panelT + 78 * density, movePaint)

        val starSize = 36 * density
        val starGap  = 8 * density
        val totalW   = 3 * starSize + 2 * starGap
        var starX    = cx - totalW / 2f
        val starY    = panelT + 98 * density
        // 3-star: bigger pulse amplitude
        val pulseAmp = if (victoryStars == 3) 0.18f else 0.08f
        val pulse    = 1f + pulseAmp * sin(starAnimPhase.toDouble()).toFloat()

        // 3-star golden shimmer behind stars
        if (victoryStars == 3) {
            val shimmerAlpha = ((sin(starAnimPhase.toDouble()) * 0.3 + 0.5) * 255).toInt().coerceIn(0, 255)
            val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD600.toInt()
                maskFilter = BlurMaskFilter(starSize * 1.2f, BlurMaskFilter.Blur.NORMAL)
            }
            shimmerPaint.alpha = shimmerAlpha
            canvas.drawCircle(cx, starY + starSize / 2f, totalW * 0.65f, shimmerPaint)
        }

        for (i in 1..3) {
            val earned  = i <= victoryStars
            // Staggered pop-in: each star appears with a delay based on index
            // starAnimPhase goes 0→∞; star i appears after phase > (i-1)*0.4
            val starDelay = (i - 1) * 0.4f
            val starPhase = (starAnimPhase - starDelay).coerceAtLeast(0f)
            // Pop scale: quickly scales from 0→1.2→1.0 then pulses
            val popScale = when {
                starPhase < 0.01f -> 0f
                starPhase < 0.3f  -> (starPhase / 0.3f) * 1.25f  // overshoot
                starPhase < 0.5f  -> 1.25f - ((starPhase - 0.3f) / 0.2f) * 0.25f  // settle
                else              -> if (earned) pulse else 1f
            }
            val scale   = if (earned) popScale else minOf(popScale, 1f)
            val scaledS = starSize * scale
            val offsetX = (scaledS - starSize) / 2f
            val offsetY = (scaledS - starSize) / 2f
            val rect    = RectF(
                starX - offsetX, starY - offsetY,
                starX + starSize + offsetX, starY + starSize + offsetY
            )
            if (scale > 0f) {
                val bmp = if (earned) starFullBitmap else starEmptyBitmap
                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, null, rect, null)
                } else {
                    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt()
                    }
                    canvas.drawCircle(rect.centerX(), rect.centerY(), starSize / 2f * scale, starPaint)
                }
            }
            starX += starSize + starGap
        }

        // "PERFECT!" text for 3-star clear (fades in after stars pop)
        if (victoryStars == 3 && starAnimPhase > 1.5f) {
            val perfectAlpha = ((starAnimPhase - 1.5f) / 0.5f).coerceIn(0f, 1f)
            val perfectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF7043.toInt()
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize  = 22 * density
            }
            perfectPaint.alpha = (perfectAlpha * 255).toInt()
            canvas.drawText("PERFECT!", cx, starY - 10 * density, perfectPaint)
        }

        // ── Buttons ──
        val btnW  = panelW * 0.7f
        val btnH2 = 44 * density
        val btnGap = 10 * density
        val btnL  = cx - btnW / 2f
        var btnY  = starY + starSize + 20 * density

        // "Next Stage" button (coral)
        nextStageRect.set(btnL, btnY, btnL + btnW, btnY + btnH2)
        buttonPaint.color = 0xFFFF7043.toInt()
        canvas.drawRoundRect(nextStageRect, 12 * density, 12 * density, buttonPaint)
        buttonTextPaint.textSize = 17 * density
        canvas.drawText(
            if (isEndless) "Next Puzzle  \u25B6" else "Next Stage  \u25B6",
            nextStageRect.centerX(),
            nextStageRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // "Retry" button (teal)
        btnY += btnH2 + btnGap
        retryRect.set(btnL, btnY, btnL + btnW, btnY + btnH2)
        buttonPaint.color = 0xFF26A69A.toInt()
        canvas.drawRoundRect(retryRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "\u21BB  Retry",
            retryRect.centerX(),
            retryRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        // "Level Select" / "Menu" button (gray-blue)
        btnY += btnH2 + btnGap
        levelSelectRect.set(btnL, btnY, btnL + btnW, btnY + btnH2)
        buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(levelSelectRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            if (isEndless) "\u2630  Menu" else "\u2630  Level Select",
            levelSelectRect.centerX(),
            levelSelectRect.centerY() + buttonTextPaint.textSize * 0.35f,
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

        // Always reserve arrow space on all sides so board stays centered regardless of exit direction
        val availableW = w - arrowArea * 2
        val availableH = h - hudH - toolbarH - arrowArea * 2 - (24 * density)

        val maxBoard = min(availableW, availableH)
        val targetH  = (h * 0.65f).coerceIn(availableH * 0.55f, availableH)
        boardSize    = min(maxBoard, targetH)

        cellSize  = boardSize / max(g.rows, g.cols)
        boardLeft = arrowArea + (availableW - boardSize) / 2f
        boardTop  = hudH + arrowArea + (availableH - boardSize) / 2f

        // Exclude board area from system back gesture on both edges
        // Must span full screen width so both left and right edge swipes are blocked
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
