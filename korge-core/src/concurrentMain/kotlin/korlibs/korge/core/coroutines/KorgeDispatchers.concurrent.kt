package korlibs.korge.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
actual object KorgeDispatchers {
    // newSingleThreadContext leaks a real thread until explicitly .close()'d, and JetBrains' docs warn specifically
    // against closing it on Kotlin/Native while coroutines might still be running on it. Since
    // [KorgeDispatchers.EventLoop] is meant to live for the whole app lifetime as a singleton, that's the correct
    // usage pattern (never closed).
    actual val EventLoop: CoroutineDispatcher by lazy {
        newSingleThreadContext("KorgeEventLoop")
    }
}
