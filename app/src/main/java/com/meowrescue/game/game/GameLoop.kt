package com.meowrescue.game.game

import com.meowrescue.game.ui.GameView

class GameLoop(
    private val gameEngine: GameEngine,
    private val gameView: GameView
) : Runnable {

    companion object {
        const val TARGET_FPS = 60
        const val TARGET_TIME = 1_000_000_000L / TARGET_FPS
    }

    @Volatile
    var running = false
    private var gameThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        gameThread = Thread(this).also { it.start() }
    }

    fun stop() {
        running = false
        try {
            gameThread?.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        gameThread = null
    }

    fun pause() {
        if (gameEngine.gameState == GameEngine.GameState.PLAYING) {
            gameEngine.gameState = GameEngine.GameState.PAUSED
        }
    }

    fun resume() {
        if (gameEngine.gameState == GameEngine.GameState.PAUSED) {
            gameEngine.gameState = GameEngine.GameState.PLAYING
        }
    }

    override fun run() {
        var lastTime = System.nanoTime()

        while (running) {
            val now = System.nanoTime()
            var deltaTime = (now - lastTime) / 1_000_000_000f
            lastTime = now

            // Clamp to prevent huge physics jumps on slow frames
            if (deltaTime > 0.05f) deltaTime = 0.05f

            gameEngine.update(deltaTime)
            gameView.render(gameEngine)

            val frameTime = System.nanoTime() - now
            val sleepTime = (TARGET_TIME - frameTime) / 1_000_000L
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
