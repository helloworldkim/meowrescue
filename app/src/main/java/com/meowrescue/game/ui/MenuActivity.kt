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
import com.meowrescue.game.util.ResourceManager
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var repository: GameRepository
    private var showingLevelSelect = false
    private lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResourceManager.init(this)
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

        // Cat mascot image
        val catImage = ImageView(this).apply {
            setImageResource(R.drawable.cat_1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val dp200 = (200 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(dp200, dp200)
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

        // Play button using btn_play.png as image
        val playButton = ImageView(this).apply {
            setImageResource(R.drawable.btn_play)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = true
            val dp180 = (180 * resources.displayMetrics.density).toInt()
            val dp72 = (72 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(dp180, dp72)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = 16
            lp.bottomMargin = 16
            layoutParams = lp
            setOnClickListener { loadAndShowLevelSelect() }
        }
        contentLayout.addView(playButton)

        val collectionButton = makeButton("Collection", Theme.COLOR_BUTTON_COLLECTION)
        collectionButton.setOnClickListener {
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
        val btnSize   = (80 * density).toInt()   // square level button
        val starSize  = (16 * density).toInt()   // small stars
        val pathW     = (40 * density).toInt()   // path connector width
        val pathH     = (60 * density).toInt()   // path connector height

        for ((levelId, progress) in progressList) {
            val isUnlocked = levelId == 1 || levelId <= maxCompleted + 1
            val stars = if (isUnlocked) progress?.stars ?: 0 else 0
            val isCompleted = isUnlocked && stars > 0

            // Odd levels align LEFT, even levels align RIGHT (zigzag pattern)
            val isLeftAligned = (levelId % 2 == 1)
            val rowGravity = if (isLeftAligned) Gravity.START else Gravity.END

            // Row holding the level button, aligned left or right
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
                        val intent = Intent(this@MenuActivity, GameActivity::class.java)
                        intent.putExtra("level_id", levelId)
                        startActivity(intent)
                    }
                }
            }

            // FrameLayout: level_button background + level number + stars + overlays
            val entryFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            }

            // level_button background image
            val buttonBg = ImageView(this).apply {
                setImageResource(R.drawable.level_button)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            entryFrame.addView(buttonBg)

            // Level number text centered in the button
            val levelLabel = TextView(this).apply {
                text = "$levelId"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isUnlocked) Theme.COLOR_PRIMARY_TEXT else Theme.COLOR_MUTED_TEXT))
                gravity = Gravity.CENTER
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                lp.gravity = Gravity.CENTER
                lp.bottomMargin = (18 * density).toInt() // shift up slightly to leave room for stars
                layoutParams = lp
            }
            entryFrame.addView(levelLabel)

            // Stars row pinned to bottom-center of the button
            val starsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                lp.bottomMargin = (6 * density).toInt()
                layoutParams = lp
            }
            for (i in 1..3) {
                val starImg = ImageView(this).apply {
                    setImageResource(if (i <= stars) R.drawable.star_full else R.drawable.star_empty)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val lp = LinearLayout.LayoutParams(starSize, starSize)
                    lp.marginStart = 1
                    lp.marginEnd = 1
                    layoutParams = lp
                }
                starsLayout.addView(starImg)
            }
            entryFrame.addView(starsLayout)

            // level_cleared overlay for completed levels
            if (isCompleted) {
                val clearedImg = ImageView(this).apply {
                    setImageResource(R.drawable.level_cleared)
                    scaleType = ImageView.ScaleType.FIT_XY
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                entryFrame.addView(clearedImg)
            }

            // level_locked overlay for locked levels
            if (!isUnlocked) {
                val lockedImg = ImageView(this).apply {
                    setImageResource(R.drawable.level_locked)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                entryFrame.addView(lockedImg)
            }

            row.addView(entryFrame)
            contentLayout.addView(row)

            // Path connector between levels (not after the last one)
            if (levelId < totalLevels) {
                // Flip horizontally on right-to-left segments so the path curves the right way
                val pathFlip = if (!isLeftAligned) -1f else 1f
                val pathView = ImageView(this).apply {
                    setImageResource(R.drawable.path)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    scaleX = pathFlip
                    val lp = LinearLayout.LayoutParams(pathW, pathH)
                    lp.gravity = Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 2
                    lp.bottomMargin = 2
                    layoutParams = lp
                }
                contentLayout.addView(pathView)
            }
        }

        // Home/Back button using btn_home image
        val dp56  = (56 * density).toInt()
        val dp160 = (160 * density).toInt()
        val homeButton = ImageView(this).apply {
            setImageResource(R.drawable.btn_home)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(dp160, dp56)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = 40
            layoutParams = lp
            setOnClickListener { showMainMenu() }
        }
        contentLayout.addView(homeButton)
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
            setTextColor(Color.parseColor("#333333"))
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
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
