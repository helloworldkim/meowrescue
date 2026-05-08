package com.meowrescue.game.threading

import com.meowrescue.game.core.threading.BaseLaunchActivity
import com.meowrescue.game.core.threading.LaunchRenderThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

class FakeLaunchActivity : BaseLaunchActivity() {
    val tickCount = AtomicInteger(0)
    public override val renderThread = LaunchRenderThread(tick = { tickCount.incrementAndGet() })
    var pauseMenuVisible = false
    override val isPauseMenuShown: Boolean get() = pauseMenuVisible
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PauseResumeCoordinationTest {

    private fun startedActivity(): Pair<FakeLaunchActivity, org.robolectric.android.controller.ActivityController<FakeLaunchActivity>> {
        val controller = Robolectric.buildActivity(FakeLaunchActivity::class.java)
            .create().start().resume()
        val activity = controller.get()
        activity.renderThread.startThread()
        Thread.sleep(50) // let thread establish ticks
        return activity to controller
    }

    // AC-1 + AC-2: onPause sets paused flag and stops ticks within 32ms
    @Test
    fun `onPause pauses render thread and stops ticks`() {
        val (activity, controller) = startedActivity()
        assertFalse(activity.renderThread.paused)

        controller.pause()

        Thread.sleep(32)
        assertTrue("renderThread.paused must be true after onPause", activity.renderThread.paused)

        val countAtPause = activity.tickCount.get()
        Thread.sleep(32)
        val countAfterWait = activity.tickCount.get()
        activity.renderThread.stopThread()

        assertTrue(
            "tick must stop after onPause (count was $countAtPause, now $countAfterWait)",
            countAfterWait == countAtPause
        )
    }

    // AC-3: onResume with hidden menu resumes ticks
    @Test
    fun `onResume with pause menu hidden resumes tick calls`() {
        val (activity, controller) = startedActivity()
        controller.pause()
        Thread.sleep(32)

        val countBeforeResume = activity.tickCount.get()
        activity.pauseMenuVisible = false
        controller.resume()
        Thread.sleep(50)

        val countAfterResume = activity.tickCount.get()
        activity.renderThread.stopThread()

        assertTrue(
            "Ticks should resume after onResume (pauseMenu=false): was $countBeforeResume, now $countAfterResume",
            countAfterResume > countBeforeResume
        )
    }

    // AC-4: onResume with visible menu does NOT resume ticks
    @Test
    fun `onResume with pause menu visible does not resume tick calls`() {
        val (activity, controller) = startedActivity()
        controller.pause()
        Thread.sleep(32)

        val countBeforeResume = activity.tickCount.get()
        activity.pauseMenuVisible = true
        controller.resume()
        Thread.sleep(50)

        val countAfterResume = activity.tickCount.get()
        activity.renderThread.stopThread()

        assertTrue(
            "Ticks must NOT resume when isPauseMenuShown=true (was $countBeforeResume, now $countAfterResume)",
            countAfterResume == countBeforeResume
        )
    }

    // AC-5: onDestroy joins thread within 120ms
    @Test
    fun `onDestroy joins render thread within 120ms`() {
        val (activity, controller) = startedActivity()

        val start = System.currentTimeMillis()
        controller.pause().stop().destroy()
        val elapsed = System.currentTimeMillis() - start

        assertFalse("Thread must not be alive after onDestroy", activity.renderThread.isAlive)
        assertTrue("onDestroy must join within 120ms, took ${elapsed}ms", elapsed < 120)
    }
}
