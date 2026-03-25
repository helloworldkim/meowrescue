package com.meowrescue.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.game.GameEngine
import com.meowrescue.game.game.GameLoop
import com.meowrescue.game.level.LevelLoader

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
            repository.saveProgress(levelId, stars, catId = rescuedCatId)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}