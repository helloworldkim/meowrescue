package com.meowrescue.game.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meowrescue.game.data.UserProgress

class StagePageAdapter(
    private val maxCompleted: Int,
    private val progressList: List<UserProgress?>,
    private val onLevelClick: (Int) -> Unit,
    private val totalPages: Int,
    private val totalLevels: Int,
    private val levelsPerPage: Int,
    private val density: Float
) : RecyclerView.Adapter<StagePageAdapter.PageVH>() {

    private val animatedPages = mutableSetOf<Int>()

    class PageVH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override fun getItemCount() = totalPages

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

        val dp = density
        val gridRecycler = RecyclerView(holder.container.context).apply {
            layoutManager = GridLayoutManager(holder.container.context, 4)
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

        gridRecycler.adapter = StageGridAdapter(
            page = position,
            maxCompleted = maxCompleted,
            progressList = progressList,
            onLevelClick = onLevelClick,
            animateEntrance = shouldAnimate,
            totalLevels = totalLevels,
            levelsPerPage = levelsPerPage,
            density = density
        )
        holder.container.addView(gridRecycler)
    }
}
