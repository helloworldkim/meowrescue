package com.meowrescue.game.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.util.SoundManager

object PuzzleOverlays {

    fun buildCongratsOverlay(
        context: Context,
        cat: GameRepository.CatDefinition,
        dp: Float,
        frameRoot: FrameLayout,
        onDismiss: () -> Unit
    ): FrameLayout {
        val overlay = FrameLayout(context)
        overlay.setBackgroundColor(0xAA000000.toInt())

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * dp).toInt(), (28 * dp).toInt(),
                (24 * dp).toInt(), (28 * dp).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Theme.INT_BG_CREAM)
                cornerRadius = 24 * dp
            }
            elevation = 8 * dp
        }

        val title = TextView(context).apply {
            text = "New Cat Unlocked!"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.INT_CORAL)
            gravity = Gravity.CENTER
        }
        panel.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (16 * dp).toInt() })

        val catImage = ImageView(context).apply {
            setImageResource(cat.drawableRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val imgSize = (100 * dp).toInt()
        panel.addView(catImage, LinearLayout.LayoutParams(imgSize, imgSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = (12 * dp).toInt()
        })

        val nameTv = TextView(context).apply {
            text = cat.name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.INT_WARM_BROWN)
            gravity = Gravity.CENTER
        }
        panel.addView(nameTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (20 * dp).toInt() })

        val okBtn = makeDialogButton(context, "OK", Theme.INT_CORAL, dp) {
            SoundManager.playButtonTap()
            frameRoot.removeView(overlay)
            onDismiss()
        }
        panel.addView(okBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * dp).toInt()
        ))

        overlay.addView(panel, FrameLayout.LayoutParams(
            (280 * dp).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        return overlay
    }

    fun buildLoadingOverlay(context: Context, catDrawableRes: Int, dp: Float): FrameLayout {
        val overlay = FrameLayout(context)
        overlay.setBackgroundColor(Theme.INT_BG_CREAM)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val catImage = ImageView(context).apply {
            setImageResource(catDrawableRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val imgSize = (100 * dp).toInt()
        container.addView(catImage, LinearLayout.LayoutParams(imgSize, imgSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = (16 * dp).toInt()
        })

        val loadingText = TextView(context).apply {
            text = "Preparing puzzle..."
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.INT_WARM_BROWN)
            gravity = Gravity.CENTER
        }
        container.addView(loadingText)

        overlay.addView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        return overlay
    }

    fun buildPauseOverlay(
        context: Context,
        dp: Float,
        onResume: () -> Unit,
        onRestart: () -> Unit,
        onQuit: () -> Unit
    ): FrameLayout {
        val overlay = FrameLayout(context)
        overlay.setBackgroundColor(0xCC000000.toInt())

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * dp).toInt(), (28 * dp).toInt(),
                (24 * dp).toInt(), (28 * dp).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Theme.INT_BG_CREAM)
                cornerRadius = 24 * dp
            }
            elevation = 8 * dp
        }

        val title = TextView(context).apply {
            text = "Paused"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Theme.INT_WARM_BROWN)
            gravity = Gravity.CENTER
        }
        panel.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (20 * dp).toInt() })

        panel.addView(
            makeDialogButton(context, "Resume", Theme.INT_CORAL, dp, onResume),
            buttonLayoutParams(dp)
        )
        panel.addView(
            makeDialogButton(context, "Restart", Theme.INT_TEAL, dp, onRestart),
            buttonLayoutParams(dp)
        )
        panel.addView(
            makeDialogButton(context, "Quit", Theme.INT_GRAY, dp, onQuit),
            buttonLayoutParams(dp)
        )

        overlay.addView(panel, FrameLayout.LayoutParams(
            (260 * dp).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        return overlay
    }

    private fun buttonLayoutParams(dp: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * dp).toInt()
        ).also { it.topMargin = (10 * dp).toInt() }
    }

    private fun makeDialogButton(
        context: Context,
        label: String,
        color: Int,
        dp: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12 * dp
            }
            elevation = 4 * dp
            setOnClickListener { onClick() }
        }
    }
}
