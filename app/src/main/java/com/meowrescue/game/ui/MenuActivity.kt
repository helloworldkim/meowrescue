package com.meowrescue.game.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#1A1A2E"),
                    Color.parseColor("#16213E"),
                    Color.parseColor("#0F3460")
                )
            )
            background = gradient
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
            val dp = resources.displayMetrics.density
            val dpW = (180 * dp).toInt()
            val dpH = (240 * dp).toInt()
            val lp = LinearLayout.LayoutParams(dpW, dpH)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = (24 * dp).toInt()
            lp.bottomMargin = (40 * dp).toInt()
            layoutParams = lp
        }
        contentLayout.addView(catImage)

        // Title
        val title = TextView(this).apply {
            text = "Meow Rescue"
            textSize = 52f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.COLOR_GOLD)
            gravity = Gravity.CENTER
            setShadowLayer(8f, 2f, 2f, Color.parseColor("#AA000000"))
            val dp = resources.displayMetrics.density
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (64 * dp).toInt()
            layoutParams = lp
        }
        contentLayout.addView(title)

        // Start Run button
        val startButton = makeButton("Start Run", 0xFF2ECC71.toInt(), 0xFF27AE60.toInt())
        startButton.setOnClickListener {
            SoundManager.playButtonTap()
            val intent = Intent(this, BattleActivity::class.java)
            intent.putExtra("chapter", 1)
            intent.putExtra("stage", 1)
            startActivity(intent)
        }
        contentLayout.addView(startButton)

        val collectionButton = makeButton("Collection", 0xFF9B59B6.toInt(), 0xFF8E44AD.toInt())
        collectionButton.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        contentLayout.addView(collectionButton)
    }

    private fun makeButton(text: String, colorTop: Int, colorBottom: Int): Button {
        return Button(this).apply {
            this.text = text
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 1f, 1f, Color.parseColor("#66000000"))
            val shape = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorTop, colorBottom)
            ).apply {
                cornerRadius = 16f * resources.displayMetrics.density
            }
            background = shape
            elevation = 8f * resources.displayMetrics.density
            val dp = resources.displayMetrics.density
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (16 * dp).toInt()
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
            setPadding((48 * dp).toInt(), (20 * dp).toInt(), (48 * dp).toInt(), (20 * dp).toInt())
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
