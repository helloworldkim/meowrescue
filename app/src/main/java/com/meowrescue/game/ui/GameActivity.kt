package com.meowrescue.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.game.GameEngine
import com.meowrescue.game.game.GameLoop
import com.meowrescue.game.level.LevelLoader
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var gameEngine: GameEngine
    private lateinit var gameView: GameView
    private lateinit var gameLoop: GameLoop
    private var levelId: Int = 1
    private lateinit var repository: GameRepository
    private var levelDifficulty: String = "tutorial"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive mode
        applyImmersiveMode()

        levelId = intent.getIntExtra("level_id", 1)
        repository = GameRepository(this)

        gameEngine = GameEngine()
        gameView = GameView(this)
        gameView.gameEngine = gameEngine
        setContentView(gameView)

        val levelData = try {
            LevelLoader.loadLevel(this, levelId)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load level $levelId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        gameEngine.loadLevel(levelData)
        levelDifficulty = levelData.difficulty
        gameView.setBackgroundForLevel(levelDifficulty)
        gameView.resetCallbackState()

        // Sound: connect game events to SFX
        gameEngine.eventListener = object : GameEngine.GameEventListener {
            override fun onPinRemoved() = SoundManager.playPinRemove()
            override fun onBallDestroyed(isBomb: Boolean) {
                if (isBomb) SoundManager.playBombExplode() else SoundManager.playBallDestroy()
            }
            override fun onBallBounce() = SoundManager.playBallBounce()
            override fun onCatRescued() = SoundManager.playCatRescue()
            override fun onCageDestroyed() = SoundManager.playCageDestroy()
            override fun onTeleport() = SoundManager.playTeleport()
            override fun onLevelSuccess() = SoundManager.playLevelClear()
            override fun onLevelFailed() = SoundManager.playLevelFail()
        }

        gameLoop = GameLoop(gameEngine, gameView)

        // Preload ads
        AdManager.loadInterstitial(this)
        AdManager.loadRewarded(this)

        gameView.onLevelComplete = {
            runOnUiThread {
                val stars = gameEngine.calculateStars()
                val rescuedCatId = gameEngine.cats.firstOrNull { it.isRescued }?.catId
                lifecycleScope.launch {
                    repository.saveProgress(levelId, stars, catId = rescuedCatId)
                }
                SoundManager.playButtonTap()
                val goToNext = {
                    val nextLevel = levelId + 1
                    val maxLevel = try {
                        LevelLoader.loadLevel(this, nextLevel); nextLevel
                    } catch (_: Exception) { levelId }
                    if (maxLevel > levelId) {
                        val intent = Intent(this, GameActivity::class.java)
                        intent.putExtra("level_id", nextLevel)
                        startActivity(intent)
                    }
                    finish()
                }
                if (AdManager.shouldShowInterstitial(levelId)) {
                    AdManager.showInterstitial(this) { goToNext() }
                } else {
                    goToNext()
                }
            }
        }

        gameView.onLevelFailed = {
            runOnUiThread {
                SoundManager.playButtonTap()
                val restartLevel = {
                    val intent = Intent(this, GameActivity::class.java)
                    intent.putExtra("level_id", levelId)
                    startActivity(intent)
                    finish()
                }
                if (AdManager.isRewardedReady()) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Continue?")
                        .setMessage("Watch an ad to retry this level!")
                        .setPositiveButton("Watch Ad") { _, _ ->
                            AdManager.showRewarded(this,
                                onRewarded = { restartLevel() },
                                onDismissed = { restartLevel() }
                            )
                        }
                        .setNegativeButton("Skip") { _, _ -> restartLevel() }
                        .setCancelable(false)
                        .show()
                } else {
                    restartLevel()
                }
            }
        }

        gameView.onNavigateHome = {
            runOnUiThread {
                SoundManager.playButtonTap()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameLoop.start()
        // Play BGM matching the level difficulty
        SoundManager.playBgm(levelDifficulty)
    }

    override fun onPause() {
        super.onPause()
        gameLoop.stop()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameLoop.stop()
        gameView.cleanup()
        SoundManager.stopBgm()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
