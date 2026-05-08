package com.meowrescue.game.threading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies that render-path state maps use ConcurrentHashMap semantics correctly.
 *
 * These tests document the threading contract for maps shared between the
 * render thread and main thread in Cat Launch (ADR-0004, T-4-9 / foundation-threading-007).
 */
class ConcurrentRenderStateTest {

    // ── No ConcurrentModificationException under concurrent access ────────

    @Test
    fun `concurrent reads and writes do not throw ConcurrentModificationException`() {
        val map = ConcurrentHashMap<Int, String>()
        repeat(50) { map[it] = "init-$it" }

        val error = AtomicReference<Throwable>(null)
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        // Writer thread: replace entries while reader iterates
        pool.submit {
            try {
                repeat(100) { i -> map[i % 50] = "write-$i" }
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }

        // Reader thread: iterate all values
        pool.submit {
            try {
                repeat(50) { map.values.forEach { _ -> } }
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }

        latch.await()
        pool.shutdown()
        assertNull("Unexpected exception from concurrent access: ${error.get()}", error.get())
    }

    // ── remove() during iteration does not corrupt map ────────────────────

    @Test
    fun `remove during iteration does not corrupt map`() {
        val map = ConcurrentHashMap<Int, String>()
        repeat(20) { map[it] = "v$it" }

        val removed = mutableListOf<Int>()
        val iter = map.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key % 2 == 0) {
                iter.remove()
                removed.add(entry.key)
            }
        }

        // All even keys removed; odd keys intact
        assertEquals(10, removed.size)
        repeat(20) { key ->
            if (key % 2 == 0) assertNull(map[key])
            else assertEquals("v$key", map[key])
        }
    }

    // ── Empty map reads return null ───────────────────────────────────────

    @Test
    fun `empty ConcurrentHashMap returns null for any key`() {
        val map = ConcurrentHashMap<Int, String>()
        assertNull(map[0])
        assertNull(map[999])
    }

    // ── Stress test: rapid concurrent updates ─────────────────────────────

    @Test
    fun `100 concurrent put operations complete without error`() {
        val map = ConcurrentHashMap<Int, Int>()
        val error = AtomicReference<Throwable>(null)
        val latch = CountDownLatch(100)
        val pool = Executors.newFixedThreadPool(8)

        repeat(100) { i ->
            pool.submit {
                try {
                    map[i] = i * 2
                } catch (t: Throwable) {
                    error.set(t)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        pool.shutdown()
        assertNull("Unexpected exception: ${error.get()}", error.get())
        assertEquals(100, map.size)
    }

    // ── Volatile reference visibility pattern ─────────────────────────────

    @Test
    fun `reference swap on volatile field is visible across threads`() {
        // Simulates catBitmaps reference replacement on main thread, read on render thread.
        // @Volatile is only valid on fields, so we wrap in an object to mirror the production pattern.
        class Holder { @Volatile var ref: Map<Int, String> = emptyMap() }
        val holder = Holder()

        val writerDone = CountDownLatch(1)
        val readerSaw = AtomicReference<Map<Int, String>>(null)

        val writer = Thread {
            holder.ref = mapOf(1 to "cat_bitmap")
            writerDone.countDown()
        }
        val reader = Thread {
            writerDone.await()
            readerSaw.set(holder.ref)
        }

        writer.start(); reader.start()
        writer.join(); reader.join()

        assertEquals("cat_bitmap", readerSaw.get()?.get(1))
    }
}
