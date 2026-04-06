package com.meowrescue.game.puzzle.ui

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.puzzle.engine.PuzzleGenerator
import com.meowrescue.game.puzzle.engine.PuzzleSolver
import com.meowrescue.game.puzzle.model.GenerateResult
import com.meowrescue.game.util.HapticManager
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import kotlin.random.Random

class PuzzleActivity : AppCompatActivity() {

    private lateinit var puzzleView: PuzzleView
    private lateinit var repository: GameRepository
    private val generator = PuzzleGenerator()
    private var currentStage: Int = 1
    private var isEndless: Boolean = false
    private var endlessCount: Int = 1
    private var bannerAd: AdView? = null
    private lateinit var pauseOverlay: FrameLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var frameRoot: FrameLayout
    private var congratsOverlay: FrameLayout? = null
    private var solvePending = false
    private val dp by lazy { resources.displayMetrics.density }

    // ── Next stage preloading (thread-safe) ──────────────────────────────
    private data class PreloadedPuzzle(val stage: Int, val result: GenerateResult)
    private var preloaded: PreloadedPuzzle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.init(this)
        HapticManager.init(this)
        repository = GameRepository(this)

        isEndless = intent.getBooleanExtra("endless", false)
        if (isEndless) {
            endlessCount = repository.getEndlessCount()
            currentStage = generateRandomStage()
        } else {
            currentStage = intent.getIntExtra("stage", 1)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        puzzleView = PuzzleView(this)
        rootLayout.addView(
            puzzleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        bannerAd = AdManager.createBannerAd(this)
        rootLayout.addView(
            bannerAd,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        frameRoot = FrameLayout(this)
        frameRoot.addView(rootLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        loadingOverlay = PuzzleOverlays.buildLoadingOverlay(this, repository.getSelectedCatDrawable(), dp)
        frameRoot.addView(loadingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        pauseOverlay = PuzzleOverlays.buildPauseOverlay(
            context = this,
            dp = dp,
            onResume = {
                pauseOverlay.visibility = View.GONE
                puzzleView.resume()
            },
            onRestart = {
                pauseOverlay.visibility = View.GONE
                puzzleView.resetPuzzle()
                puzzleView.resume()
            },
            onQuit = {
                pauseOverlay.visibility = View.GONE
                finish()
            }
        )
        pauseOverlay.visibility = View.GONE
        frameRoot.addView(pauseOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(frameRoot)
        enableImmersiveMode()

        // Load selected cat bitmap
        val catRes = repository.getSelectedCatDrawable()
        puzzleView.setCatBitmap(catRes)

        setupCallbacks()
        loadStage(currentStage)
        AdManager.loadRewarded(this)
    }

    // ── Stage loading ────────────────────────────────────────────────────

    private fun loadStage(stage: Int) {
        currentStage = stage
        solvePending = false
        loadingOverlay.visibility = View.VISIBLE
        puzzleView.visibility = View.INVISIBLE
        lifecycleScope.launch {
            // Use preloaded result if available for this stage (local capture for safety)
            val cached = preloaded
            val result = if (cached != null && cached.stage == stage) {
                cached.result
            } else {
                withContext(Dispatchers.Default) { generator.generateWithResult(stage) }
            }
            preloaded = null

            puzzleView.setGrid(result.grid, stage, result.optimalMoves, isEndless, endlessCount)
            puzzleView.hintCount = 2
            loadingOverlay.visibility = View.GONE
            puzzleView.visibility = View.VISIBLE

            // Show tutorial for stages 1-3 (only first time ever)
            if (!isEndless && !repository.isTutorialCompleted()) {
                when (stage) {
                    1 -> {
                        puzzleView.tutorialStep = 0
                        puzzleView.tutorialAutoDismissAt = 0L
                        puzzleView.onTutorialDismissed = {
                            repository.setTutorialCompleted()
                        }
                    }
                    2 -> {
                        puzzleView.tutorialStep = 10
                        puzzleView.tutorialAutoDismissAt = System.currentTimeMillis() + 2500L
                        puzzleView.onTutorialDismissed = null
                    }
                    3 -> {
                        puzzleView.tutorialStep = 20
                        puzzleView.tutorialAutoDismissAt = System.currentTimeMillis() + 2500L
                        puzzleView.onTutorialDismissed = {
                            repository.setTutorialCompleted()
                        }
                    }
                }
            }

            // Preload next stage in background
            preloadNextStage()
        }
    }

    private fun preloadNextStage() {
        val nextStage = if (isEndless) generateRandomStage() else (currentStage + 1).coerceAtMost(200)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { generator.generateWithResult(nextStage) }
            preloaded = PreloadedPuzzle(nextStage, result)
        }
    }

    // ── Callbacks ────────────────────────────────────────────────────────

    private fun setupCallbacks() {
        puzzleView.onStageClear = { moves, stars -> handleStageClear(moves, stars) }
        puzzleView.onNextStageClicked = { handleNextStage() }
        puzzleView.onRetryClicked = { loadStage(currentStage) }
        puzzleView.onLevelSelectClicked = { finish() }
        puzzleView.onPauseClicked = {
            puzzleView.pause()
            pauseOverlay.visibility = View.VISIBLE
        }
        puzzleView.onHintClicked = { handleHintRequest() }
        puzzleView.onSolveClicked = { handleSolveRequest() }
    }

    private fun handleStageClear(moves: Int, stars: Int) {
        lifecycleScope.launch {
            if (isEndless) {
                endlessCount++
                repository.setEndlessCount(endlessCount)
                if (endlessCount > repository.getEndlessBest()) {
                    repository.setEndlessBest(endlessCount)
                }
                AdManager.onStageClear()
            } else {
                val prevMax = repository.getMaxCompletedLevel()
                repository.saveProgress(currentStage, stars, null)
                AdManager.onStageClear()

                // Check for new cat unlock (only on first-time clear)
                if (currentStage > prevMax) {
                    val newCat = repository.getNewlyUnlockedCat(currentStage)
                    if (newCat != null) {
                        showCongratsDialog(newCat)
                    }
                }
            }
        }
    }

    private fun handleNextStage() {
        // Use preloaded stage if available, otherwise compute fresh (local capture)
        val cached = preloaded
        val nextStage = if (cached != null && cached.stage > 0) cached.stage
            else if (isEndless) generateRandomStage() else (currentStage + 1).coerceAtMost(200)
        if (AdManager.shouldShowAd(currentStage)) {
            if (AdManager.isRewardedReady()) {
                AdManager.showRewarded(this@PuzzleActivity,
                    onRewarded = {
                        loadStage(nextStage)
                        AdManager.loadRewarded(this@PuzzleActivity)
                    },
                    onDismissed = { AdManager.loadRewarded(this@PuzzleActivity) }
                )
            } else {
                Toast.makeText(this, "광고를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                AdManager.loadRewarded(this)
            }
        } else {
            loadStage(nextStage)
        }
    }

    private fun handleHintRequest() {
        if (puzzleView.hintCount > 0) {
            puzzleView.hintCount--
            val currentGrid = puzzleView.getCurrentGrid()
            if (currentGrid != null) {
                lifecycleScope.launch(Dispatchers.Default) {
                    val steps = generator.solveSteps(currentGrid)
                    withContext(Dispatchers.Main) {
                        if (steps != null && steps.isNotEmpty()) {
                            puzzleView.showHint(steps[0])
                        }
                    }
                }
            }
        } else {
            // No hints left — show rewarded ad to grant 1 more hint
            if (AdManager.isRewardedReady()) {
                AdManager.showRewarded(this@PuzzleActivity,
                    onRewarded = {
                        puzzleView.hintCount = 1
                        AdManager.loadRewarded(this@PuzzleActivity)
                    },
                    onDismissed = { AdManager.loadRewarded(this@PuzzleActivity) }
                )
            } else {
                Toast.makeText(this, "광고를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                AdManager.loadRewarded(this)
            }
        }
    }

    private fun handleSolveRequest() {
        if (solvePending) return
        solvePending = true
        if (!AdManager.isRewardedReady()) {
            solvePending = false
            Toast.makeText(this, "광고를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            AdManager.loadRewarded(this)
            return
        }
        AdManager.showRewarded(this@PuzzleActivity,
            onRewarded = {
                if (isDestroyed) { solvePending = false; return@showRewarded }
                AdManager.loadRewarded(this@PuzzleActivity)
                val currentGrid = puzzleView.getCurrentGrid()
                if (currentGrid != null) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        val steps = generator.solveSteps(currentGrid)
                        withContext(Dispatchers.Main) {
                            solvePending = false
                            if (!steps.isNullOrEmpty()) {
                                puzzleView.startAutoSolve(steps)
                            }
                        }
                    }
                } else {
                    solvePending = false
                }
            },
            onDismissed = {
                solvePending = false
                AdManager.loadRewarded(this@PuzzleActivity)
            }
        )
    }

    private fun generateRandomStage(): Int = Random.nextInt(131, 10000)

    // ── Congratulation dialog for new cat unlock ────────────────────────

    private fun showCongratsDialog(cat: GameRepository.CatDefinition) {
        val overlay = PuzzleOverlays.buildCongratsOverlay(this, cat, dp, frameRoot) {
            congratsOverlay = null
        }
        congratsOverlay = overlay
        frameRoot.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        SoundManager.playStarEarn()
    }

    // ── Immersive mode (엣지 스와이프 뒤로가기 방지) ──────────────────────
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.hide(WindowInsetsCompat.Type.navigationBars())
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        puzzleView.resume()
        bannerAd?.resume()
        SoundManager.playBgm("beginner")
    }

    override fun onPause() {
        super.onPause()
        puzzleView.pause()
        bannerAd?.pause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        puzzleView.recycleBitmaps()
        bannerAd?.destroy()
        SoundManager.stopBgm()
        HapticManager.release()
    }
}
