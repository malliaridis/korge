package korlibs.korge.core.event

import korlibs.datastructure.TGenPriorityQueue
import korlibs.korge.core.coroutines.KorgeDispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

open class CoroutineEventLoop : EventLoop {
    private var scope: CoroutineScope? = null
    private var loopJob: CompletableJob? = null
    private var pumpJob: Job? = null

    private val tasks = ArrayDeque<() -> Unit>()
    private val timedTasks = TGenPriorityQueue<TimedTask> { a, b -> a.compareTo(b) }
    private val commands = Channel<Command>(Channel.UNLIMITED)

    private val closingFlow = MutableStateFlow(false)
    private val pausedState = MutableStateFlow(false)
    override var paused: Boolean
        get() = pausedState.value
        set(value) { pausedState.value = value }

    private val startTime = TimeSource.Monotonic.markNow()

    var uncaughtExceptionHandler: (Throwable) -> Unit = { it.printStackTrace() }

    internal var nowProvider: () -> Duration = { startTime.elapsedNow() }
    private fun now(): Duration = nowProvider()

    override fun setImmediate(task: () -> Unit) {
        commands.trySend(Command.Run(task, first = false))
    }

    fun setImmediateFirst(task: () -> Unit) {
        commands.trySend(Command.Run(task, first = true))
    }

    fun start(parent: CoroutineScope? = null, dispatcher: CoroutineDispatcher? = null) {
        if (pumpJob != null) return
        closingFlow.value = false
        val parentContext = parent?.coroutineContext ?: EmptyCoroutineContext
        val resolvedDispatcher = dispatcher
            ?: parentContext[ContinuationInterceptor] as? CoroutineDispatcher
            ?: KorgeDispatchers.EventLoop
        val newLoopJob = SupervisorJob(parentContext[Job])
        val newScope = CoroutineScope(parentContext + resolvedDispatcher + newLoopJob)
        scope = newScope
        loopJob = newLoopJob
        pumpJob = newScope.launch { pump() }
    }

    fun stop() {
        loopJob?.cancel() // cancels pumpJob AND completes loopJob itself
        pumpJob = null
        loopJob = null
        scope = null
    }

    override fun close() {
        stop() // fast path: cancel, drop pending work
    }

    suspend fun closeAndJoin() {
        closingFlow.value = true
        commands.trySend(Command.Wake)
        pumpJob?.join()     // pump() returns normally once it observes closingFlow and drains
        loopJob?.complete() // tell the SupervisorJob no more children are coming
        loopJob?.join()     // now it can actually reach Completed
        pumpJob = null
        loopJob = null
        scope = null
    }

    override fun setTimeout(time: Duration, task: () -> Unit): AutoCloseable {
        val timedTask = TimedTask(now(), time, interval = false, callback = task)
        commands.trySend(Command.Schedule(timedTask))
        return timedTask
    }

    override fun setInterval(time: Duration, task: () -> Unit): AutoCloseable {
        val timedTask = TimedTask(now(), time, interval = true, callback = task)
        commands.trySend(Command.Schedule(timedTask))
        return timedTask
    }

    private suspend fun pump() = try {
        while (true) {
            combine(pausedState, closingFlow) { paused, closing -> !paused || closing }.first { it }
            if (closingFlow.value) break

            // Drain everything currently queued — don't interleave one command with one task run.
            while (true) {
                val cmd = commands.tryReceive().getOrNull() ?: break
                handle(cmd)
            }

            runDueTimedTasks()
            runOneImmediateTask()

            val moreWorkNow = tasks.isNotEmpty() ||
                (timedTasks.headOrNull?.let { it.timeMark <= now() } == true)
            if (moreWorkNow) continue // don't block on receive() while there's still runnable work

            val waitTime = timedTasks.headOrNull?.let { (it.timeMark - now()).coerceAtLeast(Duration.ZERO) }
            val cmd = if (waitTime != null) withTimeoutOrNull(waitTime) { commands.receive() } else commands.receive()
            cmd?.let(::handle)
        }
    } finally {
        if (closingFlow.value) drainRemaining()
    }

    private fun handle(cmd: Command) {
        when (cmd) {
            is Command.Run -> if (cmd.first) tasks.addFirst(cmd.task) else tasks.addLast(cmd.task)
            is Command.Schedule -> if (!cmd.task.cancelled) timedTasks.add(cmd.task)
            is Command.Wake -> Unit
        }
    }

    private fun runDueTimedTasks() {
        while (true) {
            val t = timedTasks.headOrNull ?: break
            if (t.cancelled) {
                timedTasks.removeHead()
                continue
            }
            if (t.timeMark > now()) break
            timedTasks.removeHead()
            runCatching { t.callback() }.onFailure(uncaughtExceptionHandler)
            if (!t.cancelled && t.interval && !closingFlow.value) {
                t.timeMark = maxOf(t.timeMark + t.time, now())
                timedTasks.add(t)
            }
        }
    }

    private fun runOneImmediateTask() {
        tasks.removeFirstOrNull()?.let { task ->
            runCatching { task() }.onFailure(uncaughtExceptionHandler)
        }
    }

    private fun drainRemaining() {
        // Only tasks already due get to run, exactly once. Nothing not-yet-due is simulated forward,
        // and interval tasks never re-queue during a graceful close.
        while (true) {
            val t = timedTasks.headOrNull ?: break
            if (t.timeMark > now()) break
            timedTasks.removeHead()
            if (!t.cancelled) runCatching { t.callback() }.onFailure(uncaughtExceptionHandler)
        }
        while (true) {
            val task = tasks.removeFirstOrNull() ?: break
            runCatching { task() }.onFailure(uncaughtExceptionHandler)
        }
    }
}

private val <T> TGenPriorityQueue<T>.headOrNull: T? get() = if (isNotEmpty()) head else null
