package com.meowrescue.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.meowrescue.game.R
import com.meowrescue.game.ads.AdManager
import com.meowrescue.game.data.GameRepository
import com.meowrescue.game.engine.BattleEngine
import com.meowrescue.game.engine.BattleTurnPhase
import com.meowrescue.game.engine.EnemyAI
import com.meowrescue.game.game.GameLoop
import com.meowrescue.game.generator.StageGenerator
import com.meowrescue.game.model.BattleState
import com.meowrescue.game.model.CatBuff
import com.meowrescue.game.model.DamageResult
import com.meowrescue.game.model.Enemy
import com.meowrescue.game.model.MatchResult
import com.meowrescue.game.model.Relic
import com.meowrescue.game.util.SoundManager
import kotlinx.coroutines.launch

class BattleActivity : AppCompatActivity() {

    private lateinit var battleEngine: BattleEngine
    private lateinit var battleView: BattleView
    private lateinit var gameLoop: GameLoop
    private lateinit var repository: GameRepository

    private var chapter = 1
    private var stage = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

        chapter = intent.getIntExtra("chapter", 1)
        stage = intent.getIntExtra("stage", 1)
        repository = GameRepository(this)

        val stageData = StageGenerator.generateStage(chapter, stage)
        val playerHp = com.meowrescue.game.generator.DifficultyScaler.getPlayerStartHp(chapter)

        val battleState = BattleState(
            grid = stageData.grid,
            enemies = stageData.enemies.toMutableList(),
            playerMaxHp = playerHp,
            playerCurrentHp = playerHp,
            turnCount = 0,
            catBuffs = mutableListOf(),
            relics = mutableListOf(),
            phase = BattleTurnPhase.PLAYER_INPUT,
            chapter = chapter,
            stage = stage
        )

        battleEngine = BattleEngine(battleState)
        battleView = BattleView(this)
        battleView.battleEngine = battleEngine

        // Use existing cat_1~8 images for the HUD portrait based on stage
        val catSprites = intArrayOf(
            R.drawable.cat_1, R.drawable.cat_2, R.drawable.cat_3, R.drawable.cat_4,
            R.drawable.cat_5, R.drawable.cat_6, R.drawable.cat_7, R.drawable.cat_8
        )
        val catIndex = ((chapter - 1) * 10 + (stage - 1)) % catSprites.size
        battleView.setCatPortrait(catSprites[catIndex])

        setContentView(battleView)

        battleView.tutorialOverlay.init(chapter, stage)
        battleView.post { battleView.setupGrid() }

        gameLoop = GameLoop(battleEngine, battleView)

