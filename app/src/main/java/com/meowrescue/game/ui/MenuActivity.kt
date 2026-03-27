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
    private var bannerAd: AdView? = null
    private lateinit var soundButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        repository = GameRepository(this)

        val dp = resources.displayMetrics.density

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor(Theme.COLOR_CREAM),
                    Color.parseColor(Theme.COLOR_LAVENDER)
                )
            )
        }

        val rootScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((48 * dp).toInt(), (80 * dp).toInt(), (48 * dp).toInt(), (80 * dp).toInt())
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

        // Cat mascot image
        val catImage = ImageView(this).apply {
            setImageResource(R.drawable.cat_1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val w = (180 * dp).toInt()
            val h = (240 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (24 * dp).toInt()
                bottomMargin = (32 * dp).toInt()
            }
        }
        contentLayout.addView(catImage)

        // Title
        val title = TextView(this).apply {
            text = "Meow Rescue"
            textSize = 48f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(Theme.COLOR_WARM_BROWN))
            gravity = Gravity.CENTER
            setShadowLayer(4f, 1f, 1f, Color.parseColor("#33000000"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * dp).toInt() }
        }
        contentLayout.addView(title)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Slide & Save!"
            textSize = 18f
            setTextColor(Color.parseColor(Theme.COLOR_WARM_BROWN).let {
                Color.argb(180, Color.red(it), Color.green(it), Color.blue(it))
            })
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (48 * dp).toInt() }
        }
        contentLayout.addView(subtitle)

        // Play button
        val playButton = makeButton("▶  Play", Theme.COLOR_CORAL)
        playButton.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, StageSelectActivity::class.java))
        }
        contentLayout.addView(playButton)

        // Sound toggle button
        soundButton = makeButton(soundLabel(), Theme.COLOR_TEAL)
        soundButton.setOnClickListener {
            SoundManager.playButtonTap()
            val newEnabled = !repository.isSoundEnabled()
            repository.setSoundEnabled(newEnabled)
            SoundManager.setSoundEnabled(newEnabled)
            if (newEnabled) SoundManager.playBgm("menu")
            soundButton.text = soundLabel()
        }
        contentLayout.addView(soundButton)
    }

    private fun soundLabel(): String {
        return if (repository.isSoundEnabled()) "🔊  Sound: ON" else "🔇  Sound: OFF"
    }

    private fun makeButton(text: String, colorHex: String): Button {
        val dp = resources.displayMetrics.density
        return Button(this).apply {
            this.text = text
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = 24 * dp
            }
            elevation = 6 * dp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
            setPadding((48 * dp).toInt(), (18 * dp).toInt(), (48 * dp).toInt(), (18 * dp).toInt())
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
        if (repository.isSoundEnabled()) SoundManager.playBgm("menu")
        // Refresh sound button label in case state changed
        if (::soundButton.isInitialized) soundButton.text = soundLabel()
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
