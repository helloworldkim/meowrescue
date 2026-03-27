package com.meowrescue.game.ui

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.launch

class CollectionActivity : AppCompatActivity() {

    data class CatEntry(
        val id: Int,
        val drawableRes: Int,
        val name: String,
        val requiredStage: Int,
        val unlocked: Boolean,
        val selected: Boolean
    )

    private lateinit var repository: GameRepository
    private var adapter: CatAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        repository = GameRepository(this)

        lifecycleScope.launch {
            val maxLevel = repository.getMaxCompletedLevel()
            val selectedId = repository.getSelectedCatId()
            buildUi(maxLevel, selectedId)
        }
    }

    private fun buildUi(maxLevel: Int, selectedId: Int) {
        val density = resources.displayMetrics.density
        val allCats = GameRepository.CAT_DEFINITIONS.map { def ->
            CatEntry(
                id = def.id,
                drawableRes = def.drawableRes,
                name = def.name,
                requiredStage = def.requiredStage,
                unlocked = maxLevel >= def.requiredStage,
                selected = def.id == selectedId
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Theme.COLOR_CREAM))
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(Theme.COLOR_TOOLBAR))
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }

        val backBtn = TextView(this).apply {
            text = "< Back"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener {
                SoundManager.playButtonTap()
                finish()
            }
        }
        toolbar.addView(backBtn)

        val titleTv = TextView(this).apply {
            text = "Cat Collection"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = (8 * density).toInt()
            layoutParams = lp
        }
        toolbar.addView(titleTv)

        // Unlocked count
        val unlockedCount = allCats.count { it.unlocked }
        val countTv = TextView(this).apply {
            text = "$unlockedCount/20"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        toolbar.addView(countTv)
        root.addView(toolbar)

        // RecyclerView with 4 columns
        adapter = CatAdapter(allCats.toMutableList())
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@CollectionActivity, 4)
            adapter = this@CollectionActivity.adapter
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    inner class CatAdapter(private val cats: MutableList<CatEntry>) :
        RecyclerView.Adapter<CatAdapter.CatViewHolder>() {

        inner class CatViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                layoutParams = lp
            }
            return CatViewHolder(card)
        }

        override fun onBindViewHolder(holder: CatViewHolder, position: Int) {
            val cat = cats[position]
            val ctx = holder.card.context
            val density = ctx.resources.displayMetrics.density
            holder.card.removeAllViews()

            // Card background
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12 * density
                if (cat.selected && cat.unlocked) {
                    setStroke((3 * density).toInt(), Color.parseColor(Theme.COLOR_CORAL))
                }
            }
            holder.card.background = bg
            holder.card.elevation = 2 * density

            // Cat image
            val imgSize = (64 * density).toInt()
            val catImage = ImageView(ctx).apply {
                setImageResource(cat.drawableRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            if (!cat.unlocked) {
                val matrix = ColorMatrix()
                matrix.setSaturation(0f)
                catImage.colorFilter = ColorMatrixColorFilter(matrix)
                catImage.alpha = 0.3f
            }
            holder.card.addView(catImage)

            // Name or lock text
            val nameTv = TextView(ctx).apply {
                textSize = 11f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (4 * density).toInt()
                layoutParams = lp
            }

            if (cat.unlocked) {
                nameTv.text = cat.name
                nameTv.setTextColor(Color.parseColor(Theme.COLOR_WARM_BROWN))
                nameTv.setTypeface(nameTv.typeface, Typeface.BOLD)
            } else {
                nameTv.text = "Stage ${cat.requiredStage}\n클리어 시 해금"
                nameTv.setTextColor(Color.parseColor(Theme.COLOR_MUTED_TEXT))
                nameTv.textSize = 9f
            }
            holder.card.addView(nameTv)

            // Selected indicator
            if (cat.selected && cat.unlocked) {
                val selectedTv = TextView(ctx).apply {
                    text = "SELECTED"
                    textSize = 8f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    val tvBg = GradientDrawable().apply {
                        setColor(Color.parseColor(Theme.COLOR_CORAL))
                        cornerRadius = 6 * density
                    }
                    background = tvBg
                    setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = (4 * density).toInt()
                    lp.gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = lp
                }
                holder.card.addView(selectedTv)
            }

            // Click handler: select unlocked cat
            holder.card.setOnClickListener {
                if (cat.unlocked) {
                    SoundManager.playButtonTap()
                    repository.setSelectedCatId(cat.id)
                    // Update selection state
                    for (i in cats.indices) {
                        cats[i] = cats[i].copy(selected = cats[i].id == cat.id)
                    }
                    notifyDataSetChanged()
                } else {
                    Toast.makeText(ctx, "Stage ${cat.requiredStage} 클리어 시 해금됩니다", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = cats.size
    }
}