        battleEngine.eventListener = object : BattleEngine.BattleEventListener {
            override fun onPhaseChanged(phase: BattleTurnPhase) {
                gameLoop.onPhaseChanged(phase)
            }

            override fun onMatchFound(matches: List<MatchResult>) {
                SoundManager.playBlockMatch()
                val positions = matches.flatMap { it.positions }.toSet()
                battleView.startMatchAnimation(positions)
            }

            override fun onCascade(round: Int, matches: List<MatchResult>) {
                SoundManager.playCascade()
                battleView.effectRenderer.triggerScreenShake(8f, round)
                if (round >= 2) {
                    battleView.effectRenderer.addComboText(540f, 500f, round)
                }
            }

            override fun onDamageDealt(results: List<DamageResult>) {
                SoundManager.playAttackHit()
                battleView.effectRenderer.triggerScreenShake(8f, 0)
                for (result in results) {
                    if (result.damage > 0) {
                        battleView.effectRenderer.addDamageNumber(540f, 300f, result.damage, result.isWeakness, result.matchType)
                        result.matchType?.let { type ->
                            battleView.effectRenderer.addElementBurst(540f, 300f, type)
                        }
                    }
                }
            }

            override fun onPlayerHealed(amount: Int) {
                SoundManager.playHeal()
                battleView.effectRenderer.addHealNumber(540f, 1800f, amount)
            }

            override fun onEnemyAttack(enemy: Enemy, effect: EnemyAI.AttackEffect) {
                when (effect) {
                    is EnemyAI.AttackEffect.DamagePlayer -> {
                        SoundManager.playEnemyAttack()
                        battleView.effectRenderer.triggerScreenShake(12f)
                    }
                    is EnemyAI.AttackEffect.HealSelf -> {
                        SoundManager.playHeal()
                    }
                    is EnemyAI.AttackEffect.BuffSelf -> {
                        SoundManager.playBlockMatch()
                    }
                }
            }

            override fun onEnemyDefeated(enemy: Enemy) {
                SoundManager.playCageDestroy()
            }

            override fun onVictory() {
                SoundManager.playLevelClear()
                runOnUiThread {
                    battleView.showVictory()
                }
            }

            override fun onDefeat() {
                SoundManager.playLevelFail()
                runOnUiThread {
                    battleView.showDefeat()
                }
            }

            override fun onSwapFailed(r1: Int, c1: Int, r2: Int, c2: Int) {
                SoundManager.playButtonTap()
            }

            override fun onBlockSelected(row: Int, col: Int) {
                SoundManager.playButtonTap()
            }

            override fun onNoValidMoves(canShuffle: Boolean) {
                runOnUiThread {
                    if (canShuffle) {
                        android.app.AlertDialog.Builder(this@BattleActivity)
                            .setTitle("No Moves!")
                            .setMessage("No valid swaps available.\nShuffle the board? (1 chance)")
                            .setPositiveButton("Shuffle") { _, _ ->
                                battleEngine.requestShuffle()
                            }
                            .setNegativeButton("Give Up") { _, _ ->
                                battleEngine.declineShuffle()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        android.app.AlertDialog.Builder(this@BattleActivity)
                            .setTitle("No Moves!")
                            .setMessage("No valid swaps and no shuffles remaining.")
                            .setPositiveButton("OK") { _, _ ->
                                battleEngine.declineShuffle()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }

            override fun onShuffle() {
                SoundManager.playCascade()
            }
        }

        battleView.onVictory = {
            runOnUiThread {
                SoundManager.playButtonTap()
                val goToNext = {
                    val nextIntent = if (stage >= 10) {
                        Intent(this, BattleActivity::class.java)
                            .putExtra("chapter", chapter + 1)
                            .putExtra("stage", 1)
                    } else {
                        Intent(this, BattleActivity::class.java)
                            .putExtra("chapter", chapter)
                            .putExtra("stage", stage + 1)
                    }
                    startActivity(nextIntent)
                    finish()
                }
                if (AdManager.shouldShowInterstitial(stage)) {
                    AdManager.showInterstitial(this) { goToNext() }
                } else {
                    goToNext()
                }
            }
        }

        battleView.onDefeat = {
            runOnUiThread {
                SoundManager.playButtonTap()
                val retry = {
                    startActivity(
                        Intent(this, BattleActivity::class.java)
                            .putExtra("chapter", chapter)
                            .putExtra("stage", stage)
                    )
                    finish()
                }
                if (AdManager.isRewardedReady()) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Continue?")
                        .setMessage("Watch an ad to retry!")
                        .setPositiveButton("Watch Ad") { _, _ ->
                            AdManager.showRewarded(this,
                                onRewarded = { retry() },
                                onDismissed = { retry() }
                            )
                        }
                        .setNegativeButton("Give Up") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                } else {
                    retry()
                }
            }
        }

        battleView.onPause = {
            runOnUiThread {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Paused")
                    .setMessage("Chapter $chapter - Stage $stage")
                    .setPositiveButton("Resume") { d, _ -> d.dismiss() }
                    .setNegativeButton("Quit") { _, _ -> finish() }
                    .setCancelable(true)
                    .show()
            }
        }

        AdManager.loadInterstitial(this)
        AdManager.loadRewarded(this)
    }

    override fun onResume() {
        super.onResume()
        gameLoop.start()
        val bgmKey = if (stage == 10) "battle_boss" else "battle_normal"
        SoundManager.playBgm(bgmKey)
    }

    override fun onPause() {
        super.onPause()
        gameLoop.stop()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameLoop.stop()
        battleView.cleanup()
        SoundManager.stopBgm()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
