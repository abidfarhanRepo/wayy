package com.wayy.ar.renderer

import android.opengl.GLES30
import android.util.Log

object ShaderUtils {
    private const val TAG = "WayyShader"

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $type")
            return 0
        }

        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)

        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed: $log")
            Log.e(TAG, "Shader source:\n$shaderCode")
            GLES30.glDeleteShader(shader)
            return 0
        }

        Log.d(TAG, "Shader compiled successfully (type=$type)")
        return shader
    }

    fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to load shaders: vertex=$vertexShader, fragment=$fragmentShader")
            return 0
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create program")
            return 0
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)

        if (linked[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            Log.e(TAG, "Program linking failed: $log")
            GLES30.glDeleteProgram(program)
            return 0
        }

        Log.d(TAG, "Program linked successfully: $program")

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }

    fun checkGLError(operation: String) {
        var error: Int
        var hasError = false
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL error during $operation: 0x${error.toString(16)}")
            hasError = true
        }
        if (!hasError) {
            Log.v(TAG, "GL operation OK: $operation")
        }
    }
}
