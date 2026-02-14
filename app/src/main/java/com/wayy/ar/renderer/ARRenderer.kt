package com.wayy.ar.renderer

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.wayy.ar.model.ARFrame
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARRenderer {
    private var laneRenderer: LaneRenderer? = null
    private var objectRenderer: ObjectRenderer? = null
    private var isInitialized = false

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    @Volatile
    private var currentFrame: ARFrame? = null

    private var frameCount = 0L
    private var lastFpsTime = System.currentTimeMillis()

    fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Initializing OpenGL ES 3.0")

        GLES30.glClearColor(0f, 0f, 0f, 0f)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LESS)

        laneRenderer = LaneRenderer().apply { initialize() }
        objectRenderer = ObjectRenderer().apply { initialize() }

        isInitialized = true
        Log.i(TAG, "onSurfaceCreated: Complete, isInitialized=$isInitialized")
        ShaderUtils.checkGLError("ARRenderer.onSurfaceCreated")
    }

    fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: ${width}x$height")

        surfaceWidth = width
        surfaceHeight = height

        GLES30.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.toFloat()
        val fov = 60f
        val near = 0.1f
        val far = 500f

        Matrix.perspectiveM(projectionMatrix, 0, fov, ratio, near, far)

        Log.d(TAG, "Projection matrix set: fov=$fov, ratio=$ratio, near=$near, far=$far")
        ShaderUtils.checkGLError("ARRenderer.onSurfaceChanged")
    }

    fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (!isInitialized) {
            Log.w(TAG, "onDrawFrame: Not initialized yet")
            return
        }

        val frame = currentFrame
        if (frame == null) {
            Log.v(TAG, "onDrawFrame: No frame data yet")
            return
        }

        updateViewMatrix(frame.cameraPose)

        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val hasLanes = frame.lanes.isNotEmpty()
        val hasObjects = frame.objects.isNotEmpty()

        Log.d(TAG, "onDrawFrame: lanes=${frame.lanes.size}, objects=${frame.objects.size}")

        if (hasLanes) {
            laneRenderer?.draw(mvpMatrix, frame.lanes, frame.imageWidth, frame.imageHeight)
        }

        if (hasObjects) {
            objectRenderer?.draw(mvpMatrix, frame.objects)
        }

        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount * 1000f / (now - lastFpsTime)
            Log.i(TAG, "AR Rendering FPS: ${"%.1f".format(fps)}")
            frameCount = 0
            lastFpsTime = now
        }

        ShaderUtils.checkGLError("ARRenderer.onDrawFrame")
    }

    private fun updateViewMatrix(pose: com.wayy.ar.model.CameraPose) {
        Matrix.setLookAtM(
            viewMatrix, 0,
            pose.x, pose.y, pose.z,
            pose.x, pose.y, pose.z - 10f,
            0f, 1f, 0f
        )

        if (pose.pitch != 0f || pose.yaw != 0f || pose.roll != 0f) {
            val rotation = FloatArray(16)
            val temp = FloatArray(16)
            Matrix.setIdentityM(rotation, 0)
            Matrix.rotateM(rotation, 0, pose.pitch, 1f, 0f, 0f)
            Matrix.rotateM(rotation, 0, pose.yaw, 0f, 1f, 0f)
            Matrix.rotateM(rotation, 0, pose.roll, 0f, 0f, 1f)
            Matrix.multiplyMM(temp, 0, rotation, 0, viewMatrix, 0)
            System.arraycopy(temp, 0, viewMatrix, 0, 16)
        }
    }

    fun updateFrame(frame: ARFrame) {
        currentFrame = frame
        Log.v(TAG, "updateFrame: Updated frame with ${frame.lanes.size} lanes, ${frame.objects.size} objects")
    }

    fun release() {
        Log.i(TAG, "Releasing ARRenderer")
        laneRenderer?.release()
        objectRenderer?.release()
        isInitialized = false
    }

    companion object {
        private const val TAG = "WayyARRenderer"
    }
}
