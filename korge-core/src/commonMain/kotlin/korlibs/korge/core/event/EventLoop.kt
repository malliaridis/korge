package korlibs.korge.core.event

import korlibs.datastructure.pauseable.Pauseable
import korlibs.korge.annotations.KorgeExperimental
import korlibs.time.Frequency
import korlibs.time.hz
import kotlin.time.Duration

interface EventLoop : Pauseable, AutoCloseable {
    companion object

    fun setImmediate(task: () -> Unit)
    fun setTimeout(time: Duration, task: () -> Unit): AutoCloseable
    fun setInterval(time: Duration, task: () -> Unit): AutoCloseable
    fun setIntervalFrame(task: () -> Unit): AutoCloseable = setInterval(60.hz.duration, task)
}

fun EventLoop.setInterval(time: Frequency, task: () -> Unit): AutoCloseable = setInterval(time.duration, task)

/**
 * Creates and returns a new [EventLoop].
 */
@KorgeExperimental
fun createEventLoop(): EventLoop = CoroutineEventLoop()
