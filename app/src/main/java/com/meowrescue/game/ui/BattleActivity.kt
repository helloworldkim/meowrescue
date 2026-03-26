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

        // Generate stage
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
        setContentView(battleView)

        // Setup grid layout after view is ready
        battleView.post { battleView.setupGrid() }

        gameLoop = GameLoop(battleEngine, battleView)

        // Connect battle events
        battleEngine.eventListener = object : BattleEngine.BattleEventListener {
            override fun onPhaseChanged(phase: BattleTurnPhase) {
                gameLoop.onPhaseChanged(phase)
            }

            override fun onMatchFound(matches: List<MatchResult>) {
                SoundManager.playButtonTap()
                val positions = matches.flatMap { it.positions }.toSet()
                battleView.startMatchAnimation(positions)
            }

            override fun onCascade(round: Int, matches: List<MatchResult>) {
                SoundManager.playBallBounce()
            }

            override fun onDamageDealt(results: List<DamageResult>) {
                SoundManager.playBallDestroy()
                battleView.effectRenderer.triggerScreenShake()
                for (result in results) {
                    if (result.damage > 0) {
                        // Position damage numbers at screen center (approximate)
                        val x = 540f
                        val y = 300f
                        battleView.effectRenderer.addDamageNumber(x, y, result.damage, result.isWeakness)
                    }
                }
            }

            override fun onPlayerHealed(amount: Int) {
                SoundManager.playCatRescue()
                battleView.effectRenderer.addHealNumber(540f, 1800f, amount)
            }

            override fun onEnemyAttack(enemy: Enemy, effect: EnemyAI.AttackEffect) {
                when (effect) {
                    is EnemyAI.AttackEffect.DamagePlayer -> {
                        SoundManager.playBombExplode()
                        battleView.effectRenderer.triggerScreenShake(12f)
                    }
                    is EnemyAI.AttackEffect.HealSelf -> {
                        SoundManager.playCatRescue()
                    }
                    is EnemyAI.AttackEffect.BuffSelf -> {
                        SoundManager.playSwitchToggle()
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
        }

        // Victory/Defeat callbacks
        battleView.onVictory = {
            runOnUiThread {
                SoundManager.playButtonTap()
                val nextStage = stage + 1
                val goToNext = {
                    if (nextStage > 10) {
                        // Chapter complete — go to next chapter
                        val intent = Intent(this, BattleActivity::class.java)
                        intent.putExtra("chapter", chapter + 1)
                        intent.putExtra("stage", 1)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, BattleActivity::class.java)
                        intent.putExtra("chapter", chapter)
                        intent.putExtra("stage", nextStage)
                        startActivity(intent)
                    }
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
                    val intent = Intent(this, BattleActivity::class.java)
                    intent.putExtra("chapter", chapter)
                    intent.putExtra("stage", stage)
                    startActivity(intent)
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

        // Preload ads
        AdManager.loadInterstitial(this)
        AdManager.loadRewarded(this)
    }

    override fun onResume() {
        super.onResume()
        gameLoop.start()
        SoundManager.playBgm("beginner")
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
