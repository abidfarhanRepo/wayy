package com.wayy.ar.renderer

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.wayy.ar.model.ARFrame
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AROverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GLSurfaceView(context, attrs) {

    private val renderer: ARRenderer
    private var isReady = false

    init {
        Log.i(TAG, "AROverlayView: Initializing OpenGL ES 3.0 surface")

        setEGLContextClientVersion(3)
        setZOrderOnTop(true)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)

        renderer = ARRenderer()
        setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                Log.d(TAG, "GLSurfaceView.Renderer.onSurfaceCreated")
                renderer.onSurfaceCreated(gl, config)
                isReady = true
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                Log.d(TAG, "GLSurfaceView.Renderer.onSurfaceChanged: ${width}x$height")
                renderer.onSurfaceChanged(gl, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                renderer.onDrawFrame(gl)
            }
        })

        renderMode = RENDERMODE_CONTINUOUSLY

        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        Log.i(TAG, "AROverlayView: Initialization complete")
    }

    fun updateARFrame(frame: ARFrame) {
        if (!isReady) {
            Log.v(TAG, "updateARFrame: Surface not ready yet")
            return
        }
        renderer.updateFrame(frame)
    }

    fun isRendererReady(): Boolean = isReady

    override fun onDetachedFromWindow() {
        Log.i(TAG, "AROverlayView: onDetachedFromWindow")
        super.onDetachedFromWindow()
        renderer.release()
        isReady = false
    }

    companion object {
        private const val TAG = "WayyAROverlayView"
    }
}
