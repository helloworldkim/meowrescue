package com.meowrescue.game.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.util.ResourceManager
import com.meowrescue.game.util.SoundManager

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
            setOnClickListener { showLevelSelect() }
        }
        contentLayout.addView(playButton)

        val collectionButton = makeButton("Collection", Theme.COLOR_BUTTON_COLLECTION)
        collectionButton.setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        contentLayout.addView(collectionButton)
    }

    private fun showLevelSelect() {
        contentLayout.removeAllViews()
        showingLevelSelect = true

        val title = TextView(this).apply {
            text = "Select Level"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 60
            layoutParams = lp
        }
        contentLayout.addView(title)

        val maxCompleted = repository.getMaxCompletedLevel()
        val totalLevels = countAvailableLevels()
        val catDrawables = listOf(
            R.drawable.cat_1,
            R.drawable.cat_2,
            R.drawable.cat_3,
            R.drawable.cat_4,
            R.drawable.cat_5,
            R.drawable.cat_6,
            R.drawable.cat_7,
            R.drawable.cat_8
        )

        for (levelId in 1..totalLevels) {
            val isUnlocked = levelId == 1 || levelId <= maxCompleted + 1
            val progress = if (isUnlocked) repository.getProgress(levelId) else null
            val stars = progress?.stars ?: 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 16
                lp.bottomMargin = 16
                layoutParams = lp
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.parseColor(if (isUnlocked) Theme.COLOR_LEVEL_UNLOCKED else Theme.COLOR_LEVEL_LOCKED))
                if (!isUnlocked) alpha = 0.4f
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

            // Cat thumbnail (cycle through available cat drawables)
            val dp48 = (48 * resources.displayMetrics.density).toInt()
            val catThumb = ImageView(this).apply {
                setImageResource(catDrawables[(levelId - 1) % catDrawables.size])
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = LinearLayout.LayoutParams(dp48, dp48)
                lp.marginEnd = 16
                layoutParams = lp
            }
            row.addView(catThumb)

            // Level label
            val levelLabel = TextView(this).apply {
                text = "Level $levelId"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isUnlocked) Theme.COLOR_PRIMARY_TEXT else Theme.COLOR_MUTED_TEXT))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            row.addView(levelLabel)

            // Stars row
            val dp24 = (24 * resources.displayMetrics.density).toInt()
            for (i in 1..3) {
                val starImg = ImageView(this).apply {
                    setImageResource(if (i <= stars) R.drawable.star_full else R.drawable.star_empty)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val lp = LinearLayout.LayoutParams(dp24, dp24)
                    lp.marginStart = 4
                    layoutParams = lp
                }
                row.addView(starImg)
            }

            contentLayout.addView(row)
        }

        val backButton = makeButton("Back", Theme.COLOR_BUTTON_BACK)
        backButton.setOnClickListener { showMainMenu() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 32
        backButton.layoutParams = lp
        contentLayout.addView(backButton)
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
        if (showingLevelSelect) showLevelSelect()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
