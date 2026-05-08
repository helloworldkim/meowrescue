package com.meowrescue.game.threading

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LifecycleScopeCancellationTest {

    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `lifecycleScope cancels coroutine on DESTROYED`() {
        val owner = FakeOwner()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val job: Job = owner.lifecycleScope.launch { delay(Long.MAX_VALUE) }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("Job should be active while lifecycle is STARTED", job.isCancelled)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Job must be cancelled when lifecycle reaches DESTROYED", job.isCancelled)
    }

    @Test
    fun `lifecycleScope with repeatOnLifecycle cancels on DESTROYED`() {
        val owner = FakeOwner()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val outerJob: Job = owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { delay(Long.MAX_VALUE) }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("Outer job should be active while lifecycle is STARTED", outerJob.isCancelled)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Outer job must no longer be active when lifecycle reaches DESTROYED", !outerJob.isActive)
    }
}
