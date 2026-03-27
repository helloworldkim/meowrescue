package com.meowrescue.game.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.data.UserProgress
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StageSelectActivity : AppCompatActivity() {

    companion object {
        const val TOTAL_LEVELS = 200
    }

    private lateinit var repository: GameRepository
    private var bannerAd: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // --- Top bar ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(Theme.COLOR_CORAL))
            elevation = 4 * dp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }

        val backButton = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                SoundManager.playButtonTap()
                finish()
            }
            isClickable = true
            isFocusable = true
        }

        val topTitle = TextView(this).apply {
            text = "Levels"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }

        // Spacer to balance back button
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), 1)
        }

        topBar.addView(backButton)
        topBar.addView(topTitle)
        topBar.addView(topSpacer)
        rootLayout.addView(topBar)

        // --- RecyclerView ---
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@StageSelectActivity, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            clipToPadding = false
        }
        rootLayout.addView(recycler)

        // --- Banner ad ---
        bannerAd = AdManager.createBannerAd(this)
        rootLayout.addView(bannerAd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        setContentView(rootLayout)

        // Load progress then bind adapter
        lifecycleScope.launch {
            val maxCompleted = repository.getMaxCompletedLevel()
            val allProgress = withContext(Dispatchers.IO) {
                (1..TOTAL_LEVELS).map { levelId ->
                    repository.getProgress(levelId)
                }
            }
            recycler.adapter = StageAdapter(maxCompleted, allProgress) { levelId ->
                SoundManager.playButtonTap()
                val intent = Intent(this@StageSelectActivity, PuzzleActivity::class.java)
                intent.putExtra("stage", levelId)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
    }

    override fun onPause() {
        super.onPause()
        bannerAd?.pause()
    }

    override fun onDestroy() {
        bannerAd?.destroy()
        super.onDestroy()
    }

    // ---- Adapter ----

    private inner class StageAdapter(
        private val maxCompleted: Int,
        private val progressList: List<UserProgress?>,
        private val onLevelClick: (Int) -> Unit
    ) : RecyclerView.Adapter<StageAdapter.VH>() {

        inner class VH(val cell: FrameLayout) : RecyclerView.ViewHolder(cell)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = resources.displayMetrics.density
            val size = (70 * dp).toInt()
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    size
                ).apply {
                    val margin = (6 * dp).toInt()
                    setMargins(margin, margin, margin, margin)
                }
            }
            return VH(cell)
        }

        override fun getItemCount() = TOTAL_LEVELS

        override fun onBindViewHolder(holder: VH, position: Int) {
            val levelId = position + 1
            val dp = resources.displayMetrics.density
            val progress = progressList.getOrNull(position)
            // Level 1 always unlocked; level N unlocked if level N-1 completed
            val isUnlocked = levelId == 1 || levelId <= maxCompleted + 1
            val isCompleted = progress != null && progress.completed
            val stars = progress?.stars ?: 0

            holder.cell.removeAllViews()

            val bgColor = when {
                !isUnlocked -> Color.parseColor(Theme.COLOR_LOCKED_GRAY)
                isCompleted -> Color.parseColor(Theme.COLOR_LEVEL_COMPLETED_BG)
                else -> Color.parseColor(Theme.COLOR_LEVEL_PLAYABLE_BG)
            }

            holder.cell.background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 16 * dp
            }
            holder.cell.elevation = 3 * dp

            // Level number
            val numText = TextView(holder.cell.context).apply {
                text = levelId.toString()
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    if (isUnlocked) Color.parseColor(Theme.COLOR_WARM_BROWN)
                    else Color.parseColor("#888888")
                )
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { topMargin = (8 * dp).toInt() }
            }
            holder.cell.addView(numText)

            if (isUnlocked) {
                if (isCompleted && stars > 0) {
                    // Stars row
                    val starsText = TextView(holder.cell.context).apply {
                        text = "★".repeat(stars) + "☆".repeat(3 - stars)
                        textSize = 11f
                        setTextColor(Color.parseColor(Theme.COLOR_STAR_GOLD))
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply { bottomMargin = (8 * dp).toInt() }
                    }
                    holder.cell.addView(starsText)
                }

                holder.cell.setOnClickListener { onLevelClick(levelId) }
            } else {
                // Lock icon overlay
                val lockText = TextView(holder.cell.context).apply {
                    text = "🔒"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    ).apply { bottomMargin = (6 * dp).toInt() }
                }
                holder.cell.addView(lockText)
                holder.cell.setOnClickListener(null)
                holder.cell.isClickable = false
            }
        }
    }
}
