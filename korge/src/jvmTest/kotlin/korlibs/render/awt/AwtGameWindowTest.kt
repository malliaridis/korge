package korlibs.render.awt

import korlibs.concurrent.thread.NativeThread
import korlibs.concurrent.thread.sleep
import korlibs.image.color.Colors
import korlibs.image.format.readBitmap
import korlibs.io.async.launchUnscoped
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.view.Views
import korlibs.korge.view.addFastUpdater
import korlibs.korge.view.image
import korlibs.korge.view.solidRect
import korlibs.time.Stopwatch
import korlibs.time.seconds
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.coroutines.delay

class AwtGameWindowTest {
    @Test
    @Ignore
    fun test() {
        val gameWindow = NewAwtGameWindow()

        //gameWindow.icon = runBlocking { resourcesVfs["korge.png"].readBitmap() }

        val views = Views(gameWindow)
        gameWindow.onRenderEvent {
            views.renderNew()
        }
        val stopwatch = Stopwatch()
        gameWindow.onUpdateEvent {
            //println(NativeThread.currentThreadName)
            views.update(stopwatch.getElapsedAndRestart())
            //println("UPDATE EVENT!")
        }
        gameWindow.launchUnscoped {
            views.stage.image(resourcesVfs["korge.png"].readBitmap())
            views.stage.solidRect(100, 100, Colors.RED).addFastUpdater {
                this.x++
            }
            println("[1]")
            delay(1.seconds)
            println("[2]")
        }
        //gameWindow.eventQueueLater { println("[0]") }
        //gameWindow.coroutineDispatcher.queue { println("[1]") }
        println("gameWindow=$gameWindow")
        //gameWindow.coroutineDispatcher.queue { println("[2]") }
        //gameWindow.show()
        //gameWindow.mainLoop { println("HELLO") }
        gameWindow.show()
        NativeThread.sleep(100.seconds)
    }
}
