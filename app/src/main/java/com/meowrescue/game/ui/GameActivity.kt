package com.meowrescue.game.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.meowrescue.game.R
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
        gameView.resetCallbackState()

        gameLoop = GameLoop(gameEngine, gameView)

        gameView.onLevelComplete = {
            runOnUiThread { showSuccessDialog() }
        }
        gameView.onLevelFailed = {
            runOnUiThread { showFailDialog() }
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

    private fun showSuccessDialog() {
        val stars = gameEngine.calculateStars()
        val rescuedCatId = gameEngine.cats.firstOrNull { it.isRescued }?.catId
        repository.saveProgress(levelId, stars, catId = rescuedCatId)

        val dp24 = (24 * resources.displayMetrics.density).toInt()
        val starsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        for (i in 1..3) {
            val starImg = ImageView(this).apply {
                setImageResource(if (i <= stars) R.drawable.star_full else R.drawable.star_empty)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = LinearLayout.LayoutParams(dp24, dp24)
                lp.marginStart = 8
                lp.marginEnd = 8
                layoutParams = lp
            }
            starsLayout.addView(starImg)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(starsLayout)
        }

        AlertDialog.Builder(this)
            .setTitle("Level Clear!")
            .setView(container)
            .setPositiveButton("Next Level") { _, _ -> navigateToNextLevel() }
            .setNegativeButton("Menu") { _, _ -> navigateToMenu() }
            .setCancelable(false)
            .show()
    }

    private fun showFailDialog() {
        AlertDialog.Builder(this)
            .setTitle("Failed!")
            .setMessage("The cats weren't rescued. Try again?")
            .setPositiveButton("Retry") { _, _ -> retryLevel() }
            .setNegativeButton("Menu") { _, _ -> navigateToMenu() }
            .setCancelable(false)
            .show()
    }

    private fun navigateToNextLevel() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("level_id", levelId + 1)
        startActivity(intent)
        finish()
    }

    private fun navigateToMenu() {
        finish()
    }

    private fun retryLevel() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("level_id", levelId)
        startActivity(intent)
        finish()
    }
}
