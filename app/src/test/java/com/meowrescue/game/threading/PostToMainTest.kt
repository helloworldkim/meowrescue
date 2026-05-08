package com.meowrescue.game.threading

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.meowrescue.game.core.threading.postToMain
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostToMainTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
    }

    // AC-1: block is posted to Main thread when called from a background thread
    @Test
    fun `block is invoked on Main thread when called from background thread`() {
        val latch = CountDownLatch(1)
        val capturedThread = AtomicReference<Thread>()

        Thread {
            activity.postToMain {
                capturedThread.set(Thread.currentThread())
                latch.countDown()
            }
        }.apply { isDaemon = true; start() }

        Thread.sleep(20) // let background thread post to main looper
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Block should execute within 100ms", latch.await(100, TimeUnit.MILLISECONDS))
        assertSame(
            "Block must run on Main thread, not on background thread",
            Looper.getMainLooper().thread,
            capturedThread.get()
        )
    }

    // AC-2: block is invoked synchronously (no post) when already on Main thread
    @Test
    fun `block is invoked synchronously when called from Main thread`() {
        var blockRanBeforeReturn = false
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            var flag = false
            activity.postToMain { flag = true }
            // If inline (synchronous), flag is already true here before any looper drain.
            blockRanBeforeReturn = flag
            latch.countDown()
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS))
        assertTrue(
            "postToMain called from Main must run block inline (synchronously)",
            blockRanBeforeReturn
        )
    }
}
