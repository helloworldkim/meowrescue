package com.meowrescue.game.launch.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.core.economy.EconomyManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.launch.model.LaunchDifficulty
import com.meowrescue.game.ui.Theme
import com.meowrescue.game.launch.physics.LaunchPhysicsWorld
import com.meowrescue.game.launch.stage.LaunchStageGenerator
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchGameActivity : AppCompatActivity() {

    private lateinit var gameView: LaunchGameView
    private lateinit var repository: GameRepository
    private lateinit var economyManager: EconomyManager
    private val stageGenerator = LaunchStageGenerator()

    private var currentStageId: Int = 1
    private var selectedDifficulty: LaunchDifficulty = LaunchDifficulty.EASY
    private var bannerAd: AdView? = null
    private var physicsWorld: LaunchPhysicsWorld? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.init(this)
        repository = GameRepository(this)
        economyManager = EconomyManager(repository)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        gameView = LaunchGameView(this)
        rootLayout.addView(
            gameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
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

        setContentView(rootLayout)
        enableImmersiveMode()

        setupCallbacks()
        showDifficultyDialog()
    }

    // ── Difficulty selection ──────────────────────────────────────────────

    private fun showDifficultyDialog() {
        val dp = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "난이도 선택"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.LAUNCH_SLINGSHOT)  // 다크브라운
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(title)

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setCancelable(false)
            .create()

        for (difficulty in LaunchDifficulty.entries) {
            val btnColor = when (difficulty) {
                LaunchDifficulty.EASY -> Theme.LAUNCH_DIFF_EASY
                LaunchDifficulty.NORMAL -> Theme.LAUNCH_DIFF_NORMAL
                LaunchDifficulty.HARD -> Theme.LAUNCH_DIFF_HARD
            }

            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(btnColor)
                    cornerRadius = 12 * dp
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * dp).toInt()
                }

                val labelText = TextView(this@LaunchGameActivity).apply {
                    text = difficulty.label
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }
                addView(labelText)

                val descText = TextView(this@LaunchGameActivity).apply {
                    text = difficulty.description
                    textSize = 12f
                    setTextColor(Theme.LAUNCH_LIGHT_GRAY)  // 연회색
                    gravity = Gravity.CENTER
                }
                addView(descText)

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    SoundManager.playButtonTap()
                    selectedDifficulty = difficulty
                    dialog.dismiss()
                    startGameWithDifficulty()
                }
            }
            container.addView(btn)
        }

        dialog.show()
    }

    private fun startGameWithDifficulty() {
        gameView.setDifficultyLabel(selectedDifficulty.label)
        lifecycleScope.launch {
            val maxCompleted = repository.getMaxCompletedLaunchStage()
            currentStageId = maxCompleted + 1
            loadStage(currentStageId)
        }
    }

    // ── Callbacks ──────────────────────────────────────────────────────────

    private fun setupCallbacks() {
        gameView.onStageClear = { result ->
            lifecycleScope.launch {
                val isFirstClear = repository.saveLaunchProgress(
                    currentStageId, result.stars, selectedDifficulty.coinMultiplier, result.score
                )
                val completedCount = repository.getCompletedLaunchCount()
                economyManager.awardLaunchCoins(
                    stars = result.stars,
                    coinMultiplier = selectedDifficulty.coinMultiplier,
                    isFirstClear = isFirstClear,
                    completedLaunchCount = completedCount
                )
                checkLaunchAchievements(result.catsUsed, result.tntExplosions)
            }
        }

        gameView.onNextStageClicked = {
            currentStageId++
            loadStage(currentStageId)
        }

        gameView.onRetryClicked = {
            loadStage(currentStageId)
        }

        gameView.onMenuClicked = {
            finish()
        }
    }

    // ── Launch achievements ─────────────────────────────────────────────────

    private suspend fun checkLaunchAchievements(catsUsed: Int, tntExplosions: Int) {
        // Launch-specific achievements
        val launchClears = repository.getCompletedLaunchCount()
        if (launchClears >= 10) repository.unlockAchievement("launch_10")
        if (catsUsed == 1) repository.unlockAchievement("launch_1cat")
        if (tntExplosions >= 3) repository.unlockAchievement("launch_tnt")

        // Shared achievements (cat collection, economy)
        val unlockedCats = repository.getUnlockedCats()
        if (unlockedCats.size >= 3) repository.unlockAchievement("cat_3")
        if (unlockedCats.size >= 7) repository.unlockAchievement("cat_7")
        if (unlockedCats.size >= 13) repository.unlockAchievement("cat_all")

        val totalCoins = repository.getTotalCoinsEarned()
        if (totalCoins >= 100) repository.unlockAchievement("coins_100")
        if (totalCoins >= 1000) repository.unlockAchievement("coins_1000")
    }

    // ── Stage loading ──────────────────────────────────────────────────────

    private fun loadStage(stageId: Int) {
        loadJob?.cancel()
        currentStageId = stageId
        loadJob = lifecycleScope.launch {
            val unlockedCatIds = getUnlockedCatIdsForLaunch()

            val config = withContext(Dispatchers.Default) {
                stageGenerator.generate(stageId, unlockedCatIds, selectedDifficulty.offset)
            }

            // Build physics world on background thread
            val world = withContext(Dispatchers.Default) {
                val w = LaunchPhysicsWorld()
                for (structure in config.structures) {
                    for (block in structure.blocks) {
                        w.createObstacle(
                            centerX = structure.baseX + block.offsetX,
                            centerY = structure.baseY + block.offsetY + block.height / 2f,
                            widthM = block.width,
                            heightM = block.height,
                            material = block.material,
                            angleDeg = block.angleDeg
                        )
                    }
                    for ((ex, ey) in structure.enemyPositions) {
                        w.createEnemy(
                            centerX = structure.baseX + ex,
                            centerY = structure.baseY + ey + 0.25f,
                            radiusM = 0.25f
                        )
                    }
                }
                w
            }
            physicsWorld = world

            // Load cat bitmaps on IO thread
            val bitmaps = withContext(Dispatchers.IO) {
                loadCatBitmaps(config.catIds)
            }

            gameView.setCatBitmaps(bitmaps)
            gameView.setStage(config, world)
        }
    }

    private suspend fun getUnlockedCatIdsForLaunch(): List<Int> {
        val maxCleared = repository.getMaxCompletedLevel()
        return GameRepository.CAT_DEFINITIONS
            .filter { it.requiredStage <= maxCleared }
            .map { it.id }
            .ifEmpty { listOf(1) }
    }

    private fun loadCatBitmaps(catIds: List<Int>): Map<Int, Bitmap> {
        val map = mutableMapOf<Int, Bitmap>()
        for (catId in catIds.distinct()) {
            val def = GameRepository.CAT_DEFINITIONS.firstOrNull { it.id == catId }
            val resId = def?.drawableRes ?: R.drawable.cat_1
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val bmp = BitmapFactory.decodeResource(resources, resId, opts)
            if (bmp != null) map[catId] = bmp
        }
        return map
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
        gameView.resume()
        bannerAd?.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        bannerAd?.pause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.recycleBitmaps()
        bannerAd?.destroy()
        SoundManager.stopBgm()
    }
}
