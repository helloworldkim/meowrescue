package com.meowrescue.game.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.data.UserProgress

class StageGridAdapter(
    private val page: Int,
    private val maxCompleted: Int,
    private val progressList: List<UserProgress?>,
    private val onLevelClick: (Int) -> Unit,
    private val animateEntrance: Boolean,
    private val totalLevels: Int,
    private val levelsPerPage: Int,
    private val density: Float
) : RecyclerView.Adapter<StageGridAdapter.VH>() {

    private val catUnlockMap: Map<Int, GameRepository.CatDefinition> = GameRepository.CAT_DEFINITIONS
        .filter { it.requiredStage > 1 }
        .associateBy { it.requiredStage }

    private val offset = page * levelsPerPage
    private val count = minOf(levelsPerPage, totalLevels - offset)

    inner class VH(val cell: FrameLayout) : RecyclerView.ViewHolder(cell)

    override fun getItemCount() = count

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val dp = density
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
        val dp = density
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

    private fun wrapWithRipple(content: GradientDrawable): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.parseColor("#40FF7043"))
        return RippleDrawable(rippleColor, content, null)
    }
}
