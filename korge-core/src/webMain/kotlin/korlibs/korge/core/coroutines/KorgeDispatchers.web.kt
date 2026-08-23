package korlibs.korge.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object KorgeDispatchers {
    actual val EventLoop: CoroutineDispatcher = Dispatchers.Default
}
