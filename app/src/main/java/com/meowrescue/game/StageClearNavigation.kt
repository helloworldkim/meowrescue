package com.meowrescue.game

/** Intent extra keys for the Stage Clear → Stage Select navigation signal (OQ-SS6). */
object StageClearNavigation {
    /** The stageId that was just cleared. Type: Int. */
    const val EXTRA_LAST_CLEARED_STAGE = "extra_last_cleared_stage"
    /** The new star count awarded for the cleared stage (1–3). Type: Int. */
    const val EXTRA_NEW_STAR_COUNT = "extra_new_star_count"
}
