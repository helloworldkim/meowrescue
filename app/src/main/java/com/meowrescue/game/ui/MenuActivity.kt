package com.meowrescue.game.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.update.UpdateManager
import com.meowrescue.game.util.SoundManager

class MenuActivity : AppCompatActivity() {

    private lateinit var repository: GameRepository
    private lateinit var updateManager: UpdateManager
    private var bannerAd: AdView? = null
    private lateinit var soundButton: Button
    private lateinit var endlessBtn: Button
    private lateinit var catImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        repository = GameRepository(this)
        updateManager = UpdateManager(this)
        updateManager.checkForUpdate()

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

        // Main area: FrameLayout holding scroll + floating sound toggle
        val mainFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val rootScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((48 * dp).toInt(), (32 * dp).toInt(), (48 * dp).toInt(), (24 * dp).toInt())
        }
        rootScroll.addView(contentLayout)
        mainFrame.addView(rootScroll)
        rootLayout.addView(mainFrame)

        // Banner ad at bottom
        bannerAd = AdManager.createBannerAd(this)
        val bannerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        rootLayout.addView(bannerAd, bannerParams)

        setContentView(rootLayout)

        // Cat mascot image (compact)
        catImage = ImageView(this).apply {
            setImageResource(repository.getSelectedCatDrawable())
            scaleType = ImageView.ScaleType.FIT_CENTER
            val w = (120 * dp).toInt()
            val h = (160 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (16 * dp).toInt()
                bottomMargin = (16 * dp).toInt()
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
            ).apply { bottomMargin = (20 * dp).toInt() }
        }
        contentLayout.addView(subtitle)

        // Play button
        val playButton = makeButton("▶  Play", Theme.COLOR_CORAL)
        playButton.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, StageSelectActivity::class.java))
        }
        contentLayout.addView(playButton)

        // Collection button
        val collectionBtn = makeButton("\uD83D\uDC31  Collection", Theme.COLOR_BUTTON_COLLECTION)
        collectionBtn.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        contentLayout.addView(collectionBtn)

        // Endless Mode button (hidden until 200 stages cleared)
        endlessBtn = makeButton("\uD83D\uDD04  Endless Mode", Theme.COLOR_ENDLESS_PURPLE)
        endlessBtn.visibility = View.GONE
        endlessBtn.setOnClickListener {
            SoundManager.playButtonTap()
            val intent = Intent(this, PuzzleActivity::class.java)
            intent.putExtra("endless", true)
            startActivity(intent)
        }
        contentLayout.addView(endlessBtn)

        // Slingshot minigame button
        val launchBtn = makeButton("\uD83D\uDE80  Cat Launch", Theme.COLOR_CORAL)
        launchBtn.setOnClickListener {
            SoundManager.playButtonTap()
            startActivity(Intent(this, LaunchGameActivity::class.java))
        }
        contentLayout.addView(launchBtn)

        // Floating sound toggle (top-right corner)
        soundButton = Button(this).apply {
            text = soundIcon()
            textSize = 20f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(Theme.COLOR_TEAL))
                cornerRadius = 999 * dp
            }
            elevation = 4 * dp
            val size = (44 * dp).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (12 * dp).toInt()
                marginEnd = (12 * dp).toInt()
            }
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                SoundManager.playButtonTap()
                val newEnabled = !repository.isSoundEnabled()
                repository.setSoundEnabled(newEnabled)
                SoundManager.setSoundEnabled(newEnabled)
                if (newEnabled) SoundManager.playBgm("menu")
                soundButton.text = soundIcon()
            }
        }
        mainFrame.addView(soundButton)
    }

    private fun soundIcon(): String {
        return if (repository.isSoundEnabled()) "🔊" else "🔇"
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateManager.handleUpdateResult(requestCode, resultCode)
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
        updateManager.onResume()
        if (repository.isSoundEnabled()) SoundManager.playBgm("menu")
        // Refresh sound button label in case state changed
        if (::soundButton.isInitialized) soundButton.text = soundIcon()
        // Refresh cat mascot in case selection changed
        if (::catImage.isInitialized) catImage.setImageResource(repository.getSelectedCatDrawable())
        // Show endless button only if 200 stages cleared
        if (::endlessBtn.isInitialized) {
            lifecycleScope.launch {
                val maxLevel = repository.getMaxCompletedLevel()
                endlessBtn.visibility = if (maxLevel >= 200) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        bannerAd?.pause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        bannerAd?.destroy()
        updateManager.onDestroy()
        SoundManager.stopBgm()
        super.onDestroy()
    }
}
