package com.wayy.ar.renderer

import android.opengl.GLES30
import android.util.Log
import com.wayy.ar.model.Lane3D
import com.wayy.ar.model.LaneType
import com.wayy.ar.model.Point3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.atan
import kotlin.math.tan

class LaneRenderer {
    private var program: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var vbo: Int = 0
    private var isInitialized = false

    private val vertexShaderCode = """
        #version 300 es
        uniform mat4 uMVPMatrix;
        in vec3 aPosition;
        in vec4 aColor;
        out vec4 vColor;
        
        void main() {
            gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            vColor = aColor;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        in vec4 vColor;
        out vec4 fragColor;
        
        void main() {
            fragColor = vColor;
        }
    """.trimIndent()

    fun initialize() {
        Log.d(TAG, "Initializing LaneRenderer")

        program = ShaderUtils.createProgram(vertexShaderCode, fragmentShaderCode)
        if (program == 0) {
            Log.e(TAG, "Failed to create lane shader program")
            return
        }

        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES30.glGetAttribLocation(program, "aColor")

        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        vbo = buffers[0]

        isInitialized = true
        Log.d(TAG, "LaneRenderer initialized: program=$program, vbo=$vbo")
        ShaderUtils.checkGLError("LaneRenderer.initialize")
    }

    fun draw(mvpMatrix: FloatArray, lanes: List<Lane3D>, imageWidth: Int, imageHeight: Int) {
        if (!isInitialized || lanes.isEmpty()) {
            Log.v(TAG, "Skip draw: initialized=$isInitialized, lanes=${lanes.size}")
            return
        }

        Log.d(TAG, "Drawing ${lanes.size} lanes")
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        for ((index, lane) in lanes.withIndex()) {
            drawLane(lane, imageWidth, imageHeight, index)
        }

        ShaderUtils.checkGLError("LaneRenderer.draw")
    }

    private fun drawLane(lane: Lane3D, imageWidth: Int, imageHeight: Int, index: Int) {
        if (lane.points.size < 2) {
            Log.v(TAG, "Lane $index has insufficient points: ${lane.points.size}")
            return
        }

        val color = getLaneColor(lane.laneType)
        val vertices = mutableListOf<Float>()

        Log.d(TAG, "Lane $index: type=${lane.laneType}, points=${lane.points.size}, color=${color.toList()}")

        for (point in lane.points) {
            vertices.add(point.x)
            vertices.add(point.y)
            vertices.add(point.z)
            vertices.addAll(color.toList())
        }

        val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices.toFloatArray())
                position(0)
            }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            vertexBuffer,
            GLES30.GL_DYNAMIC_DRAW
        )

        val stride = 7 * 4

        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, stride, 0)

        GLES30.glEnableVertexAttribArray(colorHandle)
        GLES30.glVertexAttribPointer(colorHandle, 4, GLES30.GL_FLOAT, false, stride, 12)

        GLES30.glLineWidth(5f)
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, lane.points.size)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(colorHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        Log.v(TAG, "Drew lane $index with ${lane.points.size} points")
    }

    fun pixelToWorld(px: Float, py: Float, imageWidth: Int, imageHeight: Int): Point3D {
        val focalLength = imageHeight * 1.2f
        val cameraHeight = 1.5f

        val yNorm = (py - imageHeight / 2f) / focalLength
        val xNorm = (px - imageWidth / 2f) / focalLength

        val depth = if (yNorm > 0.01f) {
            cameraHeight / tan(atan(yNorm))
        } else {
            100f
        }

        val x = xNorm * depth
        val z = -depth

        Log.v(TAG, "pixelToWorld: px=$px, py=$py -> x=$x, z=$z (depth=$depth)")
        return Point3D(x, 0.05f, z)
    }

    private fun getLaneColor(type: LaneType): FloatArray {
        return when (type) {
            LaneType.LEFT_BOUNDARY -> floatArrayOf(0.64f, 0.9f, 0.21f, 0.9f)
            LaneType.RIGHT_BOUNDARY -> floatArrayOf(0.13f, 0.83f, 0.93f, 0.9f)
            LaneType.CENTER_LINE -> floatArrayOf(1f, 0.84f, 0f, 0.9f)
            LaneType.DASHED -> floatArrayOf(1f, 1f, 1f, 0.7f)
            LaneType.SOLID -> floatArrayOf(1f, 1f, 1f, 0.9f)
            LaneType.UNKNOWN -> floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        }
    }

    fun release() {
        Log.d(TAG, "Releasing LaneRenderer")
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
        }
        isInitialized = false
    }

    companion object {
        private const val TAG = "WayyLaneRenderer"
    }
}
