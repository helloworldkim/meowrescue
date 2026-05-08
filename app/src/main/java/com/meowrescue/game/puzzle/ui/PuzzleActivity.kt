package com.meowrescue.game.puzzle.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.AchievementDefs
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.ui.CatAssetResolver
import com.meowrescue.game.puzzle.engine.PuzzleGenerator
import com.meowrescue.game.puzzle.engine.PuzzleSolver
import com.meowrescue.game.puzzle.model.GenerateResult
import com.meowrescue.game.puzzle.model.WorldTheme
import com.meowrescue.game.util.HapticManager
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
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

        loadingOverlay = PuzzleOverlays.buildLoadingOverlay(this, CatAssetResolver.getDrawable(repository.getSelectedCatId()), dp)
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
        val catRes = CatAssetResolver.getDrawable(repository.getSelectedCatId())
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

            // Load best score for new record detection
            if (!isEndless) {
                puzzleView.previousBestScore = withContext(Dispatchers.IO) {
                    repository.getBestScore(stage)
                }
                puzzleView.displayCoins = withContext(Dispatchers.IO) {
                    repository.getCoins()
                }
            }

            // Play world-themed BGM
            val theme = WorldTheme.forStage(stage)
            SoundManager.playBgm(theme.bgmKey)

            loadingOverlay.visibility = View.GONE
            puzzleView.visibility = View.VISIBLE

            // Show tutorial on stage 1 only (first time ever)
            if (!isEndless && stage == 1 && !repository.isTutorialCompleted()) {
                puzzleView.tutorialStep = 0
                puzzleView.tutorialAutoDismissAt = 0L
                puzzleView.onTutorialDismissed = {
                    repository.setTutorialCompleted()
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
        puzzleView.onShareClicked = { shareScoreCard() }
        puzzleView.onPowerUpClicked = { index -> handlePowerUp(index) }
    }

    private fun handleStageClear(moves: Int, stars: Int) {
        val score = puzzleView.score
        lifecycleScope.launch {
            if (isEndless) {
                endlessCount++
                repository.setEndlessCount(endlessCount)
                if (endlessCount > repository.getEndlessBest()) {
                    repository.setEndlessBest(endlessCount)
                }
                // Award coins in endless mode (daily cap: 150 coins/day)
                val starCoins = when (stars) { 3 -> 30; 2 -> 20; else -> 10 }
                repository.addEndlessCoins(starCoins)
                AdManager.onStageClear()
                // Endless achievements
                if (endlessCount >= 10) repository.unlockAchievement("endless_10")
                if (endlessCount >= 50) repository.unlockAchievement("endless_50")
            } else {
                val isFirstClear = repository.saveProgress(currentStage, stars, score)
                AdManager.onStageClear()

                // Check for new cat unlock (only on first-time clear)
                if (isFirstClear) {
                    val newCat = repository.getNewlyUnlockedCat(currentStage)
                    if (newCat != null) {
                        showCongratsDialog(newCat)
                    }
                }

                // Check achievements
                checkAchievements(stars)
            }
        }
    }

    private suspend fun checkAchievements(stars: Int) {
        // Progress achievements
        val clearIds = mapOf(1 to "clear_1", 10 to "clear_10", 30 to "clear_30",
            60 to "clear_60", 90 to "clear_90", 120 to "clear_120",
            150 to "clear_150", 180 to "clear_180", 200 to "clear_200")
        for ((stage, id) in clearIds) {
            if (currentStage >= stage) repository.unlockAchievement(id)
        }

        // Star mastery
        val threeStarCount = repository.getThreeStarCount()
        if (threeStarCount >= 10) repository.unlockAchievement("star3_10")
        if (threeStarCount >= 50) repository.unlockAchievement("star3_50")
        if (threeStarCount >= 100) repository.unlockAchievement("star3_100")
        if (threeStarCount >= 200) repository.unlockAchievement("star3_200")

        // Efficiency achievements (solve in fewer moves than optimal)
        val g = puzzleView.getCurrentGrid()
        val moves = g?.getMoveCount() ?: 0
        if (moves > 0 && moves <= puzzleView.optimalMoves) repository.unlockAchievement("optimal_clear")
        if (moves <= 2) repository.unlockAchievement("two_move")

        // No-undo 3-star
        if (stars == 3 && !puzzleView.undoUsed) repository.unlockAchievement("no_undo")

        // Speed achievements
        val elapsed = (System.currentTimeMillis() - puzzleView.stageStartTime) / 1000f
        if (elapsed <= 10f) repository.unlockAchievement("speed_10s")
        if (elapsed <= 5f) repository.unlockAchievement("speed_5s")

        // Cat collection
        val unlockedCats = repository.getUnlockedCats()
        if (unlockedCats.size >= 3) repository.unlockAchievement("cat_3")
        if (unlockedCats.size >= 7) repository.unlockAchievement("cat_7")
        if (unlockedCats.size >= 13) repository.unlockAchievement("cat_all")

        // Coin achievements
        val totalCoins = repository.getTotalCoinsEarned()
        if (totalCoins >= 100) repository.unlockAchievement("coins_100")
        if (totalCoins >= 1000) repository.unlockAchievement("coins_1000")
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

    // ── Power-ups ────────────────────────────────────────────────────
    private fun handlePowerUp(index: Int) {
        val costs = intArrayOf(30, 40, 50)
        val cost = costs.getOrNull(index) ?: return

        lifecycleScope.launch {
            val success = repository.spendCoins(cost)
            if (!success) {
                Toast.makeText(this@PuzzleActivity, "코인이 부족합니다!", Toast.LENGTH_SHORT).show()
                return@launch
            }
            repository.incrementPowerUpUseCount()
            val useCount = repository.getPowerUpUseCount()
            if (useCount >= 1) repository.unlockAchievement("powerup_1")
            if (useCount >= 10) repository.unlockAchievement("powerup_10")

            puzzleView.displayCoins = repository.getCoins()

            when (index) {
                0 -> { // Magnet: highlight movable blocks (toggle)
                    puzzleView.magnetActive = !puzzleView.magnetActive
                    HapticManager.vibrateButtonTap()
                }
                1 -> { // Ice: remove 1 non-cat block (thread-safe)
                    val removed = puzzleView.applyIcePowerUp()
                    if (removed) {
                        HapticManager.vibrateImpact()
                    } else {
                        // Refund if nothing to remove
                        repository.refundCoins(cost)
                        puzzleView.displayCoins = repository.getCoins()
                    }
                }
                2 -> { // Shuffle: random moves on non-cat blocks (thread-safe)
                    puzzleView.applyShufflePowerUp()
                    HapticManager.vibrateBlockMove()
                }
            }
        }
    }

    // ── Share score card ───────────────────────────────────────────────
    private fun shareScoreCard() {
        try {
            val w = 600
            val h = 400
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            // Background
            paint.color = 0xFFFFF8F0.toInt()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

            // Title
            paint.color = 0xFFFF7043.toInt()
            paint.textSize = 36f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textAlign = android.graphics.Paint.Align.CENTER
            c.drawText("Meow Rescue", w / 2f, 60f, paint)

            // Stage
            paint.color = 0xFF4E342E.toInt()
            paint.textSize = 28f
            val stageText = if (isEndless) "Endless #$endlessCount" else "Stage $currentStage"
            c.drawText(stageText, w / 2f, 110f, paint)

            // Stars
            paint.textSize = 40f
            paint.color = 0xFFFFD600.toInt()
            val starText = "\u2605".repeat(puzzleView.victoryStars) + "\u2606".repeat(3 - puzzleView.victoryStars)
            c.drawText(starText, w / 2f, 170f, paint)

            // Score
            paint.textSize = 24f
            paint.color = 0xFF4E342E.toInt()
            c.drawText("Score: ${puzzleView.score}", w / 2f, 220f, paint)

            if (puzzleView.isNewRecord) {
                paint.color = 0xFFFF1744.toInt()
                paint.textSize = 20f
                c.drawText("\u2605 NEW RECORD! \u2605", w / 2f, 260f, paint)
            }

            // Footer
            paint.color = 0xFF9E9E9E.toInt()
            paint.textSize = 16f
            c.drawText("Meow Rescue - 고양이 구출 퍼즐", w / 2f, h - 30f, paint)

            // Save to cache and share
            val shareDir = File(cacheDir, "share").also { it.mkdirs() }
            val file = File(shareDir, "score_card.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Meow Rescue - $stageText - Score: ${puzzleView.score} $starText")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Score"))
        } catch (e: Exception) {
            android.util.Log.w("PuzzleActivity", "Share failed", e)
        }
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
        val theme = WorldTheme.forStage(currentStage)
        SoundManager.playBgm(theme.bgmKey)
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
