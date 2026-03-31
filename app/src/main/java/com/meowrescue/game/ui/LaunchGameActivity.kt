package com.meowrescue.game.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.minigame.LaunchGameView
import com.meowrescue.game.minigame.LaunchPhysicsWorld
import com.meowrescue.game.minigame.LaunchStageGenerator
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchGameActivity : AppCompatActivity() {

    private lateinit var gameView: LaunchGameView
    private lateinit var repository: GameRepository
    private val stageGenerator = LaunchStageGenerator()

    private var currentStageId: Int = 1
    private var bannerAd: AdView? = null
    private var physicsWorld: LaunchPhysicsWorld? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SoundManager.init(this)
        repository = GameRepository(this)

        currentStageId = intent.getIntExtra("stage", 1)

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

        setupCallbacks()
        loadStage(currentStageId)
    }

    // ── Callbacks ──────────────────────────────────────────────────────────

    private fun setupCallbacks() {
        gameView.onStageClear = { catsUsed, stars ->
            lifecycleScope.launch {
                repository.saveLaunchProgress(currentStageId, stars)
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

    // ── Stage loading ──────────────────────────────────────────────────────

    private fun loadStage(stageId: Int) {
        loadJob?.cancel()
        currentStageId = stageId
        loadJob = lifecycleScope.launch {
            val unlockedCatIds = getUnlockedCatIdsForLaunch()

            val config = withContext(Dispatchers.Default) {
                stageGenerator.generate(stageId, unlockedCatIds)
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
        // Cat 1 is always unlocked; add cats whose requiredStage the player has cleared
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
            val bmp = BitmapFactory.decodeResource(resources, resId)
            if (bmp != null) map[catId] = bmp
        }
        return map
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
