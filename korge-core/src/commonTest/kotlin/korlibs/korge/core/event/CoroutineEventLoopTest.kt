package korlibs.korge.core.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineEventLoopTest {

    private fun TestScope.newLoop(): CoroutineEventLoop {
        val loop = CoroutineEventLoop()
        loop.nowProvider = { testScheduler.currentTime.milliseconds }
        loop.start(parent = this) // picks up this TestScope's StandardTestDispatcher automatically
        return loop
    }

    @Test
    fun setImmediate_runs_the_task() = runTest {
        val loop = newLoop()
        var ran = false
        loop.setImmediate { ran = true }
        advanceUntilIdle()
        assertTrue(ran)
        loop.close()
    }

    @Test
    fun setImmediateFirst_runs_before_previously_queued_tasks() = runTest {
        val loop = newLoop()
        val order = mutableListOf<String>()
        loop.setImmediate { order += "a" }
        loop.setImmediateFirst { order += "b" }
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), order)
        loop.close()
    }

    @Test
    fun setTimeout_runs_only_after_the_delay() = runTest {
        val loop = newLoop()
        var ran = false
        loop.setTimeout(100.milliseconds) { ran = true }

        advanceTimeBy(50.milliseconds); runCurrent()
        assertFalse(ran, "should not have run yet")

        advanceTimeBy(51.milliseconds); runCurrent()
        assertTrue(ran)
        loop.close()
    }

    @Test
    fun setTimeout_handle_close_cancels_pending_task() = runTest {
        val loop = newLoop()
        var ran = false
        val handle = loop.setTimeout(100.milliseconds) { ran = true }
        handle.close()

        advanceTimeBy(200.milliseconds); runCurrent()
        assertFalse(ran)
        loop.close()
    }

    @Test
    fun setInterval_runs_repeatedly_and_stops_after_cancel() = runTest {
        val loop = newLoop()
        var count = 0
        val handle = loop.setInterval(10.milliseconds) { count++ }

        advanceTimeBy(35.milliseconds); runCurrent()
        assertEquals(3, count)

        handle.close()
        advanceTimeBy(50.milliseconds); runCurrent()
        assertEquals(3, count, "should not fire after cancelling")
        loop.close()
    }

    @Test
    fun paused_defers_execution_until_resumed() = runTest {
        val loop = newLoop()
        var ran = false
        loop.paused = true
        loop.setImmediate { ran = true }

        advanceUntilIdle()
        assertFalse(ran)

        loop.paused = false
        advanceUntilIdle()
        assertTrue(ran)
        loop.close()
    }

    @Test
    fun exception_in_task_is_caught_and_does_not_stop_the_loop() = runTest {
        val loop = newLoop()
        val caught = mutableListOf<Throwable>()
        loop.uncaughtExceptionHandler = { caught += it }

        var secondRan = false
        loop.setImmediate { throw IllegalStateException("boom") }
        loop.setImmediate { secondRan = true }

        advanceUntilIdle()
        assertEquals(1, caught.size)
        assertTrue(secondRan)
        loop.close()
    }

    @Test
    fun close_cancels_the_pump_and_drops_pending_work() = runTest {
        val loop = newLoop()
        var ran = false
        loop.setTimeout(1000.milliseconds) { ran = true }
        loop.close()

        advanceUntilIdle()
        assertFalse(ran)
    }

    @Test
    fun closeAndJoin_drains_due_and_immediate_work_but_intervals_fire_once() = runTest {
        val loop = newLoop()
        val ran = mutableListOf<String>()

        loop.setImmediate { ran += "immediate" }
        loop.setInterval(10.milliseconds) { ran += "interval" }
        loop.setTimeout(1000.milliseconds) { ran += "far-future" } // not due

        advanceTimeBy(10.milliseconds); runCurrent() // let the interval become due once
        loop.closeAndJoin()

        assertTrue("immediate" in ran)
        assertEquals(1, ran.count { it == "interval" }, "interval must fire once, not requeue during drain")
        assertFalse("far-future" in ran)
    }

    @Test
    fun start_is_idempotent() = runTest {
        val loop = newLoop()
        loop.start(parent = this) // second call is a no-op, must not throw or restart

        var ran = false
        loop.setImmediate { ran = true }
        advanceUntilIdle()
        assertTrue(ran)
        loop.close()
    }

    @Test
    fun stop_then_start_resumes_processing_of_queued_work() = runTest {
        val loop = newLoop()
        loop.stop()

        var ran = false
        loop.setImmediate { ran = true } // queued while stopped
        advanceUntilIdle()
        assertFalse(ran, "nothing should run while stopped")

        loop.start(parent = this)
        advanceUntilIdle()
        assertTrue(ran)
        loop.close()
    }

    @Test
    fun closeAndJoin_runs_only_due_timers() = runTest {
        val loop = newLoop()
        val ran = mutableListOf<String>()

        loop.setTimeout(0.milliseconds) { ran += "due" }
        loop.setTimeout(1000.milliseconds) { ran += "not-due" }
        runCurrent() // let the due-immediately timer register as due

        val closeJob = launch { loop.closeAndJoin() }
        advanceUntilIdle()
        closeJob.join()

        assertEquals(listOf("due"), ran)
    }

    @Test
    fun closeAndJoin_works_while_paused() = runTest {
        val loop = newLoop()
        loop.paused = true

        var ran = false
        loop.setImmediate { ran = true }

        val closeJob = launch { loop.closeAndJoin() }
        advanceUntilIdle()
        closeJob.join() // must not hang

        assertFalse(ran, "loop was paused, queued task should not have run during drain")
    }
}
