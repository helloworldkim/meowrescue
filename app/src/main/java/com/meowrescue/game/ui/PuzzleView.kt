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

        // ── Layout constants ──────────────────────────────────────────────
        private const val HUD_HEIGHT_DP    = 56f
        private const val TOOLBAR_HEIGHT_DP = 80f
        private const val ARROW_AREA_DP    = 48f
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
    private var lastFrameTime = 0L         // timestamp of previous frame start (ms)
    private var frameMs = 16.67f           // actual frame delta time (ms)

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

    // ── Reusable temp objects (avoid per-frame allocation) ──
    private val tmpRect1 = RectF()
    private val tmpRect2 = RectF()
    private val tmpRect3 = RectF()
    private val tmpSrcRect = Rect()
    private val tmpMatrix = Matrix()
    private var cachedDensity = 0f

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
    private val tutPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(8f, 0f, 4f, 0x44000000) }
    private val tutTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val tutBodyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); textAlign = Paint.Align.CENTER
    }
    private val tutHintPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9E9E9E.toInt(); textAlign = Paint.Align.CENTER
    }

    // ── Promoted Paint fields ──────────────────────────────────────────────
    private val starInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8D6E63.toInt(); textAlign = Paint.Align.CENTER
    }
    private val bannerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF3E0.toInt()
    }
    private val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = 0xFF5D4037.toInt()
    }
    private val portalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val portalInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val portalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.WHITE
    }
    private val lockCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val lockIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val checkpointCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkpointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val checkpointStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val checkpointPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val checkpointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val exit2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXIT2_COLOR; style = Paint.Style.FILL
    }
    private val exit2GapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXIT2_COLOR; alpha = 80
    }
    private val hintGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val hintArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hintShaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val wallXPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBCAAA4.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private var catDragBlur: BlurMaskFilter? = null
    private var hintGlowBlur: BlurMaskFilter? = null
    private val catShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val catFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CAT_COLOR; alpha = 200
    }
    private val chainIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.WHITE
    }
    private val slideOutFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CAT_COLOR; alpha = 200
    }
    private val victoryPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8F0.toInt()
        setShadowLayer(12f, 0f, 4f, 0x44000000)
    }
    private val victoryTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val victoryMovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val victoryShimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
    }
    private val victoryStarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val victoryPerfectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val pauseFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E342E.toInt()
    }

    // ── Path cache ─────────────────────────────────────────────────────────
    private val starPath  = Path()
    private val arrowPath = Path()

    // ── Gloss gradient cache ───────────────────────────────────────────────
    private var glossGradient: LinearGradient? = null

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
        } catch (e: Exception) { android.util.Log.w("PuzzleView", "Failed to load bitmap", e) }
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
    private fun pulse(periodMs: Double, amplitude: Float, offset: Float): Float =
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
        val blockId = g.blockIdAt(row, col)
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
            val threshold = max(cellSize * 0.06f, 8f * resources.displayMetrics.density)
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

    // ── Update sub-methods ────────────────────────────────────────────────

    private fun updateDragInterpolation() {
        if (dragBlockId >= 0 && dragAxis != 0) {
            val rawOffset = if (dragAxis == 1) dragCurrentX - dragStartX else dragCurrentY - dragStartY
            val clamped = rawOffset.coerceIn(dragMaxNegPx, dragMaxPosPx)
            val targetX = if (dragAxis == 1) clamped else 0f
            val targetY = if (dragAxis == 2) clamped else 0f
            val dt = frameMs.coerceIn(1f, 32f)  // clamp to avoid extremes
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
        val wallOpenProgress = if (escapePhase >= 1 && escapePhase <= 3) {
            val elapsed = if (escapePhase == 1) {
                (System.currentTimeMillis() - escapeStartTime).toFloat() / WALL_OPEN_MS
            } else 1f
            elapsed.coerceIn(0f, 1f)
        } else 0f

        drawExitArrow(canvas, g, g.exitDirection, g.exitRow, g.exitCol, EXIT_COLOR, keyAtLock, wallOpenProgress)
        val dir2 = g.exitDirection2
        if (dir2 != null) {
            drawExitArrow(canvas, g, dir2, g.exitRow2, g.exitCol2, EXIT2_COLOR, keyAtLock)
        }

        drawBlocks(canvas, g)
        drawLockOverlay(canvas, g, keyAtLock)
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
        val hudH    = (HUD_HEIGHT_DP * density).toInt().toFloat()
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
        starInfoPaint.textSize = 11 * density
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
            val barW = btnSize * 0.22f
            val barH = btnSize * 0.55f
            val cx   = pauseRect.centerX()
            val cy   = pauseRect.centerY()
            canvas.drawRect(cx - barW * 1.5f, cy - barH / 2f, cx - barW * 0.5f, cy + barH / 2f, pauseFallbackPaint)
            canvas.drawRect(cx + barW * 0.5f, cy - barH / 2f, cx + barW * 1.5f, cy + barH / 2f, pauseFallbackPaint)
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

        val density = resources.displayMetrics.density
        val hudH = HUD_HEIGHT_DP * density
        val bannerTop = hudH + 2 * density
        val textSize = 13 * density
        val bannerH = textSize * hints.size + 12 * density

        // Background
        bannerBgPaint.alpha = 220
        canvas.drawRoundRect(
            RectF(8 * density, bannerTop, width - 8 * density, bannerTop + bannerH),
            8f, 8f, bannerBgPaint
        )

        // Text
        bannerTextPaint.textSize = textSize
        for ((i, hint) in hints.withIndex()) {
            canvas.drawText(
                hint, width / 2f,
                bannerTop + 8 * density + textSize * (i + 0.8f),
                bannerTextPaint
            )
        }
    }

    // ── Board ─────────────────────────────────────────────────────────────

    private fun drawBoard(canvas: Canvas, g: PuzzleGrid) {
        tmpRect1.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize)
        canvas.drawRoundRect(tmpRect1, 8f, 8f, gridBgPaint)

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
        val pulsed = pulse(400.0, 0.15f, 0.85f)
        val radius = cellSize * 0.35f * pulsed
        portalPaint.color = color; portalPaint.alpha = 100
        canvas.drawCircle(cx, cy, radius, portalPaint)
        portalInnerPaint.color = color; portalInnerPaint.alpha = 180
        canvas.drawCircle(cx, cy, radius * 0.6f, portalInnerPaint)
        portalLabelPaint.textSize = cellSize * 0.25f
        canvas.drawText(label, cx, cy + portalLabelPaint.textSize * 0.35f, portalLabelPaint)
    }

    // ── Lock cell ────────────────────────────────────────────────────────

    private fun drawLockCell(canvas: Canvas, g: PuzzleGrid, keyAtLock: Boolean) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return
        val left = boardLeft + g.lockCol * cellSize
        val top  = boardTop  + g.lockRow * cellSize
        val padding = cellSize * 0.1f

        lockCellPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else LOCK_COLOR
        lockCellPaint.alpha = if (keyAtLock) 180 else 120
        val rect = RectF(left + padding, top + padding,
                         left + cellSize - padding, top + cellSize - padding)
        canvas.drawRoundRect(rect, 6f, 6f, lockCellPaint)

        // Lock icon text
        lockIconPaint.textSize = cellSize * 0.35f
        lockIconPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else LOCK_COLOR
        lockIconPaint.alpha = if (keyAtLock) 200 else 100
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, left + cellSize / 2f, top + cellSize / 2f + lockIconPaint.textSize * 0.35f, lockIconPaint)
    }

    // ── Lock overlay (drawn ON TOP of blocks so always visible) ──────────

    private fun drawLockOverlay(canvas: Canvas, g: PuzzleGrid, keyAtLock: Boolean) {
        if (!g.hasKeyLock || g.lockRow < 0 || g.lockCol < 0) return

        val left = boardLeft + g.lockCol * cellSize
        val top  = boardTop  + g.lockRow * cellSize
        val badgeSize = cellSize * 0.35f

        // Small badge in top-right corner of lock cell
        val badgeLeft = left + cellSize - badgeSize - cellSize * 0.05f
        val badgeTop  = top + cellSize * 0.05f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        // Badge background
        badgeBgPaint.color = if (keyAtLock) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
        badgeBgPaint.alpha = 200
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, badgeBgPaint)

        // Badge icon
        badgeIconPaint.textSize = badgeSize * 0.7f
        val icon = if (keyAtLock) "\uD83D\uDD13" else "\uD83D\uDD12"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + badgeIconPaint.textSize * 0.3f, badgeIconPaint)

        // Pulsing border glow when lock not satisfied (to draw attention)
        if (!keyAtLock) {
            val pulsed = pulse(600.0, 0.3f, 0.7f)
            glowStrokePaint.color = LOCK_COLOR
            glowStrokePaint.alpha = (pulsed * 140).toInt()
            val pad = cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + cellSize - pad, top + cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, glowStrokePaint)
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
        badgeBgPaint.color = if (reached) 0xFF66BB6A.toInt() else 0xFF5D4037.toInt()
        badgeBgPaint.alpha = 200
        canvas.drawRoundRect(badgeRect, badgeSize * 0.3f, badgeSize * 0.3f, badgeBgPaint)

        // Badge icon
        badgeIconPaint.textSize = badgeSize * 0.7f
        val icon = if (reached) "\u2713" else "\u2B50"
        canvas.drawText(icon, badgeRect.centerX(), badgeRect.centerY() + badgeIconPaint.textSize * 0.3f, badgeIconPaint)

        // Pulsing border when not reached
        if (!reached) {
            val pulsed = pulse(500.0, 0.3f, 0.7f)
            glowStrokePaint.color = CHECKPOINT_COLOR
            glowStrokePaint.alpha = (pulsed * 140).toInt()
            val pad = cellSize * 0.06f
            val cellRect = RectF(left + pad, top + pad, left + cellSize - pad, top + cellSize - pad)
            canvas.drawRoundRect(cellRect, 6f, 6f, glowStrokePaint)
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
        checkpointCirclePaint.color = if (reached) 0xFFFFD700.toInt() else 0xFF9E9E9E.toInt()
        checkpointCirclePaint.alpha = if (reached) 60 else 30
        canvas.drawCircle(cx, cy, bgRadius, checkpointCirclePaint)

        // Dashed border ring
        checkpointBorderPaint.color = if (reached) 0xFFFFD700.toInt() else 0xFF757575.toInt()
        checkpointBorderPaint.alpha = if (reached) 180 else 80
        canvas.drawCircle(cx, cy, bgRadius, checkpointBorderPaint)

        // Star emoji (bigger)
        checkpointStarPaint.textSize = cellSize * 0.55f
        checkpointStarPaint.alpha = if (reached) 255 else 120
        canvas.drawText("\u2B50", cx, cy + checkpointStarPaint.textSize * 0.25f, checkpointStarPaint)

        // Pulsing ring when NOT reached (draw attention)
        if (!reached) {
            val pulsed = pulse(500.0, 0.4f, 0.6f)
            checkpointPulsePaint.color = CHECKPOINT_COLOR
            checkpointPulsePaint.alpha = (pulsed * 100).toInt()
            canvas.drawCircle(cx, cy, bgRadius + 3f, checkpointPulsePaint)
        }

        // Pulse ring on reach transition
        if (reached && checkpointGlowStartTime > 0L) {
            val elapsed = System.currentTimeMillis() - checkpointGlowStartTime
            if (elapsed < 400L) {
                val progress = elapsed / 400f
                checkpointRingPaint.color = CHECKPOINT_COLOR
                checkpointRingPaint.alpha = ((1f - progress) * 180).toInt()
                val radius = cellSize * 0.3f + cellSize * 0.3f * progress
                canvas.drawCircle(cx, cy, radius, checkpointRingPaint)
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
        val arrowW = cellSize * 0.5f
        val arrowH = cellSize * 0.6f
        val isPrimary = (color == EXIT_COLOR)

        if (isPrimary) {
            exitPaint.color = color
            exitPaint.style = Paint.Style.FILL
            exitPaint.alpha = if (keyAtLock) 255 else 80
            gapPaint.color = color
            gapPaint.alpha = if (keyAtLock) 80 else 40
        } else {
            exit2Paint.color = color
            exit2Paint.style = Paint.Style.FILL
            exit2GapPaint.color = color
            exit2GapPaint.alpha = 80
        }

        val arrowPaint = if (isPrimary) exitPaint else exit2Paint
        val gapUsePaint = if (isPrimary) gapPaint else exit2GapPaint
        val gapExtra = wallOpenProgress * cellSize * 0.5f

        when (dir) {
            ExitDirection.RIGHT -> {
                val exitY = boardTop + row * cellSize
                val cx = boardLeft + boardSize + arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                arrowPath.reset()
                arrowPath.moveTo(cx, cy - arrowH / 2f)
                arrowPath.lineTo(cx + arrowW, cy)
                arrowPath.lineTo(cx, cy + arrowH / 2f)
                arrowPath.close()
                canvas.drawPath(arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(boardLeft + boardSize - 4f, exitY - gapExtra, boardLeft + boardSize + 4f, exitY + cellSize + gapExtra, gapUsePaint)
                } else {
                    canvas.drawRect(boardLeft + boardSize - 4f, exitY, boardLeft + boardSize + 4f, exitY + cellSize, gapUsePaint)
                }
            }
            ExitDirection.LEFT -> {
                val exitY = boardTop + row * cellSize
                val cx = boardLeft - arrowW * 0.4f
                val cy = exitY + cellSize / 2f
                arrowPath.reset()
                arrowPath.moveTo(cx, cy - arrowH / 2f)
                arrowPath.lineTo(cx - arrowW, cy)
                arrowPath.lineTo(cx, cy + arrowH / 2f)
                arrowPath.close()
                canvas.drawPath(arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(boardLeft - 4f, exitY - gapExtra, boardLeft + 4f, exitY + cellSize + gapExtra, gapUsePaint)
                } else {
                    canvas.drawRect(boardLeft - 4f, exitY, boardLeft + 4f, exitY + cellSize, gapUsePaint)
                }
            }
            ExitDirection.TOP -> {
                val exitX = boardLeft + col * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop - arrowW * 0.4f
                arrowPath.reset()
                arrowPath.moveTo(cx - arrowH / 2f, cy)
                arrowPath.lineTo(cx, cy - arrowW)
                arrowPath.lineTo(cx + arrowH / 2f, cy)
                arrowPath.close()
                canvas.drawPath(arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(exitX - gapExtra, boardTop - 4f, exitX + cellSize + gapExtra, boardTop + 4f, gapUsePaint)
                } else {
                    canvas.drawRect(exitX, boardTop - 4f, exitX + cellSize, boardTop + 4f, gapUsePaint)
                }
            }
            ExitDirection.BOTTOM -> {
                val exitX = boardLeft + col * cellSize
                val cx = exitX + cellSize / 2f
                val cy = boardTop + boardSize + arrowW * 0.4f
                arrowPath.reset()
                arrowPath.moveTo(cx - arrowH / 2f, cy)
                arrowPath.lineTo(cx, cy + arrowW)
                arrowPath.lineTo(cx + arrowH / 2f, cy)
                arrowPath.close()
                canvas.drawPath(arrowPath, arrowPaint)
                if (isPrimary) {
                    canvas.drawRect(exitX - gapExtra, boardTop + boardSize - 4f, exitX + cellSize + gapExtra, boardTop + boardSize + 4f, gapUsePaint)
                } else {
                    canvas.drawRect(exitX, boardTop + boardSize - 4f, exitX + cellSize, boardTop + boardSize + 4f, gapUsePaint)
                }
            }
        }
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private fun drawWallBlock(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, cr: Float, padding: Float) {
        blockPaint.color = WALL_COLOR
        tmpRect1.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect1, cr, cr, blockPaint)
        canvas.drawLine(left + padding, top + padding, right - padding, bottom - padding, wallXPaint)
        canvas.drawLine(right - padding, top + padding, left + padding, bottom - padding, wallXPaint)
    }

    private fun drawCatBlock(canvas: Canvas, g: PuzzleGrid, block: com.meowrescue.game.puzzle.PuzzleBlock,
                              left: Float, top: Float, right: Float, bottom: Float, isDragging: Boolean) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val cellW = right - left
        val cellH = bottom - top
        val bmp = catBitmap

        val scale = if (isDragging) 1.05f else 1.0f
        val imgSize = min(cellW, cellH) * 0.92f * scale

        if (isDragging) {
            catShadowPaint.color = 0xFF000000.toInt()
            catShadowPaint.alpha = 25
            catShadowPaint.maskFilter = catDragBlur
            canvas.drawCircle(cx + 3f, cy + 4f, imgSize * 0.35f, catShadowPaint)
        }

        val cats = g.blocks.filter { it.isCat }
        val isSecondCat = g.exitDirection2 != null && cats.size >= 2 && block.id != cats.first().id
        if (isSecondCat) {
            catRingPaint.color = EXIT2_COLOR
            catRingPaint.strokeWidth = 3f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, imgSize * 0.48f, catRingPaint)
        } else if (g.exitDirection2 != null && cats.size >= 2) {
            catRingPaint.color = EXIT_COLOR
            catRingPaint.strokeWidth = 3f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, imgSize * 0.48f, catRingPaint)
        }

        if (bmp != null && !bmp.isRecycled) {
            tmpRect1.set(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, tmpRect1, null)
        } else {
            canvas.drawCircle(cx, cy, imgSize * 0.45f, catFallbackPaint)
            textPaint.textSize = imgSize * 0.35f
            canvas.drawText("\uD83D\uDC31", cx, cy + textPaint.textSize * 0.3f, textPaint)
        }
    }

    private fun drawNormalBlock(canvas: Canvas, block: com.meowrescue.game.puzzle.PuzzleBlock,
                                 left: Float, top: Float, right: Float, bottom: Float,
                                 cr: Float, isDragging: Boolean, isSnapping: Boolean) {
        val color = when {
            block.isKey -> KEY_COLOR
            block.linkId >= 0 -> LINK_COLOR
            else -> BLOCK_COLORS[(block.id - 1) % BLOCK_COLORS.size]
        }

        if (isDragging || isSnapping) {
            blockShadow.alpha = 80
            tmpRect2.set(left + 6f, top + 6f, right + 6f, bottom + 6f)
            canvas.drawRoundRect(tmpRect2, cr, cr, blockShadow)
        }

        blockPaint.color = color
        tmpRect1.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect1, cr, cr, blockPaint)

        // Highlight gloss using cached gradient + matrix translate
        val grad = glossGradient
        if (grad != null) {
            tmpMatrix.reset()
            tmpMatrix.setTranslate(left, top)
            grad.setLocalMatrix(tmpMatrix)
            glossPaint.shader = grad
        } else {
            glossPaint.shader = LinearGradient(
                left, top, left, top + (bottom - top) * 0.4f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(tmpRect1, cr, cr, glossPaint)

        if (block.isKey) {
            textPaint.textSize = cellSize * 0.55f
            canvas.drawText(
                "\uD83D\uDD11",
                (left + right) / 2f,
                (top + bottom) / 2f + textPaint.textSize * 0.3f,
                textPaint
            )
        }

        if (block.linkId >= 0) {
            chainIconPaint.textSize = cellSize * 0.3f
            canvas.drawText("\uD83D\uDD17", (left + right) / 2f,
                (top + bottom) / 2f + chainIconPaint.textSize * 0.3f, chainIconPaint)
        }
    }

    private fun drawHintGlow(canvas: Canvas, block: com.meowrescue.game.puzzle.PuzzleBlock,
                              left: Float, top: Float, right: Float, bottom: Float, cr: Float) {
        val elapsed = System.currentTimeMillis() - hintStartTime
        val glowAlpha = if (elapsed > 3000L) {
            val fade = 1f - ((elapsed - 3000L) / 1500f).coerceIn(0f, 1f)
            if (fade <= 0f) hintBlockId = -1
            fade
        } else 1f
        if (glowAlpha <= 0f) return

        val glowPulse = pulse(300.0, 0.4f, 0.6f)
        hintGlowPaint.strokeWidth = cellSize * 0.12f
        hintGlowPaint.color = android.graphics.Color.argb(
            (glowAlpha * glowPulse * 255).toInt().coerceIn(0, 255),
            0xFF, 0xD7, 0x00
        )
        hintGlowPaint.maskFilter = hintGlowBlur
        val padding = cellSize * 0.07f
        tmpRect1.set(left - padding * 0.5f, top - padding * 0.5f,
            right + padding * 0.5f, bottom + padding * 0.5f)
        canvas.drawRoundRect(tmpRect1, cr, cr, hintGlowPaint)

        hintArrowPaint.color = android.graphics.Color.argb(
            (glowAlpha * glowPulse * 230).toInt().coerceIn(0, 255),
            0xFF, 0xD7, 0x00
        )
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val arrowLen = cellSize * 0.35f
        val arrowHead = cellSize * 0.18f
        val dx = if (hintDCol > 0) 1f else if (hintDCol < 0) -1f else 0f
        val dy = if (hintDRow > 0) 1f else if (hintDRow < 0) -1f else 0f
        val tipX = cx + dx * arrowLen
        val tipY = cy + dy * arrowLen
        val tailX = cx - dx * arrowLen * 0.3f
        val tailY = cy - dy * arrowLen * 0.3f

        hintShaftPaint.color = hintArrowPaint.color
        hintShaftPaint.strokeWidth = cellSize * 0.07f
        canvas.drawLine(tailX, tailY, tipX, tipY, hintShaftPaint)

        val perpX = -dy * arrowHead * 0.5f
        val perpY = dx * arrowHead * 0.5f
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(tipX - dx * arrowHead + perpX, tipY - dy * arrowHead + perpY)
        arrowPath.lineTo(tipX - dx * arrowHead - perpX, tipY - dy * arrowHead - perpY)
        arrowPath.close()
        canvas.drawPath(arrowPath, hintArrowPaint)
    }

    private fun drawBlocks(canvas: Canvas, g: PuzzleGrid) {
        val padding = cellSize * 0.07f
        val cr      = min(cellSize * 0.22f, 24f)

        val draggedBlock = if (dragBlockId >= 0) g.blocks.firstOrNull { it.id == dragBlockId } else null
        val dragPartnerId = if (draggedBlock != null && draggedBlock.linkId >= 0) {
            g.blocks.firstOrNull { it.linkId == draggedBlock.linkId && it.id != dragBlockId }?.id ?: -1
        } else -1

        for (block in g.blocks) {
            val isDragging = (block.id == dragBlockId || block.id == dragPartnerId)
            val isSnapping = (block.id == snapBlockId && snapAnimating)

            if (block.isCat && escapePhase == 2) {
                val elapsed = (System.currentTimeMillis() - escapeStartTime).toFloat() / CAT_SLIDE_MS
                val t = elapsed.coerceIn(0f, 1f)
                val easeT = t * t
                drawCatSlideOut(canvas, g, block, padding, cr, easeT)
                continue
            }
            if (block.isCat && escapePhase >= 3) continue

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

            if (isSnapping) {
                val elapsed = (System.currentTimeMillis() - snapStartTime).toFloat() / SNAP_DURATION_MS
                val t = elapsed.coerceIn(0f, 1f)
                val easeT = 1f - (1f - t) * (1f - t)
                val curCol = snapFromCol + (block.col - snapFromCol) * easeT
                val curRow = snapFromRow + (block.row - snapFromRow) * easeT
                left = boardLeft + curCol * cellSize + padding
                top  = boardTop  + curRow * cellSize + padding
            } else if (isDragging && dragAxis != 0) {
                left += dragSmoothX
                top  += dragSmoothY
            }

            val widthCells  = if (block.isHorizontal || block.length == 1) block.length else 1
            val heightCells = if (!block.isHorizontal || block.length == 1) block.length else 1
            val visualRight  = if (isDragging || isSnapping) left + (widthCells * cellSize - 2 * padding) else right
            val visualBottom = if (isDragging || isSnapping) top  + (heightCells * cellSize - 2 * padding) else bottom

            if (block.id == hintBlockId) {
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

        val cx = (left + slideRight) / 2f
        val cy = (top + slideBottom) / 2f
        val imgSize = min(slideRight - left, slideBottom - top) * 0.92f

        val bmp = catBitmap
        if (bmp != null && !bmp.isRecycled) {
            val imgRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
            canvas.drawBitmap(bmp, null, imgRect, null)
        } else {
            canvas.drawCircle(cx, cy, imgSize * 0.45f, slideOutFallbackPaint)
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

        val overlayAlpha = (0.55f + 0.08f * sin(tutorialPulse.toDouble())).toFloat()
        tutBgPaint.color = Color.BLACK
        tutBgPaint.alpha = (overlayAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, tutBgPaint)

        val panelW = w * 0.85f
        val panelH = 160 * density
        val panelL = (w - panelW) / 2f
        val panelT = h * 0.35f
        tutPanelPaint.color = 0xFFFFF8F0.toInt()
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

        if (tutorialAutoDismissAt == 0L) {
            val pulsed = (sin(tutorialPulse * 2.0) * 0.3 + 0.7).toFloat()
            tutHintPaint.textSize = 13 * density
            tutHintPaint.alpha = (pulsed * 255).toInt()
            canvas.drawText("Tap to continue", cx, panelT + panelH - 18 * density, tutHintPaint)
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private fun drawToolbar(canvas: Canvas) {
        val density    = resources.displayMetrics.density
        val arrowExtra = if (grid?.exitDirection == ExitDirection.BOTTOM) ARROW_AREA_DP * density else 0f
        val toolbarTop = boardTop + boardSize + arrowExtra + cellSize * 0.8f
        val btnH       = 46 * density
        val btnW       = (width * 0.21f)
        val margin     = (width - btnW * 4) / 5f

        buttonTextPaint.textSize = 13 * density

        undoRect.set(margin, toolbarTop, margin + btnW, toolbarTop + btnH)
        buttonPaint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(undoRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "Undo",
            undoRect.centerX(), undoRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        val hintLeft = margin * 2 + btnW
        hintRect.set(hintLeft, toolbarTop, hintLeft + btnW, toolbarTop + btnH)
        val hintPulse = pulse(500.0, 0.15f, 0.85f)
        val hintR = (0xFF * hintPulse).toInt().coerceIn(0, 255)
        val hintG = (0xC0 * hintPulse).toInt().coerceIn(0, 255)
        buttonPaint.color = android.graphics.Color.argb(255, hintR, hintG, 0)
        canvas.drawRoundRect(hintRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            "Hint${if (hintCount > 0) " $hintCount" else ""}",
            hintRect.centerX(), hintRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

        val solveLeft = margin * 3 + btnW * 2
        solveRect.set(solveLeft, toolbarTop, solveLeft + btnW, toolbarTop + btnH)
        buttonPaint.color = if (autoSolving) 0xFF7E57C2.toInt() else 0xFF9575CD.toInt()
        canvas.drawRoundRect(solveRect, 12 * density, 12 * density, buttonPaint)
        canvas.drawText(
            if (autoSolving) "..." else "Solve",
            solveRect.centerX(), solveRect.centerY() + buttonTextPaint.textSize * 0.35f,
            buttonTextPaint
        )

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
        canvas.drawRoundRect(
            RectF(panelL, panelT, panelL + panelW, panelT + panelH),
            24 * density, 24 * density, victoryPanelPaint
        )

        victoryTitlePaint.textSize = 28 * density
        val clearTitle = if (isEndless) "Endless #$endlessCount Clear!" else "Stage Clear!"
        canvas.drawText(clearTitle, cx, panelT + 52 * density, victoryTitlePaint)

        victoryMovePaint.textSize = 16 * density
        canvas.drawText("Moves: ${g.getMoveCount()}", cx, panelT + 78 * density, victoryMovePaint)

        val starSize = 36 * density
        val starGap  = 8 * density
        val totalW   = 3 * starSize + 2 * starGap
        var starX    = cx - totalW / 2f
        val starY    = panelT + 98 * density
        val pulseAmp = if (victoryStars == 3) 0.18f else 0.08f
        val pulsed   = 1f + pulseAmp * sin(starAnimPhase.toDouble()).toFloat()

        if (victoryStars == 3) {
            val shimmerAlpha = ((sin(starAnimPhase.toDouble()) * 0.3 + 0.5) * 255).toInt().coerceIn(0, 255)
            victoryShimmerPaint.maskFilter = BlurMaskFilter(starSize * 1.2f, BlurMaskFilter.Blur.NORMAL)
            victoryShimmerPaint.alpha = shimmerAlpha
            canvas.drawCircle(cx, starY + starSize / 2f, totalW * 0.65f, victoryShimmerPaint)
        }

        for (i in 1..3) {
            val earned  = i <= victoryStars
            val starDelay = (i - 1) * 0.4f
            val starPhase = (starAnimPhase - starDelay).coerceAtLeast(0f)
            val popScale = when {
                starPhase < 0.01f -> 0f
                starPhase < 0.3f  -> (starPhase / 0.3f) * 1.25f
                starPhase < 0.5f  -> 1.25f - ((starPhase - 0.3f) / 0.2f) * 0.25f
                else              -> if (earned) pulsed else 1f
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
                    victoryStarPaint.color = if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt()
                    canvas.drawCircle(rect.centerX(), rect.centerY(), starSize / 2f * scale, victoryStarPaint)
                }
            }
            starX += starSize + starGap
        }

        if (victoryStars == 3 && starAnimPhase > 1.5f) {
            val perfectAlpha = ((starAnimPhase - 1.5f) / 0.5f).coerceIn(0f, 1f)
            victoryPerfectPaint.textSize = 22 * density
            victoryPerfectPaint.alpha = (perfectAlpha * 255).toInt()
            canvas.drawText("PERFECT!", cx, starY - 10 * density, victoryPerfectPaint)
        }

        val btnW  = panelW * 0.7f
        val btnH2 = 44 * density
        val btnGap = 10 * density
        val btnL  = cx - btnW / 2f
        var btnY  = starY + starSize + 20 * density

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

        cachedDensity = resources.displayMetrics.density
        val density    = cachedDensity
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

        catDragBlur = BlurMaskFilter(cellSize * 0.22f, BlurMaskFilter.Blur.NORMAL)
        hintGlowBlur = BlurMaskFilter(cellSize * 0.18f, BlurMaskFilter.Blur.OUTER)

        // Build gloss gradient once at layout time (height = cellSize * 0.4)
        glossGradient = LinearGradient(
            0f, 0f, 0f, cellSize * 0.4f,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )

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
