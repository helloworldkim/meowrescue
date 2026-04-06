package com.meowrescue.game.launch.ui

import com.meowrescue.game.launch.model.CatAbility
import com.meowrescue.game.launch.model.LaunchGameState
import com.meowrescue.game.launch.physics.LaunchPhysicsWorld
import org.jbox2d.common.Vec2
import kotlin.math.sqrt

class LaunchInputHandler(private val view: LaunchGameView) {

    /** Returns a callback to invoke outside synchronized(lock), or null. */
    fun handleDown(x: Float, y: Float): (() -> Unit)? {
        // HUD: pause button
        if (view.pauseRect.contains(x, y)) {
            val cb = view.onMenuClicked
            return { cb?.invoke() }
        }

        // Overlay buttons
        if (view.gameState == LaunchGameState.STAGE_CLEAR) {
            if (view.nextStageRect.contains(x, y)) {
                val cb = view.onNextStageClicked
                return { cb?.invoke() }
            }
            if (view.retryRect.contains(x, y)) {
                val cb = view.onRetryClicked
                return { cb?.invoke() }
            }
            if (view.menuRect.contains(x, y)) {
                val cb = view.onMenuClicked
                return { cb?.invoke() }
            }
            return null
        }
        if (view.gameState == LaunchGameState.STAGE_FAIL) {
            if (view.retryRect.contains(x, y)) {
                val cb = view.onRetryClicked
                return { cb?.invoke() }
            }
            if (view.menuRect.contains(x, y)) {
                val cb = view.onMenuClicked
                return { cb?.invoke() }
            }
            return null
        }

        // Ability activation / camera pan during flight
        if (view.gameState == LaunchGameState.FLYING || view.gameState == LaunchGameState.ABILITY_READY) {
            view.potentialTap = true
            view.tapDownX = x
            view.tapDownY = y
            view.panLastX = x
            view.panLastY = y
            return null
        }

        // Slingshot drag or camera pan in AIMING state
        if (view.gameState == LaunchGameState.AIMING) {
            val anchorScreenX = view.renderer.worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
            val anchorScreenY = view.renderer.worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
            val touchRadius = 80f * view.density
            val dx = x - anchorScreenX
            val dy = y - anchorScreenY
            if (sqrt(dx * dx + dy * dy) <= touchRadius) {
                view.isDragging = true
                view.manualPanActive = false
                view.dragStartX = x
                view.dragStartY = y
                view.dragCurrentX = x
                view.dragCurrentY = y
            } else {
                view.isPanning = true
                view.panLastX = x
                view.panLastY = y
            }
        }
        return null
    }

    fun handleMove(x: Float, y: Float) {
        // Slingshot dragging
        if (view.isDragging) {
            view.dragCurrentX = x
            view.dragCurrentY = y

            // Clamp pull distance
            val anchorScreenX = view.renderer.worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
            val anchorScreenY = view.renderer.worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)
            val dx = view.dragCurrentX - anchorScreenX
            val dy = view.dragCurrentY - anchorScreenY
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > view.maxPullDistancePx) {
                val scale = view.maxPullDistancePx / dist
                view.dragCurrentX = anchorScreenX + dx * scale
                view.dragCurrentY = anchorScreenY + dy * scale
            }
            return
        }

        // Convert potential tap to pan if moved enough (FLYING/ABILITY_READY)
        if (view.potentialTap) {
            val dx = x - view.tapDownX
            val dy = y - view.tapDownY
            if (sqrt(dx * dx + dy * dy) > LaunchGameView.PAN_THRESHOLD_PX * view.density) {
                view.potentialTap = false
                view.isPanning = true
                view.manualPanActive = true
                view.panLastX = x
                view.panLastY = y
            }
            return
        }

        // Camera panning
        if (view.isPanning) {
            val dx = view.panLastX - x
            val dy = view.panLastY - y
            view.cameraOffsetX += dx / view.pixelsPerMeter
            view.cameraOffsetY -= dy / view.pixelsPerMeter
            view.clampCamera()
            view.panLastX = x
            view.panLastY = y
        }
    }

    // x, y 미사용: handleDown/handleMove와 인터페이스 대칭 유지
    @Suppress("UNUSED_PARAMETER")
    fun handleUp(x: Float, y: Float): (() -> Unit)? {
        // End camera panning
        if (view.isPanning) {
            if (view.gameState == LaunchGameState.AIMING) {
                view.manualPanActive = true
            }
            view.isPanning = false
            return null
        }

        // Ability activation: was a short tap during flight (not a pan)
        if (view.potentialTap) {
            view.potentialTap = false
            val pw = view.physicsWorld ?: return null
            val projectiles = pw.getProjectiles()
            val activeProjectile = projectiles.lastOrNull()
            if (activeProjectile != null && !activeProjectile.abilityUsed) {
                val tapWorldPos = Vec2(
                    view.renderer.screenToWorldX(view.tapDownX),
                    view.renderer.screenToWorldY(view.tapDownY)
                )
                val prePos = Vec2(activeProjectile.body.position.x, activeProjectile.body.position.y)
                pw.activateAbility(activeProjectile, tapWorldPos)
                if (activeProjectile.ability is CatAbility.Explosive) {
                    view.explosions.add(LaunchGameView.ExplosionEffect(
                        x = prePos.x, y = prePos.y,
                        radius = 0f,
                        maxRadius = (activeProjectile.ability as CatAbility.Explosive).blastRadiusMeters,
                        alpha = 1f,
                        startTime = System.currentTimeMillis()
                    ))
                }
            }
            return null
        }

        // Slingshot release
        if (!view.isDragging) return null
        view.isDragging = false

        val pw = view.physicsWorld ?: return null
        val config = view.stageConfig ?: return null
        if (view.currentCatIndex >= config.catIds.size) return null

        val anchorScreenX = view.renderer.worldToScreenX(LaunchPhysicsWorld.SLINGSHOT_X)
        val anchorScreenY = view.renderer.worldToScreenY(LaunchPhysicsWorld.SLINGSHOT_Y)

        val pullScreenX = anchorScreenX - view.dragCurrentX
        val pullScreenY = anchorScreenY - view.dragCurrentY

        val pullDist = sqrt(pullScreenX * pullScreenX + pullScreenY * pullScreenY)
        if (pullDist < 10f * view.density) return null

        val pullWorldX = pullScreenX / view.pixelsPerMeter
        val pullWorldY = -pullScreenY / view.pixelsPerMeter

        val pullVector = Vec2(pullWorldX, pullWorldY)

        val catId = config.catIds[view.currentCatIndex]
        val ability = CatAbility.forCatId(catId)
        pw.launchProjectile(catId, ability, pullVector)

        view.currentCatIndex++
        view.catsUsed++
        view.settleFrameCount = 0
        view.manualPanActive = false

        val hasAbility = ability !is CatAbility.Normal && ability !is CatAbility.Charge
        view.gameState = if (hasAbility) LaunchGameState.ABILITY_READY else LaunchGameState.FLYING

        return null
    }
}
