package korlibs.render.awt

import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JFrame
import javax.swing.JLabel
import korlibs.concurrent.lock.Lock
import korlibs.concurrent.thread.NativeThread
import korlibs.concurrent.thread.nativeThread
import korlibs.concurrent.thread.sleep
import korlibs.datastructure.event.SyncEventLoop
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.kgl.KmlGl
import korlibs.korge.render.RenderContext
import korlibs.platform.Platform
import korlibs.render.osx.autoreleasePool
import korlibs.time.hz
import korlibs.time.seconds
import kotlin.math.sin
import kotlin.test.Ignore
import kotlin.test.Test

class AwtGameCanvasTest {
    @Test
    @Ignore
    fun test() = autoreleasePool {
        //System.setProperty("sun.java2d.metal", "true")
        //System.setProperty("sun.java2d.opengl", "false")
        System.setProperty("sun.java2d.opengl", "true")
        //System.setProperty("sun.java2d.opengl", "false")

        val frame = object : JFrame("Title") {
        }
        val bmp = Bitmap32(128, 128) { x, y -> Colors.BLACK.withB(x * 2).withG(y * 2) }.premultiplied()
        val bmp2 = Bitmap32(128, 128) { x, y -> Colors.BLACK.withR(x * 2).withG(y * 2) }.premultiplied()

        val el = SyncEventLoop()
        val viewsLock = Lock()
        var canvas: Component? = null
        var fpsLabel: JLabel? = null

        var x = 0

        el.setInterval(60.hz.timeSpan) {
            viewsLock {
                x++
                if (!Platform.isMac) {
                    canvas?.repaint()
                }
            }
        }

        nativeThread {
            el.runTasksForever()
        }

        frame.contentPane.layout = GridLayout(2, 2)
        frame.contentPane.add(AwtAGOpenglCanvas().also {
            canvas = it
            val renderContext = RenderContext(it.ag, it)
            it.doRender = { ag ->
                renderContext.doRenderNew {
                    //println(renderContext.currentWidth)
                    renderContext.clear(Colors.LIGHTPINK)
                    viewsLock {
                        renderContext.drawBitmapXY(renderContext.currentFrameBuffer, bmp, (sin(x.toFloat() / 50f) * 200 + 200).toInt(), 100)
                        //SolidRect(100, 100, Colors.MEDIUMPURPLE).render(renderContext)
                    }
                }
                fpsLabel?.text = "FPS: ${(canvas as AwtAGOpenglCanvas).renderFps}"
            }
        })

        frame.contentPane.add(JLabel().also {
            fpsLabel = it
            it.isOpaque = true; it.background = Color.YELLOW })
        frame.contentPane.add(JLabel().also { it.isOpaque = true; it.background = Color.GREEN })
        frame.contentPane.add(GLCanvas().also {
            it.defaultRenderer = { gl, g ->
                gl.clearColor(1f, .5f, 1f, 1f)
                gl.clear(KmlGl.COLOR_BUFFER_BIT)
            }
        })

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.preferredSize = Dimension(600, 600)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        NativeThread.sleep(100.seconds)
    }
}
