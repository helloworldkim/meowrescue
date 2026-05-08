package com.meowrescue.game.ui

import com.meowrescue.game.R
import com.meowrescue.game.data.GameRepository

object CatAssetResolver {
    fun getDrawable(catId: Int): Int =
        GameRepository.CAT_DEFINITIONS.firstOrNull { it.id == catId }?.drawableRes ?: R.drawable.cat_1
}
