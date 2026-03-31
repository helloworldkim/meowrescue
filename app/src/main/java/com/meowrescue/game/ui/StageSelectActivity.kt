package com.meowrescue.game.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
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
    private lateinit var prevArrow: ImageView
    private lateinit var nextArrow: ImageView
    private lateinit var dotContainer: LinearLayout

    private var currentPage = 0

    private fun wrapWithRipple(content: GradientDrawable): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.parseColor("#40FF7043"))
        return RippleDrawable(rippleColor, content, null)
    }

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
            // Ripple on back button
            val backBg = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 8 * dp
            }
            background = wrapWithRipple(backBg)
        }

        // Page navigation: [<] [1 - 30] [>]
        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val arrowSize = (36 * dp).toInt()

        prevArrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_left)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val arrowBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = wrapWithRipple(arrowBg)
            layoutParams = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                marginEnd = (8 * dp).toInt()
            }
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
            text = "1 - 30"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = (100 * dp).toInt()
        }

        nextArrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val arrowBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = wrapWithRipple(arrowBg)
            layoutParams = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                marginStart = (8 * dp).toInt()
            }
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

        // --- Dot page indicator ---
        dotContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            }
        }
        buildDotIndicators()
        rootLayout.addView(dotContainer)

        // --- Banner ad ---
        bannerAd = AdManager.createBannerAd(this)
        rootLayout.addView(bannerAd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        setContentView(rootLayout)

        loadStageData()
    }

    private fun buildDotIndicators() {
        val dp = resources.displayMetrics.density
        dotContainer.removeAllViews()
        for (i in 0 until TOTAL_PAGES) {
            val isActive = i == currentPage
            val dotSize = if (isActive) (8 * dp).toInt() else (6 * dp).toInt()
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (isActive) Color.parseColor(Theme.COLOR_CORAL)
                        else Color.parseColor("#55FFFFFF")
                    )
                }
            }
            dotContainer.addView(dot, LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginStart = (3 * dp).toInt()
                marginEnd = (3 * dp).toInt()
                gravity = Gravity.CENTER_VERTICAL
            })
        }
    }

    private fun updatePageIndicator() {
        val start = currentPage * LEVELS_PER_PAGE + 1
        val end = minOf(start + LEVELS_PER_PAGE - 1, TOTAL_LEVELS)

        // Crossfade page title
        pageTitle.animate().alpha(0f).setDuration(100).withEndAction {
            pageTitle.text = "$start - $end"
            pageTitle.animate().alpha(1f).setDuration(150).start()
        }.start()

        prevArrow.alpha = if (currentPage > 0) 1f else 0.3f
        prevArrow.isClickable = currentPage > 0
        nextArrow.alpha = if (currentPage < TOTAL_PAGES - 1) 1f else 0.3f
        nextArrow.isClickable = currentPage < TOTAL_PAGES - 1

        // Update dot indicators
        buildDotIndicators()
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

        private val animatedPages = mutableSetOf<Int>()

        inner class PageVH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun getItemCount() = TOTAL_PAGES

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val container = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    parent.width,
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

            val shouldAnimate = !animatedPages.contains(position)
            if (shouldAnimate) animatedPages.add(position)

            gridRecycler.adapter = StageGridAdapter(position, maxCompleted, progressList, onLevelClick, shouldAnimate)
            holder.container.addView(gridRecycler)
        }
    }

    // ---- Stage Grid Adapter (30 stages per page) ----

    private inner class StageGridAdapter(
        private val page: Int,
        private val maxCompleted: Int,
        private val progressList: List<UserProgress?>,
        private val onLevelClick: (Int) -> Unit,
        private val animateEntrance: Boolean
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

            val bgDrawable = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 16 * dp
            }
            holder.cell.background = wrapWithRipple(bgDrawable)
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
                // Star images instead of unicode text
                if (isCompleted && stars > 0) {
                    val starSz = (12 * dp).toInt()
                    val gap = (2 * dp).toInt()
                    val starRow = LinearLayout(holder.cell.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply { bottomMargin = (8 * dp).toInt() }
                    }
                    for (i in 1..3) {
                        val iv = ImageView(holder.cell.context).apply {
                            setImageResource(
                                if (i <= stars) R.drawable.star_full else R.drawable.star_empty
                            )
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        starRow.addView(iv, LinearLayout.LayoutParams(starSz, starSz).apply {
                            marginEnd = gap
                        })
                    }
                    holder.cell.addView(starRow)
                }

                // Scale animation on touch
                holder.cell.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    false // don't consume, let onClick fire
                }

                holder.cell.setOnClickListener { onLevelClick(levelId) }
            } else {
                val lockText = TextView(holder.cell.context).apply {
                    text = "\uD83D\uDD12"
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

            // Stagger entrance animation
            if (animateEntrance) {
                holder.cell.alpha = 0f
                holder.cell.translationY = 30 * dp
                holder.cell.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .setStartDelay((position * 30).toLong())
                    .start()
            }
        }
    }
}
