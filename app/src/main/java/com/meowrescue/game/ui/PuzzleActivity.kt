package com.meowrescue.game.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdView
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.puzzle.PuzzleGenerator
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PuzzleActivity : AppCompatActivity() {

    private lateinit var puzzleView: PuzzleView
    private lateinit var repository: GameRepository
    private val generator = PuzzleGenerator()
    private var currentStage: Int = 1
    private var bannerAd: AdView? = null
    private var pauseOverlay: FrameLayout? = null
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var frameRoot: FrameLayout
    private var congratsOverlay: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentStage = intent.getIntExtra("stage", 1)

        SoundManager.init(this)
        repository = GameRepository(this)

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

        loadingOverlay = buildLoadingOverlay()
        frameRoot.addView(loadingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        pauseOverlay = buildPauseOverlay()
        pauseOverlay!!.visibility = View.GONE
        frameRoot.addView(pauseOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(frameRoot)

        // Load selected cat bitmap
        val catRes = repository.getSelectedCatDrawable()
        puzzleView.setCatBitmap(catRes)

        setupCallbacks()
        loadStage(currentStage)
        AdManager.loadInterstitial(this)
    }

    private fun loadStage(stage: Int) {
        currentStage = stage
        loadingOverlay.visibility = View.VISIBLE
        puzzleView.visibility = View.INVISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                generator.generateWithResult(stage)
            }
            puzzleView.setGrid(result.grid, stage, result.optimalMoves)
            loadingOverlay.visibility = View.GONE
            puzzleView.visibility = View.VISIBLE
        }
    }

    private fun setupCallbacks() {
        // Called when escape animation completes and victory overlay shows
        puzzleView.onStageClear = { moves, stars ->
            lifecycleScope.launch {
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

        // Called when user clicks "Next Stage" button in victory overlay
        puzzleView.onNextStageClicked = {
            val nextStage = currentStage + 1
            if (AdManager.shouldShowInterstitial(currentStage)) {
                AdManager.showInterstitial(this@PuzzleActivity) {
                    loadStage(nextStage)
                    AdManager.loadInterstitial(this@PuzzleActivity)
                }
            } else {
                loadStage(nextStage)
                AdManager.loadInterstitial(this@PuzzleActivity)
            }
        }

        puzzleView.onPauseClicked = {
            puzzleView.pause()
            pauseOverlay?.visibility = View.VISIBLE
        }
    }

    // ── Congratulation dialog for new cat unlock ────────────────────────

    private fun showCongratsDialog(cat: GameRepository.CatDefinition) {
        val density = resources.displayMetrics.density

        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xAA000000.toInt())

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * density).toInt(), (28 * density).toInt(),
                (24 * density).toInt(), (28 * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(0xFFFFF8F0.toInt())
                cornerRadius = 24 * density
            }
            elevation = 8 * density
        }

        val title = TextView(this).apply {
            text = "New Cat Unlocked!"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFF7043.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (16 * density).toInt() })

        val catImage = ImageView(this).apply {
            setImageResource(cat.drawableRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val imgSize = (100 * density).toInt()
        panel.addView(catImage, LinearLayout.LayoutParams(imgSize, imgSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = (12 * density).toInt()
        })

        val nameTv = TextView(this).apply {
            text = cat.name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF4E342E.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(nameTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (20 * density).toInt() })

        val okBtn = TextView(this).apply {
            text = "OK"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFFFF7043.toInt())
                cornerRadius = 12 * density
            }
            elevation = 4 * density
            setOnClickListener {
                SoundManager.playButtonTap()
                frameRoot.removeView(overlay)
                congratsOverlay = null
            }
        }
        panel.addView(okBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * density).toInt()
        ))

        overlay.addView(panel, FrameLayout.LayoutParams(
            (280 * density).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        congratsOverlay = overlay
        frameRoot.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        SoundManager.playStarEarn()
    }

    // ── Loading overlay ────────────────────────────────────────────────────

    private fun buildLoadingOverlay(): FrameLayout {
        val density = resources.displayMetrics.density
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xFFFFF8F0.toInt())

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val catImage = android.widget.ImageView(this).apply {
            setImageResource(com.meowrescue.game.R.drawable.cat_1)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val imgSize = (100 * density).toInt()
        container.addView(catImage, LinearLayout.LayoutParams(imgSize, imgSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = (16 * density).toInt()
        })

        val loadingText = TextView(this).apply {
            text = "Preparing puzzle..."
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF4E342E.toInt())
            gravity = Gravity.CENTER
        }
        container.addView(loadingText)

        overlay.addView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        return overlay
    }

    // ── Pause overlay ──────────────────────────────────────────────────────

    private fun buildPauseOverlay(): FrameLayout {
        val density = resources.displayMetrics.density

        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xCC000000.toInt())

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * density).toInt(), (28 * density).toInt(),
                (24 * density).toInt(), (28 * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(0xFFFFF8F0.toInt())
                cornerRadius = 24 * density
            }
            elevation = 8 * density
        }

        val title = TextView(this).apply {
            text = "Paused"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF4E342E.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (20 * density).toInt() })

        panel.addView(makeDialogButton("Resume", 0xFFFF7043.toInt()) {
            pauseOverlay?.visibility = View.GONE
            puzzleView.resume()
        }, buttonLayoutParams(density))

        panel.addView(makeDialogButton("Restart", 0xFF26A69A.toInt()) {
            pauseOverlay?.visibility = View.GONE
            puzzleView.resetPuzzle()
            puzzleView.resume()
        }, buttonLayoutParams(density))

        panel.addView(makeDialogButton("Quit", 0xFF9E9E9E.toInt()) {
            pauseOverlay?.visibility = View.GONE
            finish()
        }, buttonLayoutParams(density))

        overlay.addView(panel, FrameLayout.LayoutParams(
            (260 * density).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        return overlay
    }

    private fun buttonLayoutParams(density: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * density).toInt()
        ).also { it.topMargin = (10 * density).toInt() }
    }

    private fun makeDialogButton(label: String, color: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12 * density
            }
            elevation = 4 * density
            setOnClickListener { onClick() }
        }
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
    }
}
