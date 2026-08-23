package korlibs.korge.tests

import korlibs.concurrent.lock.NonRecursiveLock
import korlibs.datastructure.PriorityQueue
import korlibs.time.milliseconds
import korlibs.time.millisecondsLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable

@OptIn(InternalCoroutinesApi::class)
@Deprecated("")
class TestCoroutineDispatcher(val frameTime: Duration = 16.milliseconds) :
	CoroutineDispatcher(), ContinuationInterceptor, Delay {
	var time = 0L; private set

	class TimedTask(val time: Long, val callback: suspend () -> Unit) {
		override fun toString(): String = "TimedTask(time=$time)"
	}

	private val tasks = PriorityQueue<TimedTask>(Comparator { a, b -> a.time.compareTo(b.time) })
    private val lock = NonRecursiveLock()

	private fun scheduleAfter(time: Int, callback: suspend () -> Unit) {
        lock {
            tasks += TimedTask(this.time + time) {
                callback()
            }
        }
	}

	override fun dispatch(context: CoroutineContext, block: Runnable) {
		scheduleAfter(0) { block.run() }
	}

	override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
		scheduleAfter(timeMillis.toInt()) { continuation.resume(Unit) }
	}

	var exception: Throwable? = null
	fun loop() {
		if (exception != null) throw exception ?: error("error")
		while (true) {
			val task = lock { if (tasks.isNotEmpty()) tasks.removeHead() else null } ?: break
			this.time = task.time

            // @TODO: This is probably wrong
			task.callback.startCoroutine(object : Continuation<Unit> {
				override val context: CoroutineContext = this@TestCoroutineDispatcher

				override fun resumeWith(result: Result<Unit>) {
					val exception = result.exceptionOrNull()
					exception?.printStackTrace()
					this@TestCoroutineDispatcher.exception = exception
				}
			})
		}
	}

    @Deprecated("Use loop instead if possible")
    suspend fun step(time: Duration) {
        this.time += time.millisecondsLong
        while (true) {
            val task = lock { if (tasks.isNotEmpty() && this.time >= tasks.head.time) tasks.removeHead() else null } ?: break
            task.callback()
        }
    }

	fun loop(entry: suspend () -> Unit) {
		entry.startCoroutine(object : Continuation<Unit> {
			override val context: CoroutineContext = this@TestCoroutineDispatcher

			override fun resumeWith(result: Result<Unit>) {
				val exception = result.exceptionOrNull()
				exception?.printStackTrace()
				this@TestCoroutineDispatcher.exception = exception
			}
		})
		loop()
	}
}
