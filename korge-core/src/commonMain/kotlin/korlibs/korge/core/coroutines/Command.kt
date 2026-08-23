package korlibs.korge.core

import korlibs.korge.core.coroutines.TimedTask

sealed interface Command {
    class Run(val task: () -> Unit, val first: Boolean) : Command
    class Schedule(val task: TimedTask) : Command
    data object Wake : Command
}
