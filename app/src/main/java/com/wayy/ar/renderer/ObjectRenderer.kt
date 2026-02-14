package com.wayy.ar.renderer

import android.opengl.GLES30
import android.util.Log
import com.wayy.ar.model.Dimensions3D
import com.wayy.ar.model.Object3D
import com.wayy.ar.model.Point3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.atan
import kotlin.math.tan

class ObjectRenderer {
    private var program: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var vbo: Int = 0
    private var ibo: Int = 0
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
        Log.d(TAG, "Initializing ObjectRenderer")

        program = ShaderUtils.createProgram(vertexShaderCode, fragmentShaderCode)
        if (program == 0) {
            Log.e(TAG, "Failed to create object shader program")
            return
        }

        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES30.glGetAttribLocation(program, "aColor")

        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vbo = buffers[0]
        ibo = buffers[1]

        isInitialized = true
        Log.d(TAG, "ObjectRenderer initialized: program=$program, vbo=$vbo, ibo=$ibo")
        ShaderUtils.checkGLError("ObjectRenderer.initialize")
    }

    fun draw(mvpMatrix: FloatArray, objects: List<Object3D>) {
        if (!isInitialized) {
            Log.w(TAG, "ObjectRenderer not initialized")
            return
        }

        if (objects.isEmpty()) {
            Log.v(TAG, "No objects to draw")
            return
        }

        Log.d(TAG, "Drawing ${objects.size} objects")
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        for ((index, obj) in objects.withIndex()) {
            drawBoundingBox(obj, index)
        }

        ShaderUtils.checkGLError("ObjectRenderer.draw")
    }

    private fun drawBoundingBox(obj: Object3D, index: Int) {
        Log.d(TAG, "Drawing object $index: class=${obj.className}, pos=(${obj.position.x}, ${obj.position.y}, ${obj.position.z}), conf=${obj.confidence}")

        val (x, y, z) = obj.position
        val hw = obj.dimensions.width / 2f
        val hh = obj.dimensions.height / 2f
        val hd = obj.dimensions.depth / 2f

        val vertices = floatArrayOf(
            x - hw, y, z - hd,
            x + hw, y, z - hd,
            x + hw, y, z + hd,
            x - hw, y, z + hd,
            x - hw, y + 2f * hh, z - hd,
            x + hw, y + 2f * hh, z - hd,
            x + hw, y + 2f * hh, z + hd,
            x - hw, y + 2f * hh, z + hd
        )

        val color = getObjectColor(obj.className, obj.confidence)

        val vertexData = mutableListOf<Float>()
        for (i in vertices.indices step 3) {
            vertexData.add(vertices[i])
            vertexData.add(vertices[i + 1])
            vertexData.add(vertices[i + 2])
            vertexData.addAll(color.toList())
        }

        val indices = shortArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7
        )

        val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData.toFloatArray())
                position(0)
            }

        val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexData.size * 4, vertexBuffer, GLES30.GL_DYNAMIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexBuffer, GLES30.GL_DYNAMIC_DRAW)

        val stride = 7 * 4

        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, stride, 0)

        GLES30.glEnableVertexAttribArray(colorHandle)
        GLES30.glVertexAttribPointer(colorHandle, 4, GLES30.GL_FLOAT, false, stride, 12)

        GLES30.glLineWidth(3f)
        GLES30.glDrawElements(GLES30.GL_LINES, indices.size, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(colorHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        Log.v(TAG, "Drew bounding box for object $index")
    }

    fun estimateObjectHeight(className: String): Float {
        return when (className.lowercase()) {
            "car" -> 1.5f
            "truck", "bus" -> 3.0f
            "pedestrian", "person" -> 1.7f
            "bicycle", "motorcycle" -> 1.2f
            else -> 1.5f
        }
    }

    fun estimateObjectWidth(className: String): Float {
        return when (className.lowercase()) {
            "car" -> 1.8f
            "truck", "bus" -> 2.5f
            "pedestrian", "person" -> 0.5f
            "bicycle", "motorcycle" -> 0.8f
            else -> 1.5f
        }
    }

    fun bboxTo3D(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        imageWidth: Int,
        imageHeight: Int,
        className: String
    ): Object3D? {
        val depth = estimateDepthFromBbox(height, imageHeight)
        if (depth < 0) {
            Log.w(TAG, "Invalid depth estimation for $className")
            return null
        }

        val focalLength = imageHeight * 1.2f
        val x = (centerX - imageWidth / 2f) / focalLength * depth
        val z = -depth
        val objHeight = estimateObjectHeight(className)
        val objWidth = estimateObjectWidth(className)

        Log.d(TAG, "bboxTo3D: center=($centerX, $centerY) -> 3D=($x, 0, $z), depth=$depth, class=$className")

        return Object3D(
            position = Point3D(x, 0f, z),
            dimensions = Dimensions3D(objWidth, objHeight, objWidth),
            className = className,
            confidence = 1f
        )
    }

    private fun estimateDepthFromBbox(bboxHeight: Float, imageHeight: Int): Float {
        val focalLength = imageHeight * 1.2f
        val realHeight = 1.5f

        if (bboxHeight <= 0) return -1f

        val depth = (focalLength * realHeight) / bboxHeight
        return depth.coerceIn(1f, 150f)
    }

    private fun getObjectColor(className: String, confidence: Float): FloatArray {
        val alpha = (0.5f + confidence * 0.5f).coerceIn(0.5f, 1f)
        return when (className.lowercase()) {
            "car" -> floatArrayOf(0.3f, 0.64f, 1f, alpha)
            "truck", "bus" -> floatArrayOf(0.98f, 0.57f, 0.24f, alpha)
            "pedestrian", "person" -> floatArrayOf(1f, 0.3f, 0.3f, alpha)
            "bicycle", "motorcycle" -> floatArrayOf(1f, 0.84f, 0f, alpha)
            else -> floatArrayOf(0.5f, 0.5f, 1f, alpha)
        }
    }

    fun release() {
        Log.d(TAG, "Releasing ObjectRenderer")
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        }
        if (ibo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(ibo), 0)
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
        }
        isInitialized = false
    }

    companion object {
        private const val TAG = "WayyObjectRenderer"
    }
}
