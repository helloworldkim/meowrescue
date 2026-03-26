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
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.util.SoundManager

class MenuActivity : AppCompatActivity() {

    private lateinit var repository: GameRepository
    private lateinit var contentLayout: LinearLayout
    private var bannerAd: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        repository = GameRepository(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Theme.COLOR_BACKGROUND))
        }

        val rootScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 80, 48, 80)
        }
        rootScroll.addView(contentLayout)
        rootLayout.addView(rootScroll)

        // Banner ad at bottom
        bannerAd = AdManager.createBannerAd(this)
        val bannerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        rootLayout.addView(bannerAd, bannerParams)

        setContentView(rootLayout)

        showMainMenu()
    }

    private fun showMainMenu() {
        contentLayout.removeAllViews()

        // Cat mascot image
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

        // Start Run button
        val startButton = makeButton("Start Run", Theme.COLOR_LEVEL_UNLOCKED)
        startButton.setOnClickListener {
            SoundManager.playButtonTap()
            val intent = Intent(this, BattleActivity::class.java)
            intent.putExtra("chapter", 1)
            intent.putExtra("stage", 1)
            startActivity(intent)
        }
        contentLayout.addView(startButton)

        val collectionButton = makeButton("Collection", Theme.COLOR_BUTTON_COLLECTION)
        collectionButton.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        contentLayout.addView(collectionButton)
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
        bannerAd?.resume()
        SoundManager.playBgm("menu")
    }

    override fun onPause() {
        super.onPause()
        bannerAd?.pause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        bannerAd?.destroy()
        super.onDestroy()
        SoundManager.stopBgm()
    }
}
