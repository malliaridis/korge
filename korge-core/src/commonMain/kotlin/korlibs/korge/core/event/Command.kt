package korlibs.korge.core.event

sealed interface Command {
    class Run(val task: () -> Unit, val first: Boolean) : Command
    class Schedule(val task: TimedTask) : Command
    data object Wake : Command
}
