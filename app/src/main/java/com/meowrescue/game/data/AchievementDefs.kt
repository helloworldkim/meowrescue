package com.meowrescue.game.data

/**
 * Static achievement definitions. Each has an ID, display info, and coin reward.
 */
object AchievementDefs {

    data class AchievementDef(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val coinReward: Int
    )

    val ALL: List<AchievementDef> = listOf(
        // ── Progress (진행) ─────────────────────────────────────────
        AchievementDef("clear_1",    "첫 발걸음",      "스테이지 1 클리어",       "\u2B50", 20),
        AchievementDef("clear_10",   "견습 구조대원",    "스테이지 10 클리어",      "\u2B50", 30),
        AchievementDef("clear_30",   "정원 졸업",       "스테이지 30 클리어",      "\uD83C\uDF3F", 50),
        AchievementDef("clear_60",   "해변 졸업",       "스테이지 60 클리어",      "\uD83C\uDFD6", 50),
        AchievementDef("clear_90",   "숲 졸업",        "스테이지 90 클리어",      "\uD83C\uDF32", 50),
        AchievementDef("clear_120",  "눈 졸업",        "스테이지 120 클리어",     "\u2744", 50),
        AchievementDef("clear_150",  "화산 졸업",       "스테이지 150 클리어",     "\uD83C\uDF0B", 80),
        AchievementDef("clear_180",  "우주 졸업",       "스테이지 180 클리어",     "\uD83D\uDE80", 80),
        AchievementDef("clear_200",  "전설의 구조대원",   "모든 스테이지 클리어!",    "\uD83D\uDC51", 200),

        // ── Mastery (마스터리) ──────────────────────────────────────
        AchievementDef("star3_10",   "별 수집가",       "3성 10개 달성",          "\u2B50", 30),
        AchievementDef("star3_50",   "스타 마스터",      "3성 50개 달성",          "\u2B50", 80),
        AchievementDef("star3_100",  "퍼펙트 플레이어",   "3성 100개 달성",         "\u2B50", 150),
        AchievementDef("star3_200",  "완벽주의자",       "모든 스테이지 3성!",       "\uD83C\uDFC6", 500),

        // ── Gameplay (게임플레이) ────────────────────────────────────
        AchievementDef("optimal_clear", "최적 경로",    "최소 이동으로 클리어",     "\uD83C\uDFAF", 20),
        AchievementDef("two_move",     "최소 클리어",    "2수 이하로 클리어",        "\u26A1", 50),
        AchievementDef("no_undo",    "한 번에 OK",      "실행취소 없이 3성 클리어",   "\u2705", 20),
        AchievementDef("speed_10s",  "번개손",         "10초 안에 클리어",         "\u26A1", 30),
        AchievementDef("speed_5s",   "찰나의 구출",     "5초 안에 클리어",          "\u26A1", 80),

        // ── Launch Mode ───────────────────────────────────────────
        AchievementDef("launch_10",  "런처 루키",       "Cat Launch 10 스테이지 클리어", "\uD83D\uDE80", 30),
        AchievementDef("launch_1cat","원샷 원킬",       "고양이 1마리로 클리어",       "\uD83C\uDFAF", 50),
        AchievementDef("launch_tnt", "폭파 전문가",      "TNT 연쇄 3회 이상",         "\uD83D\uDCA5", 30),

        // ── Collection (컬렉션) ─────────────────────────────────────
        AchievementDef("cat_3",      "고양이 친구",      "고양이 3마리 해금",         "\uD83D\uDC31", 20),
        AchievementDef("cat_7",      "고양이 대가족",     "고양이 7마리 해금",         "\uD83D\uDC31", 50),
        AchievementDef("cat_all",    "모든 고양이!",     "모든 고양이 해금",           "\uD83D\uDC31", 200),

        // ── Economy ────────────────────────────────────────────────
        AchievementDef("coins_100",  "용돈 모으기",      "코인 100개 누적 획득",       "\uD83D\uDCB0", 10),
        AchievementDef("coins_1000", "부자 고양이",      "코인 1000개 누적 획득",      "\uD83D\uDCB0", 50),
        AchievementDef("powerup_1",  "파워업 입문",      "파워업 처음 사용",           "\u2728", 10),
        AchievementDef("powerup_10", "파워업 마니아",     "파워업 10회 사용",           "\u2728", 30),

        // ── Endless ────────────────────────────────────────────────
        AchievementDef("endless_10", "끝없는 도전",      "엔들리스 10스테이지 클리어",    "\u267E", 30),
        AchievementDef("endless_50", "무한 구조대원",     "엔들리스 50스테이지 클리어",    "\u267E", 100),
    )

    private val MAP = ALL.associateBy { it.id }

    fun get(id: String): AchievementDef? = MAP[id]
}
