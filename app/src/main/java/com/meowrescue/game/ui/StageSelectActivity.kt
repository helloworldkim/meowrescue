package com.meowrescue.game.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.puzzle.model.WorldTheme
import com.meowrescue.game.puzzle.ui.PuzzleActivity
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

        // Show world name alongside page range
        val worldTheme = WorldTheme.forStage(start)
        val worldName = worldTheme.name

        // Crossfade page title
        pageTitle.animate().alpha(0f).setDuration(100).withEndAction {
            pageTitle.text = "$worldName  $start-$end"
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
            pagerRecycler.adapter = StagePageAdapter(
                maxCompleted = maxCompleted,
                progressList = allProgress,
                onLevelClick = { levelId ->
                    SoundManager.playButtonTap()
                    val intent = Intent(this@StageSelectActivity, PuzzleActivity::class.java)
                    intent.putExtra("stage", levelId)
                    startActivity(intent)
                },
                totalPages = TOTAL_PAGES,
                totalLevels = TOTAL_LEVELS,
                levelsPerPage = LEVELS_PER_PAGE,
                density = resources.displayMetrics.density
            )

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

}
