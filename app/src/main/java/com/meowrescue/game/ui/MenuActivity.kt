package com.meowrescue.game.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.data.UserProgress
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var repository: GameRepository
    private var showingLevelSelect = false
    private lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        repository = GameRepository(this)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor(Theme.COLOR_BACKGROUND))
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 80, 48, 80)
        }
        rootScroll.addView(contentLayout)
        setContentView(rootScroll)

        showMainMenu()
    }

    private fun showMainMenu() {
        contentLayout.removeAllViews()
        showingLevelSelect = false

        // Cat mascot image (actual 384x512 = 3:4 portrait → 150x200dp)
        val catImage = ImageView(this).apply {
            setImageResource(R.drawable.cat_1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val dp150 = (150 * resources.displayMetrics.density).toInt()
            val dp200 = (200 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(dp150, dp200)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = 32
            layoutParams = lp
        }
        contentLayout.addView(catImage)

        // Title
        val title = TextView(this).apply {
            text = "Meow Rescue"
            textSize = 40f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(Theme.COLOR_TITLE_TEXT))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 56
            layoutParams = lp
        }
        contentLayout.addView(title)

        // Play button using btn_play.png (actual 384x512 = 3:4 portrait → 105x140dp)
        val playButton = ImageView(this).apply {
            setImageResource(R.drawable.btn_play)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = true
            val dp105 = (105 * resources.displayMetrics.density).toInt()
            val dp140 = (140 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(dp105, dp140)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = 16
            lp.bottomMargin = 16
            layoutParams = lp
            setOnClickListener {
                SoundManager.playButtonTap()
                loadAndShowLevelSelect()
            }
        }
        contentLayout.addView(playButton)

        val collectionButton = makeButton("Collection", Theme.COLOR_BUTTON_COLLECTION)
        collectionButton.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        contentLayout.addView(collectionButton)
    }

    private fun loadAndShowLevelSelect() {
        lifecycleScope.launch {
            val maxCompleted = repository.getMaxCompletedLevel()
            val totalLevels = countAvailableLevels()
            val progressList = (1..totalLevels).map { levelId ->
                levelId to repository.getProgress(levelId)
            }
            showLevelSelect(maxCompleted, totalLevels, progressList)
        }
    }

    private fun showLevelSelect(
        maxCompleted: Int,
        totalLevels: Int,
        progressList: List<Pair<Int, UserProgress?>>
    ) {
        contentLayout.removeAllViews()
        showingLevelSelect = true

        val title = TextView(this).apply {
            text = "Select Level"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(Theme.COLOR_TITLE_TEXT))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 40
            layoutParams = lp
        }
        contentLayout.addView(title)

        val density = resources.displayMetrics.density
        val btnSize   = (80 * density).toInt()   // square level button (512x512 = 1:1)
        val starW     = (12 * density).toInt()   // star width (384x512 = 3:4)
        val starH     = (16 * density).toInt()   // star height
        val pathSize  = (80 * density).toInt()   // path connector (512x512 = 1:1 square)

        for ((levelId, progress) in progressList) {
            val isUnlocked = levelId == 1 || levelId <= maxCompleted + 1
            val stars = if (isUnlocked) progress?.stars ?: 0 else 0
            val isCompleted = isUnlocked && stars > 0
            val isLeftAligned = (levelId % 2 == 1)
            val rowGravity = if (isLeftAligned) Gravity.START else Gravity.END

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = rowGravity or Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 4
                lp.bottomMargin = 4
                layoutParams = lp
                if (!isUnlocked) alpha = 0.45f
                if (isUnlocked) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        SoundManager.playButtonTap()
                        val intent = Intent(this@MenuActivity, GameActivity::class.java)
                        intent.putExtra("level_id", levelId)
                        startActivity(intent)
                    }
                }
            }

            row.addView(buildLevelEntry(levelId, stars, isUnlocked, isCompleted, density, btnSize, starW, starH))
            contentLayout.addView(row)

            // Path connector between levels (not after the last one)
            if (levelId < totalLevels) {
                val pathFlip = if (!isLeftAligned) -1f else 1f
                val pathView = ImageView(this).apply {
                    setImageResource(R.drawable.path)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    scaleX = pathFlip
                    val lp = LinearLayout.LayoutParams(pathSize, pathSize)
                    lp.gravity = Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 2
                    lp.bottomMargin = 2
                    layoutParams = lp
                }
                contentLayout.addView(pathView)
            }
        }

        // Home/Back button (actual 384x512 = 3:4 portrait → 75x100dp)
        val dp75  = (75 * density).toInt()
        val dp100 = (100 * density).toInt()
        val homeButton = ImageView(this).apply {
            setImageResource(R.drawable.btn_home)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(dp75, dp100)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = 40
            layoutParams = lp
            setOnClickListener {
                SoundManager.playButtonTap()
                showMainMenu()
            }
        }
        contentLayout.addView(homeButton)
    }

    private fun buildLevelEntry(
        levelId: Int, stars: Int, isUnlocked: Boolean, isCompleted: Boolean,
        density: Float, btnSize: Int, starW: Int, starH: Int
    ): FrameLayout {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        }

        // Background
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.level_button)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })

        // Level number
        frame.addView(TextView(this).apply {
            text = "$levelId"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (isUnlocked) Theme.COLOR_PRIMARY_TEXT else Theme.COLOR_MUTED_TEXT))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                this.gravity = Gravity.CENTER
                bottomMargin = (18 * density).toInt()
            }
        })

        // Stars row
        val starsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (6 * density).toInt()
            }
        }
        for (i in 1..3) {
            starsLayout.addView(ImageView(this).apply {
                setImageResource(if (i <= stars) R.drawable.star_full else R.drawable.star_empty)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(starW, starH).apply {
                    marginStart = 1; marginEnd = 1
                }
            })
        }
        frame.addView(starsLayout)

        // Cleared overlay
        if (isCompleted) {
            frame.addView(ImageView(this).apply {
                setImageResource(R.drawable.level_cleared)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        }

        // Locked overlay
        if (!isUnlocked) {
            frame.addView(ImageView(this).apply {
                setImageResource(R.drawable.level_locked)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        }

        return frame
    }

    private fun countAvailableLevels(): Int {
        var count = 0
        try {
            val files = assets.list("levels") ?: emptyArray()
            count = files.count { it.startsWith("level_") && it.endsWith(".json") }
        } catch (_: Exception) {}
        return if (count > 0) count else 5
    }

    private fun makeButton(text: String, bgColor: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(Color.parseColor(Theme.COLOR_PRIMARY_TEXT))
            setBackgroundColor(Color.parseColor(bgColor))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 24
            lp.bottomMargin = 24
            layoutParams = lp
            setPadding(32, 32, 32, 32)
        }
    }

    override fun onResume() {
        super.onResume()
        if (showingLevelSelect) loadAndShowLevelSelect()
        SoundManager.playBgm("menu")
    }

    override fun onPause() {
        super.onPause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.stopBgm()
    }
}
