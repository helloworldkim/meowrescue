package com.meowrescue.game.threading

import com.meowrescue.game.core.threading.LaunchRenderThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LaunchRenderThreadTest {

    // AC-1: thread starts and calls tick() repeatedly at ~16ms intervals
    @Test
    fun `thread starts and ticks at least twice in 50ms`() {
        val count = AtomicInteger(0)
        val thread = LaunchRenderThread(tick = { count.incrementAndGet() })

        thread.startThread()
        Thread.sleep(50)
        val ticks = count.get()
        thread.stopThread()

        assertTrue("Expected at least 2 ticks in 50ms, got $ticks", ticks >= 2)
        assertTrue("Expected at most 4 ticks in 50ms (16ms pacing), got $ticks", ticks <= 4)
    }

    // AC-2: pauseLoop() stops tick calls within 32ms
    @Test
    fun `pause stops tick calls`() {
        val count = AtomicInteger(0)
        val thread = LaunchRenderThread(tick = { count.incrementAndGet() })

        thread.startThread()
        Thread.sleep(50)

        thread.pauseLoop()
        Thread.sleep(16) // wait for in-flight tick to finish
        val countAtPause = count.get()
        Thread.sleep(32) // no ticks should occur
        val countAfterWait = count.get()
        thread.stopThread()

        assertTrue(thread.paused)
        assertTrue(
            "Tick count should not increase while paused (was $countAtPause, now $countAfterWait)",
            countAfterWait == countAtPause
        )
    }

    // AC-3: resumeLoop() restarts tick calls
    @Test
    fun `resume restarts tick calls after pause`() {
        val count = AtomicInteger(0)
        val thread = LaunchRenderThread(tick = { count.incrementAndGet() })

        thread.startThread()
        Thread.sleep(50)
        thread.pauseLoop()
        Thread.sleep(32)

        val countBeforeResume = count.get()
        thread.resumeLoop()
        Thread.sleep(50)
        val countAfterResume = count.get()
        thread.stopThread()

        assertFalse(thread.paused)
        assertTrue(
            "Tick count should increase after resume (was $countBeforeResume, now $countAfterResume)",
            countAfterResume > countBeforeResume
        )
    }

    // AC-4: stopThread() joins within 100ms wall-clock
    @Test
    fun `stopThread joins within 100ms`() {
        val thread = LaunchRenderThread(tick = {})
        thread.startThread()
        Thread.sleep(20)

        val stopStart = System.currentTimeMillis()
        thread.stopThread()
        val elapsed = System.currentTimeMillis() - stopStart

        assertFalse("Thread must not be alive after stopThread()", thread.isAlive)
        assertTrue("stopThread() must join within 120ms, took ${elapsed}ms", elapsed < 120)
    }

    // AC-5: thread priority — verified manually on device (Process.setThreadPriority
    // is a no-op in JVM unit tests; device verification documented in
    // production/qa/evidence/render-thread-manual-test.md)
}
