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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
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
        private const val LEVELS_PER_PAGE = 30
        private val TOTAL_PAGES get() = (TOTAL_LEVELS + LEVELS_PER_PAGE - 1) / LEVELS_PER_PAGE
    }

    private lateinit var repository: GameRepository
    private var bannerAd: AdView? = null
    private lateinit var pagerRecycler: RecyclerView
    private lateinit var pagerSnapHelper: PagerSnapHelper
    private lateinit var pageTitle: TextView
    private lateinit var prevArrow: TextView
    private lateinit var nextArrow: TextView

    private var currentPage = 0

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
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        val backButton = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                SoundManager.playButtonTap()
                finish()
            }
            isClickable = true
            isFocusable = true
        }

        // Page navigation: [<] [1 - 20] [>]
        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        prevArrow = TextView(this).apply {
            text = "<"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SoundManager.playButtonTap()
                if (currentPage > 0) {
                    pagerRecycler.smoothScrollToPosition(currentPage - 1)
                }
            }
        }

        pageTitle = TextView(this).apply {
            text = "1 - 20"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = (100 * dp).toInt()
        }

        nextArrow = TextView(this).apply {
            text = ">"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SoundManager.playButtonTap()
                if (currentPage < TOTAL_PAGES - 1) {
                    pagerRecycler.smoothScrollToPosition(currentPage + 1)
                }
            }
        }

        navContainer.addView(prevArrow)
        navContainer.addView(pageTitle)
        navContainer.addView(nextArrow)

        // Spacer to balance back button
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), 1)
        }

        topBar.addView(backButton)
        topBar.addView(navContainer)
        topBar.addView(topSpacer)
        rootLayout.addView(topBar)

        // --- Pager RecyclerView (horizontal, snapping pages) ---
        pagerSnapHelper = PagerSnapHelper()
        pagerRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@StageSelectActivity, LinearLayoutManager.HORIZONTAL, false
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            // Disable nested scrolling for smoother paging
            isNestedScrollingEnabled = false
        }
        pagerSnapHelper.attachToRecyclerView(pagerRecycler)

        // Listen for page changes
        pagerRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val snapView = pagerSnapHelper.findSnapView(layoutManager)
                    if (snapView != null) {
                        val newPage = layoutManager.getPosition(snapView)
                        if (newPage != currentPage) {
                            currentPage = newPage
                            updatePageIndicator()
                        }
                    }
                }
            }
        })

        rootLayout.addView(pagerRecycler)

        // --- Banner ad ---
        bannerAd = AdManager.createBannerAd(this)
        rootLayout.addView(bannerAd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        setContentView(rootLayout)

        loadStageData()
    }

    private fun updatePageIndicator() {
        val start = currentPage * LEVELS_PER_PAGE + 1
        val end = minOf(start + LEVELS_PER_PAGE - 1, TOTAL_LEVELS)
        pageTitle.text = "$start - $end"

        prevArrow.alpha = if (currentPage > 0) 1f else 0.3f
        prevArrow.isClickable = currentPage > 0
        nextArrow.alpha = if (currentPage < TOTAL_PAGES - 1) 1f else 0.3f
        nextArrow.isClickable = currentPage < TOTAL_PAGES - 1
    }

    private fun loadStageData() {
        lifecycleScope.launch {
            val maxCompleted = repository.getMaxCompletedLevel()
            val allProgress = withContext(Dispatchers.IO) {
                (1..TOTAL_LEVELS).map { levelId ->
                    repository.getProgress(levelId)
                }
            }

            // Set pager adapter
            pagerRecycler.adapter = PageAdapter(maxCompleted, allProgress) { levelId ->
                SoundManager.playButtonTap()
                val intent = Intent(this@StageSelectActivity, PuzzleActivity::class.java)
                intent.putExtra("stage", levelId)
                startActivity(intent)
            }

            // Auto-scroll to the page of the current playable stage
            val targetPage = (maxCompleted / LEVELS_PER_PAGE).coerceIn(0, TOTAL_PAGES - 1)
            if (targetPage != currentPage) {
                currentPage = targetPage
                pagerRecycler.scrollToPosition(currentPage)
            }
            updatePageIndicator()
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
        if (::pagerRecycler.isInitialized) loadStageData()
    }

    override fun onPause() {
        super.onPause()
        bannerAd?.pause()
    }

    override fun onDestroy() {
        bannerAd?.destroy()
        super.onDestroy()
    }

    // ---- Page Adapter (each item = full-width page with a grid of stages) ----

    private inner class PageAdapter(
        private val maxCompleted: Int,
        private val progressList: List<UserProgress?>,
        private val onLevelClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PageAdapter.PageVH>() {

        inner class PageVH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun getItemCount() = TOTAL_PAGES

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val container = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    parent.width,    // full screen width for snap paging
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            }
            return PageVH(container)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.container.removeAllViews()

            val dp = resources.displayMetrics.density
            val gridRecycler = RecyclerView(holder.container.context).apply {
                layoutManager = GridLayoutManager(this@StageSelectActivity, 4)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(
                    (12 * dp).toInt(), (12 * dp).toInt(),
                    (12 * dp).toInt(), (12 * dp).toInt()
                )
                clipToPadding = false
                isNestedScrollingEnabled = false
            }

            gridRecycler.adapter = StageGridAdapter(position, maxCompleted, progressList, onLevelClick)
            holder.container.addView(gridRecycler)
        }
    }

    // ---- Stage Grid Adapter (20 stages per page) ----

    private inner class StageGridAdapter(
        private val page: Int,
        private val maxCompleted: Int,
        private val progressList: List<UserProgress?>,
        private val onLevelClick: (Int) -> Unit
    ) : RecyclerView.Adapter<StageGridAdapter.VH>() {

        private val catUnlockMap: Map<Int, GameRepository.CatDefinition> = GameRepository.CAT_DEFINITIONS
            .filter { it.requiredStage > 1 }
            .associateBy { it.requiredStage }

        private val offset = page * LEVELS_PER_PAGE
        private val count = minOf(LEVELS_PER_PAGE, TOTAL_LEVELS - offset)

        inner class VH(val cell: FrameLayout) : RecyclerView.ViewHolder(cell)

        override fun getItemCount() = count

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = resources.displayMetrics.density
            val size = (80 * dp).toInt()
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

        override fun onBindViewHolder(holder: VH, position: Int) {
            val levelId = offset + position + 1
            val dp = resources.displayMetrics.density
            val progress = progressList.getOrNull(offset + position)
            // DEV: all stages unlocked for testing
            val isUnlocked = true // levelId == 1 || levelId <= maxCompleted + 1
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

            // Cat unlock thumbnail preview
            val catDef = catUnlockMap[levelId]
            if (catDef != null) {
                val thumbSize = (28 * dp).toInt()
                val catThumb = ImageView(holder.cell.context).apply {
                    setImageResource(catDef.drawableRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize,
                        Gravity.TOP or Gravity.END
                    ).apply {
                        topMargin = (3 * dp).toInt()
                        marginEnd = (3 * dp).toInt()
                    }
                    if (!isCompleted) {
                        colorFilter = android.graphics.PorterDuffColorFilter(
                            0xFF444444.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY
                        )
                        alpha = 0.6f
                    }
                }
                holder.cell.addView(catThumb)
            }

            if (isUnlocked) {
                if (isCompleted && stars > 0) {
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
