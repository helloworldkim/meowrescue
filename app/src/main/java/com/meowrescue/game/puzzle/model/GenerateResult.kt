package com.meowrescue.game.puzzle.model

import com.meowrescue.game.puzzle.engine.PuzzleGrid

data class GenerateResult(val grid: PuzzleGrid, val optimalMoves: Int)
