package com.meowrescue.game.ui

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository

class CollectionActivity : AppCompatActivity() {

    data class CatEntry(
        val catId: String,
        val drawableRes: Int,
        val name: String,
        val rarity: String,
        val collected: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = GameRepository(this)
        val unlockedCats = repository.getUnlockedCats().toSet()

        val allCats = listOf(
            CatEntry("cat_001", R.drawable.cat_1, "Orange Tabby", "Common", unlockedCats.contains("cat_001")),
            CatEntry("cat_002", R.drawable.cat_2, "Happy Cat", "Common", unlockedCats.contains("cat_002")),
            CatEntry("cat_003", R.drawable.cat_3, "Yarn Lover", "Common", unlockedCats.contains("cat_003")),
            CatEntry("cat_004", R.drawable.cat_4, "Adventurer", "Common", unlockedCats.contains("cat_004")),
            CatEntry("cat_005", R.drawable.cat_5, "Crying Cat", "Common", unlockedCats.contains("cat_005")),
            CatEntry("cat_006", R.drawable.cat_6, "Sleepy Cat", "Rare", unlockedCats.contains("cat_006")),
            CatEntry("cat_007", R.drawable.cat_7, "Star Cat", "Rare", unlockedCats.contains("cat_007")),
            CatEntry("cat_008", R.drawable.cat_8, "Surprised Cat", "Legendary", unlockedCats.contains("cat_008"))
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF9FB"))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FFB3C6"))
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backBtn = TextView(this).apply {
            text = "< Back"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setOnClickListener { finish() }
        }
        toolbar.addView(backBtn)

        val titleTv = TextView(this).apply {
            text = "Cat Collection"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = 24
            layoutParams = lp
        }
        toolbar.addView(titleTv)
        root.addView(toolbar)

        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@CollectionActivity, 3)
            adapter = CatAdapter(allCats)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 16
            layoutParams = lp
            setPadding(16, 16, 16, 16)
        }
        root.addView(recycler)

        setContentView(root)
    }

    inner class CatAdapter(private val cats: List<CatEntry>) :
        RecyclerView.Adapter<CatAdapter.CatViewHolder>() {

        inner class CatViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
                setBackgroundColor(Color.parseColor("#FFFFFF"))
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(8, 8, 8, 8)
                layoutParams = lp
            }
            return CatViewHolder(card)
        }

        override fun onBindViewHolder(holder: CatViewHolder, position: Int) {
            val cat = cats[position]
            holder.card.removeAllViews()

            val dp100 = (100 * holder.card.context.resources.displayMetrics.density).toInt()
            val catImage = ImageView(holder.card.context).apply {
                setImageResource(cat.drawableRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = LinearLayout.LayoutParams(dp100, dp100)
                lp.gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = lp
            }

            if (!cat.collected) {
                val matrix = ColorMatrix()
                matrix.setSaturation(0f)
                catImage.colorFilter = ColorMatrixColorFilter(matrix)
                catImage.alpha = 0.3f
            } else {
                catImage.colorFilter = null
                catImage.alpha = 1.0f
            }

            val nameTv = TextView(holder.card.context).apply {
                text = if (cat.collected) cat.name else "???"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#555555"))
            }

            val rarityColor = when (cat.rarity) {
                "Legendary" -> "#FFB3C6"
                "Rare" -> "#C8B8E8"
                else -> "#B5EAD7"
            }
            val rarityTv = TextView(holder.card.context).apply {
                text = if (cat.collected) cat.rarity else "???"
                textSize = 11f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor(rarityColor))
                setTextColor(Color.parseColor("#333333"))
                setPadding(8, 4, 8, 4)
            }

            holder.card.alpha = 1.0f
            holder.card.addView(catImage)
            holder.card.addView(nameTv)
            holder.card.addView(rarityTv)
        }

        override fun getItemCount() = cats.size
    }
}
