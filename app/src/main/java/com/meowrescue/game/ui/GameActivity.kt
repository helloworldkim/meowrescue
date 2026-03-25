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
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.game.GameEngine
import com.meowrescue.game.game.GameLoop
import com.meowrescue.game.level.LevelLoader
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var gameEngine: GameEngine
    private lateinit var gameView: GameView
    private lateinit var gameLoop: GameLoop
    private var levelId: Int = 1
    private lateinit var repository: GameRepository

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
        gameView.setBackgroundForLevel(levelData.difficulty)
        gameView.resetCallbackState()

        gameLoop = GameLoop(gameEngine, gameView)

        gameView.onLevelComplete = {
            val stars = gameEngine.calculateStars()
            val rescuedCatId = gameEngine.cats.firstOrNull { it.isRescued }?.catId
            lifecycleScope.launch {
                repository.saveProgress(levelId, stars, catId = rescuedCatId)
            }
            runOnUiThread {
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra("level_id", levelId + 1)
                startActivity(intent)
                finish()
            }
        }

        gameView.onLevelFailed = {
            runOnUiThread {
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra("level_id", levelId)
                startActivity(intent)
                finish()
            }
        }

        gameView.onNavigateHome = {
            runOnUiThread { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        gameLoop.start()
    }

    override fun onPause() {
        super.onPause()
        gameLoop.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameLoop.stop()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
