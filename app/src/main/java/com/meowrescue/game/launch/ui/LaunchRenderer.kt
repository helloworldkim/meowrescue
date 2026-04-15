package com.meowrescue.game.launch.ui

import android.graphics.*
import com.meowrescue.game.launch.model.CatAbility
import com.meowrescue.game.launch.model.LaunchGameState
import com.meowrescue.game.launch.model.ObstacleMaterial
import com.meowrescue.game.launch.physics.LaunchPhysicsWorld
import com.meowrescue.game.ui.Theme
import com.meowrescue.game.util.ScreenShake
import kotlin.math.min

class LaunchRenderer(
    private val view: LaunchGameView,
    private val paints: LaunchPaints
) {

    companion object {
        private const val TRAJECTORY_DOT_COUNT = 30
        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
    }

    // ── Reusable Rect/RectF to avoid per-frame allocation ──────────────────
    private val tmpSrcRect = Rect()
    private val tmpDstRect = RectF()

    // ── Coordinate conversion ────────────────────────────────────────────────

    fun worldToScreenX(wx: Float): Float =
        (wx - view.cameraOffsetX) * view.pixelsPerMeter

    fun worldToScreenY(wy: Float): Float =
        view.worldOriginScreenY - (wy - view.cameraOffsetY) * view.pixelsPerMeter

    fun screenToWorldX(sx: Float): Float =
        sx / view.pixelsPerMeter + view.cameraOffsetX

    fun screenToWorldY(sy: Float): Float =
        (view.worldOriginScreenY - sy) / view.pixelsPerMeter + view.cameraOffsetY

    // ── Entry point ──────────────────────────────────────────────────────────

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        // Apply screen shake
        val shakeX = ScreenShake.offsetX
        val shakeY = ScreenShake.offsetY
        if (shakeX != 0f || shakeY != 0f) {
            canvas.save()
            canvas.translate(shakeX, shakeY)
        }

        drawSky(canvas)
        drawGround(canvas)

        val pw = view.physicsWorld ?: run {
            if (shakeX != 0f || shakeY != 0f) canvas.restore()
            return
        }

        drawObstacles(canvas, pw)
        drawEnemies(canvas, pw)
        drawSlingshot(canvas)

        if (view.isDragging) {
            drawBand(canvas)
            drawTrajectoryPreview(canvas)
        }

        drawCurrentCatOnSlingshot(canvas)
        drawProjectiles(canvas, pw)
        drawDebris(canvas, pw)
        drawExplosions(canvas)
        drawHud(canvas)
        drawCatQueue(canvas)
        drawCelebration(canvas)

        // Restore shake transform before overlays
        if (shakeX != 0f || shakeY != 0f) {
            canvas.restore()
        }

        when (view.gameState) {
            LaunchGameState.STAGE_CLEAR -> drawVictoryOverlay(canvas)
            LaunchGameState.STAGE_FAIL -> drawFailOverlay(canvas)
            else -> {}
        }
    }

    // ── Draw methods ─────────────────────────────────────────────────────────

    private fun drawSky(canvas: Canvas) {
        if (view.skyGradient != null) {
            canvas.drawRect(0f, 0f, view.width.toFloat(), worldToScreenY(LaunchPhysicsWorld.GROUND_HEIGHT), paints.skyPaint)
        } else {
            canvas.drawColor(Theme.LAUNCH_SKY_TOP)
        }
    }

    private fun drawGround(canvas: Canvas) {
        val groundTop = worldToScreenY(LaunchPhysicsWorld.GROUND_HEIGHT)
        val h = view.height.toFloat()

        canvas.drawRect(0f, groundTop, view.width.toFloat(), h, paints.groundPaint)

        val grassHeight = 4f * view.density
        canvas.drawRect(0f, groundTop, view.width.toFloat(), groundTop + grassHeight, paints.grassPaint)
    }

    private fun drawSlingshot(canvas: Canvas) {
        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val baseY = worldToScreenY(LaunchPhysicsWorld.GROUND_HEIGHT)
        val topY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val forkSpread = 12f * view.density
        val forkHeight = 15f * view.density

        paints.slingshotPaint.strokeWidth = 6f * view.density

        canvas.drawLine(baseX, baseY, baseX, topY, paints.slingshotPaint)
        canvas.drawLine(baseX, topY, baseX - forkSpread, topY - forkHeight, paints.slingshotPaint)
        canvas.drawLine(baseX, topY, baseX + forkSpread, topY - forkHeight, paints.slingshotPaint)

        val baseW = 10f * view.density
        val baseH = 5f * view.density
        canvas.drawRect(
            baseX - baseW / 2f, baseY - baseH,
            baseX + baseW / 2f, baseY,
            paints.slingshotBasePaint
        )
    }

    private fun drawBand(canvas: Canvas) {
        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val topY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val forkSpread = 12f * view.density
        val forkHeight = 15f * view.density

        val leftTipX = baseX - forkSpread
        val leftTipY = topY - forkHeight
        val rightTipX = baseX + forkSpread
        val rightTipY = topY - forkHeight

        paints.bandPaint.strokeWidth = 4f * view.density

        canvas.drawLine(leftTipX, leftTipY, view.dragCurrentX, view.dragCurrentY, paints.bandPaint)
        canvas.drawLine(rightTipX, rightTipY, view.dragCurrentX, view.dragCurrentY, paints.bandPaint)

        val config = view.stageConfig ?: return
        if (view.currentCatIndex < config.catIds.size) {
            val catId = config.catIds[view.currentCatIndex]
            val catRadius = 12f * view.density
            val bm = view.catBitmaps[catId]
            if (bm != null) {
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(view.dragCurrentX - catRadius, view.dragCurrentY - catRadius,
                               view.dragCurrentX + catRadius, view.dragCurrentY + catRadius)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
            } else {
                canvas.drawCircle(view.dragCurrentX, view.dragCurrentY, catRadius, paints.catFallbackPaint)
            }
        }
    }

    private fun drawTrajectoryPreview(canvas: Canvas) {
        val anchorScreenX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val anchorScreenY = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)

        val pullScreenX = anchorScreenX - view.dragCurrentX
        val pullScreenY = anchorScreenY - view.dragCurrentY

        var vx = pullScreenX / view.pixelsPerMeter * LaunchPhysicsWorld.POWER_FACTOR
        var vy = -pullScreenY / view.pixelsPerMeter * LaunchPhysicsWorld.POWER_FACTOR

        val previewSpeed = kotlin.math.sqrt(vx * vx + vy * vy)
        if (previewSpeed > LaunchPhysicsWorld.MAX_LAUNCH_SPEED) {
            val scale = LaunchPhysicsWorld.MAX_LAUNCH_SPEED / previewSpeed
            vx *= scale
            vy *= scale
        }

        val gx = LaunchPhysicsWorld.GRAVITY.x
        val gy = LaunchPhysicsWorld.GRAVITY.y
        val dt = LaunchPhysicsWorld.TIME_STEP * 3f

        var wx = LaunchPhysicsWorld.SLINGSHOT_X
        var wy = LaunchPhysicsWorld.SLINGSHOT_Y
        var cvx = vx
        var cvy = vy

        for (i in 0 until TRAJECTORY_DOT_COUNT) {
            wx += cvx * dt
            wy += cvy * dt
            cvx += gx * dt
            cvy += gy * dt

            val sx = worldToScreenX(wx)
            val sy = worldToScreenY(wy)

            val alpha = (1f - i.toFloat() / TRAJECTORY_DOT_COUNT) * 200
            paints.trajectoryPaint.alpha = alpha.toInt()
            val dotRadius = (3f - i.toFloat() / TRAJECTORY_DOT_COUNT * 2f) * view.density
            canvas.drawCircle(sx, sy, dotRadius, paints.trajectoryPaint)
        }
        paints.trajectoryPaint.alpha = 255
    }

    private fun drawObstacles(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (obstacle in pw.getObstacles()) {
            val pos = obstacle.body.position
            val angle = obstacle.body.angle
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val halfW = obstacle.widthM / 2f * view.pixelsPerMeter
            val halfH = obstacle.heightM / 2f * view.pixelsPerMeter
            val cornerRadius = 3f * view.density

            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(-angle * RAD_TO_DEG)

            paints.obstaclePaint.color = obstacle.material.color
            canvas.drawRoundRect(
                -halfW, -halfH, halfW, halfH,
                cornerRadius, cornerRadius, paints.obstaclePaint
            )

            if (obstacle.material == ObstacleMaterial.TNT) {
                val mx = halfW * 0.5f
                val my = halfH * 0.5f
                paints.tntMarkPaint.strokeWidth = 2f * view.density
                canvas.drawLine(-mx, -my, mx, my, paints.tntMarkPaint)
                canvas.drawLine(mx, -my, -mx, my, paints.tntMarkPaint)
            }

            val hpRatio = obstacle.hp.toFloat() / obstacle.material.maxHp
            if (hpRatio < 0.5f) {
                val crackCount = if (hpRatio < 0.25f) 3 else 1
                for (c in 0 until crackCount) {
                    val cx1 = -halfW * 0.3f + c * halfW * 0.2f
                    val cy1 = -halfH * 0.5f
                    val cx2 = halfW * 0.2f + c * halfW * 0.1f
                    val cy2 = halfH * 0.5f
                    canvas.drawLine(cx1, cy1, cx2, cy2, paints.crackPaint)
                }
            }

            canvas.restore()
        }
    }

    private fun drawEnemies(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (enemy in pw.getEnemies()) {
            val pos = enemy.body.position
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val radiusPx = enemy.radiusM * view.pixelsPerMeter

            canvas.drawCircle(sx, sy, radiusPx, paints.enemyPaint)

            val eyeOffsetX = radiusPx * 0.3f
            val eyeOffsetY = radiusPx * 0.2f
            val eyeRadius = radiusPx * 0.2f
            val pupilRadius = radiusPx * 0.1f

            canvas.drawCircle(sx - eyeOffsetX, sy - eyeOffsetY, eyeRadius, paints.enemyEyePaint)
            canvas.drawCircle(sx + eyeOffsetX, sy - eyeOffsetY, eyeRadius, paints.enemyEyePaint)

            canvas.drawCircle(sx - eyeOffsetX, sy - eyeOffsetY, pupilRadius, paints.enemyPupilPaint)
            canvas.drawCircle(sx + eyeOffsetX, sy - eyeOffsetY, pupilRadius, paints.enemyPupilPaint)
        }
    }

    private fun drawProjectiles(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (proj in pw.getProjectiles()) {
            val pos = proj.body.position
            val angle = proj.body.angle
            val sx = worldToScreenX(pos.x)
            val sy = worldToScreenY(pos.y)
            val radiusPx = proj.radiusM * view.pixelsPerMeter

            val bm = view.catBitmaps[proj.catId]
            if (bm != null) {
                canvas.save()
                canvas.translate(sx, sy)
                canvas.rotate(-angle * RAD_TO_DEG)
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(-radiusPx, -radiusPx, radiusPx, radiusPx)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
                canvas.restore()
            } else {
                canvas.drawCircle(sx, sy, radiusPx, paints.catFallbackPaint)
            }
        }
    }

    private fun drawDebris(canvas: Canvas, pw: LaunchPhysicsWorld) {
        for (d in pw.getDebris()) {
            val sx = worldToScreenX(d.x)
            val sy = worldToScreenY(d.y)
            val size = 4f * view.density

            paints.debrisPaint.color = d.material.color
            paints.debrisPaint.alpha = (d.life * 255).toInt().coerceIn(0, 255)

            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(d.rotation * RAD_TO_DEG)
            canvas.drawRect(-size / 2f, -size / 2f, size / 2f, size / 2f, paints.debrisPaint)
            canvas.restore()
        }
    }

    private fun drawExplosions(canvas: Canvas) {
        for (e in view.explosions) {
            val sx = worldToScreenX(e.x)
            val sy = worldToScreenY(e.y)
            val radiusPx = e.radius * view.pixelsPerMeter

            paints.explosionPaint.alpha = (e.alpha * 200).toInt().coerceIn(0, 255)
            canvas.drawCircle(sx, sy, radiusPx, paints.explosionPaint)
        }
    }

    private fun abilityColor(ability: CatAbility): Int = when (ability) {
        is CatAbility.Normal -> Theme.LAUNCH_ABILITY_NORMAL
        is CatAbility.Redirect -> Theme.LAUNCH_ABILITY_REDIRECT
        is CatAbility.Split -> Theme.LAUNCH_ABILITY_SPLIT
        is CatAbility.Explosive -> Theme.LAUNCH_ABILITY_EXPLOSIVE
        is CatAbility.Charge -> Theme.LAUNCH_ABILITY_CHARGE
    }

    private fun drawHud(canvas: Canvas) {
        val w = view.width.toFloat()
        val hudH = LaunchGameView.HUD_HEIGHT_DP * view.density

        canvas.drawRect(0f, 0f, w, hudH, paints.hudBgPaint)

        paints.hudTextPaint.textSize = 16f * view.density
        val textY = hudH / 2f + paints.hudTextPaint.textSize / 3f

        val config = view.stageConfig
        val diffLabel = if (view.difficultyLabel.isNotEmpty()) " [${view.difficultyLabel}]" else ""
        val stageText = if (config != null) "Stage ${config.stageId}$diffLabel" else "Stage"
        canvas.drawText(stageText, w / 2f, textY, paints.hudTextPaint)

        val remaining = if (config != null) config.catIds.size - view.currentCatIndex else 0
        val catsText = "x$remaining"
        paints.hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(catsText, 16f * view.density + 20f * view.density, textY, paints.hudTextPaint)
        paints.hudTextPaint.textAlign = Paint.Align.CENTER

        paints.catFallbackPaint.alpha = 255
        canvas.drawCircle(16f * view.density + 8f * view.density, hudH / 2f, 8f * view.density, paints.catFallbackPaint)

        if (config != null && view.currentCatIndex < config.catIds.size && view.gameState == LaunchGameState.AIMING) {
            val catId = config.catIds[view.currentCatIndex]
            val ability = CatAbility.forCatId(catId)
            val abilityName = ability.displayName
            val abColor = abilityColor(ability)

            paints.abilityLabelPaint.textSize = 11f * view.density
            paints.abilityBgPaint.color = abColor

            val tagW = paints.abilityLabelPaint.measureText(abilityName) + 12f * view.density
            val tagH = 16f * view.density
            val tagX = w / 2f - tagW / 2f
            val tagY = textY + 6f * view.density

            canvas.drawRoundRect(
                RectF(tagX, tagY, tagX + tagW, tagY + tagH),
                tagH / 2f, tagH / 2f, paints.abilityBgPaint
            )
            paints.abilityLabelPaint.color = Color.WHITE
            canvas.drawText(abilityName, w / 2f, tagY + tagH - 4f * view.density, paints.abilityLabelPaint)
        }

        val pw = view.physicsWorld
        if (pw != null) {
            paints.scorePaint.textSize = 16f * view.density
            val scoreX = w - 52f * view.density
            canvas.drawText("${pw.score}", scoreX, textY, paints.scorePaint)
        }

        val pauseSize = 32f * view.density
        val pauseRight = w - 12f * view.density
        val pauseLeft = pauseRight - pauseSize
        val pauseTop = (hudH - pauseSize) / 2f
        val pauseBottom = pauseTop + pauseSize
        view.pauseRect.set(pauseLeft, pauseTop, pauseRight, pauseBottom)

        val barW = 4f * view.density
        val barH = 16f * view.density
        val barGap = 4f * view.density
        val barCX = (pauseLeft + pauseRight) / 2f
        val barCY = (pauseTop + pauseBottom) / 2f
        paints.hudTextPaint.style = Paint.Style.FILL
        canvas.drawRect(
            barCX - barGap - barW, barCY - barH / 2f,
            barCX - barGap, barCY + barH / 2f,
            paints.hudTextPaint
        )
        canvas.drawRect(
            barCX + barGap, barCY - barH / 2f,
            barCX + barGap + barW, barCY + barH / 2f,
            paints.hudTextPaint
        )
    }

    private fun drawCurrentCatOnSlingshot(canvas: Canvas) {
        if (view.gameState != LaunchGameState.AIMING || view.isDragging) return
        val config = view.stageConfig ?: return
        if (view.currentCatIndex >= config.catIds.size) return

        val catId = config.catIds[view.currentCatIndex]
        val sx = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val sy = worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
        val catRadius = 12f * view.density

        // Apply squash/stretch
        val sq = view.catSquash
        val scaleX = if (sq < 1f) 1f + (1f - sq) * 0.3f else 1f / sq.coerceAtLeast(0.5f)
        val scaleY = sq

        canvas.save()
        canvas.translate(sx, sy)
        canvas.scale(scaleX, scaleY)

        val bm = view.catBitmaps[catId]
        if (bm != null) {
            tmpSrcRect.set(0, 0, bm.width, bm.height)
            tmpDstRect.set(-catRadius, -catRadius, catRadius, catRadius)
            canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
        } else {
            canvas.drawCircle(0f, 0f, catRadius, paints.catFallbackPaint)
        }
        canvas.restore()
    }

    private fun drawCatQueue(canvas: Canvas) {
        val config = view.stageConfig ?: return
        if (view.currentCatIndex >= config.catIds.size) return

        val remaining = config.catIds.size - view.currentCatIndex
        val showCount = min(remaining, 5)
        if (showCount <= 0) return

        val baseX = worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val baseY = worldToScreenY(LaunchPhysicsWorld.GROUND_HEIGHT) + 8f * view.density
        val currentSize = 26f * view.density
        val queueSize = 20f * view.density
        val spacing = 4f * view.density

        val totalW = currentSize + spacing +
            (showCount - 1).coerceAtLeast(0) * (queueSize + spacing) + 8f * view.density
        val bgRect = RectF(
            baseX - totalW / 2f, baseY,
            baseX + totalW / 2f, baseY + currentSize + 8f * view.density
        )
        canvas.drawRoundRect(bgRect, 4f * view.density, 4f * view.density, paints.catQueueBgPaint)

        var drawX = bgRect.left + 4f * view.density

        for (i in 0 until showCount) {
            val catIdx = view.currentCatIndex + i
            if (catIdx >= config.catIds.size) break
            val catId = config.catIds[catIdx]
            val isCurrent = (i == 0)
            val size = if (isCurrent) currentSize else queueSize

            val cx = drawX + size / 2f
            val cy = baseY + 4f * view.density + currentSize / 2f

            val bm = view.catBitmaps[catId]
            if (bm != null) {
                tmpSrcRect.set(0, 0, bm.width, bm.height)
                tmpDstRect.set(cx - size / 2f, cy - size / 2f,
                               cx + size / 2f, cy + size / 2f)
                canvas.drawBitmap(bm, tmpSrcRect, tmpDstRect, null)
            } else {
                paints.catFallbackPaint.alpha = if (isCurrent) 255 else 180
                canvas.drawCircle(cx, cy, size / 2f - 2f, paints.catFallbackPaint)
                paints.catFallbackPaint.alpha = 255
            }

            if (isCurrent) {
                paints.abilityBgPaint.color = Theme.INT_CORAL
                paints.abilityBgPaint.style = Paint.Style.STROKE
                paints.abilityBgPaint.strokeWidth = 2f * view.density
                canvas.drawCircle(cx, cy, size / 2f, paints.abilityBgPaint)
                paints.abilityBgPaint.style = Paint.Style.FILL
            }

            val ability = CatAbility.forCatId(catId)
            paints.abilityBgPaint.color = abilityColor(ability)
            paints.abilityBgPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + currentSize / 2f + 3f * view.density, 3f * view.density, paints.abilityBgPaint)

            drawX += size + spacing
        }
    }

    private fun drawCelebration(canvas: Canvas) {
        for (p in view.celebrationParticles) {
            paints.particlePaint.color = p.color
            paints.particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, paints.particlePaint)
        }
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val alpha = view.victoryAlpha
        val isLandscape = w > h

        paints.overlayBgPaint.color = Color.argb((alpha * 180).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paints.overlayBgPaint)

        if (alpha < 0.3f) return

        val panelW = if (isLandscape) w * 0.55f else w * 0.8f
        val panelH = if (isLandscape) h * 0.82f else h * 0.45f
        val panelLeft = (w - panelW) / 2f
        val panelTop = (h - panelH) / 2f
        val panelRight = panelLeft + panelW
        val panelBottom = panelTop + panelH
        val cornerR = 16f * view.density

        paints.buttonPaint.color = Theme.INT_BG_CREAM
        paints.buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, cornerR, cornerR, paints.buttonPaint)

        val titleY = panelTop + panelH * 0.10f
        val starCenterY = panelTop + panelH * 0.24f
        val infoY = panelTop + panelH * 0.38f
        val scoreY = panelTop + panelH * 0.47f
        val btnStartY = panelTop + panelH * 0.56f

        val titleSize = min(28f * view.density, panelH * 0.10f)
        paints.overlayTitlePaint.textSize = titleSize
        paints.overlayTitlePaint.color = Theme.INT_CORAL
        paints.overlayTitlePaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Stage Clear!", w / 2f, titleY, paints.overlayTitlePaint)

        val starSize = min(28f * view.density, panelH * 0.10f)
        val starSpacing = starSize * 1.5f
        val starStartX = w / 2f - starSpacing

        for (i in 0 until 3) {
            val sx = starStartX + i * starSpacing
            if (i < view.victoryStars) {
                paints.starPaint.alpha = (alpha * 255).toInt()
                canvas.drawCircle(sx, starCenterY, starSize / 2f, paints.starPaint)
                paints.overlayTitlePaint.textSize = starSize * 0.7f
                paints.overlayTitlePaint.color = Theme.INT_BG_CREAM
                paints.overlayTitlePaint.alpha = (alpha * 255).toInt()
                canvas.drawText("\u2605", sx, starCenterY + starSize * 0.2f, paints.overlayTitlePaint)
            } else {
                paints.starEmptyPaint.alpha = (alpha * 255).toInt()
                canvas.drawCircle(sx, starCenterY, starSize / 2f, paints.starEmptyPaint)
                paints.overlayTitlePaint.textSize = starSize * 0.7f
                paints.overlayTitlePaint.color = Color.WHITE
                paints.overlayTitlePaint.alpha = (alpha * 150).toInt()
                canvas.drawText("\u2605", sx, starCenterY + starSize * 0.2f, paints.overlayTitlePaint)
            }
        }

        val infoSize = min(16f * view.density, panelH * 0.06f)
        paints.overlayInfoPaint.textSize = infoSize
        paints.overlayInfoPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Cats used: ${view.catsUsed}", w / 2f, infoY, paints.overlayInfoPaint)

        val scoreSize = min(20f * view.density, panelH * 0.08f)
        paints.scorePaint.textSize = scoreSize
        paints.scorePaint.textAlign = Paint.Align.CENTER
        paints.scorePaint.alpha = (alpha * 255).toInt()
        val totalScore = view.physicsWorld?.score ?: 0
        canvas.drawText("Score: $totalScore", w / 2f, scoreY, paints.scorePaint)
        paints.scorePaint.textAlign = Paint.Align.RIGHT
        paints.scorePaint.alpha = 255

        val btnW = panelW * 0.7f
        val btnH = min(44f * view.density, panelH * 0.12f)
        val btnGap = min(10f * view.density, panelH * 0.03f)
        val btnLeft = (w - btnW) / 2f
        val btnRight = btnLeft + btnW
        val btnCorner = btnH / 2f
        val btnTextSize = min(16f * view.density, btnH * 0.45f)

        val nextTop = btnStartY
        val nextBottom = nextTop + btnH
        view.nextStageRect.set(btnLeft, nextTop, btnRight, nextBottom)
        paints.buttonPaint.color = Theme.INT_CORAL
        paints.buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(view.nextStageRect, btnCorner, btnCorner, paints.buttonPaint)
        paints.buttonTextPaint.textSize = btnTextSize
        paints.buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Next Stage", w / 2f, nextTop + btnH / 2f + btnTextSize / 3f, paints.buttonTextPaint)

        val retryTop = nextBottom + btnGap
        val retryBottom = retryTop + btnH
        view.retryRect.set(btnLeft, retryTop, btnRight, retryBottom)
        paints.retryButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(view.retryRect, btnCorner, btnCorner, paints.retryButtonPaint)
        paints.buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Retry", w / 2f, retryTop + btnH / 2f + btnTextSize / 3f, paints.buttonTextPaint)

        val menuTop = retryBottom + btnGap
        val menuBottom = menuTop + btnH
        view.menuRect.set(btnLeft, menuTop, btnRight, menuBottom)
        paints.menuButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(view.menuRect, btnCorner, btnCorner, paints.menuButtonPaint)
        paints.buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Menu", w / 2f, menuTop + btnH / 2f + btnTextSize / 3f, paints.buttonTextPaint)

        paints.buttonPaint.alpha = 255
        paints.overlayTitlePaint.alpha = 255
        paints.buttonTextPaint.alpha = 255
    }

    private fun drawFailOverlay(canvas: Canvas) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val alpha = view.victoryAlpha
        val isLandscape = w > h

        paints.overlayBgPaint.color = Color.argb((alpha * 180).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paints.overlayBgPaint)

        if (alpha < 0.3f) return

        val panelW = if (isLandscape) w * 0.55f else w * 0.8f
        val panelH = if (isLandscape) h * 0.65f else h * 0.35f
        val panelLeft = (w - panelW) / 2f
        val panelTop = (h - panelH) / 2f
        val panelRight = panelLeft + panelW
        val panelBottom = panelTop + panelH
        val cornerR = 16f * view.density

        paints.buttonPaint.color = Theme.INT_BG_CREAM
        paints.buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, cornerR, cornerR, paints.buttonPaint)

        val titleSize = min(28f * view.density, panelH * 0.12f)
        paints.overlayTitlePaint.textSize = titleSize
        paints.overlayTitlePaint.color = Theme.LAUNCH_ABILITY_EXPLOSIVE
        paints.overlayTitlePaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Stage Failed", w / 2f, panelTop + panelH * 0.25f, paints.overlayTitlePaint)

        val btnW = panelW * 0.7f
        val btnH = min(44f * view.density, panelH * 0.15f)
        val btnGap = min(10f * view.density, panelH * 0.04f)
        val btnLeft = (w - btnW) / 2f
        val btnRight = btnLeft + btnW
        val btnCorner = btnH / 2f
        val btnTextSize = min(16f * view.density, btnH * 0.45f)

        val retryTop = panelTop + panelH * 0.48f
        val retryBottom = retryTop + btnH
        view.retryRect.set(btnLeft, retryTop, btnRight, retryBottom)
        paints.buttonPaint.color = Theme.INT_CORAL
        paints.buttonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(view.retryRect, btnCorner, btnCorner, paints.buttonPaint)
        paints.buttonTextPaint.textSize = btnTextSize
        paints.buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Retry", w / 2f, retryTop + btnH / 2f + btnTextSize / 3f, paints.buttonTextPaint)

        val menuTop = retryBottom + btnGap
        val menuBottom = menuTop + btnH
        view.menuRect.set(btnLeft, menuTop, btnRight, menuBottom)
        paints.menuButtonPaint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(view.menuRect, btnCorner, btnCorner, paints.menuButtonPaint)
        paints.buttonTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawText("Menu", w / 2f, menuTop + btnH / 2f + btnTextSize / 3f, paints.buttonTextPaint)

        view.nextStageRect.setEmpty()

        paints.buttonPaint.alpha = 255
        paints.overlayTitlePaint.alpha = 255
        paints.buttonTextPaint.alpha = 255
    }
}
