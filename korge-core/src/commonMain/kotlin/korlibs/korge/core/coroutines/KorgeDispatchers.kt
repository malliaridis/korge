package korlibs.korge.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher

expect object KorgeDispatchers {
    /** The dispatcher korge's core update/event loop runs on. Thread-affine on JVM/Native. */
    val EventLoop: CoroutineDispatcher
}
