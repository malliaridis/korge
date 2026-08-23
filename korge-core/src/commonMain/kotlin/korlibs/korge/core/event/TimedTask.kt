package korlibs.korge.core.event

import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlinx.atomicfu.atomic

class TimedTask(
    var now: Duration,
    val time: Duration,
    @Volatile var interval: Boolean,
    val callback: () -> Unit,
) : Comparable<TimedTask>, AutoCloseable {
    var timeMark: Duration
        get() = now + time
        set(value) { now = value - time }

    private val cancelledFlag = atomic(false) // thread-safe
    val cancelled: Boolean get() = cancelledFlag.value

    override fun compareTo(other: TimedTask): Int = this.timeMark.compareTo(other.timeMark)
    override fun close() {
        cancelledFlag.value = true
    }
}
