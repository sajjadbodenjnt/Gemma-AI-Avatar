package com.gemma.avatar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight procedural 3D companion rendered locally with OpenGL ES 2.0.
 * This is a stylized 3D character, not a scanned/photorealistic human model.
 */
class Mira3DView(context: Context) : GLSurfaceView(context) {
    enum class Expression { NEUTRAL, HAPPY, SAD, SURPRISED }

    @Volatile var talking = false
        set(value) { field = value; renderer.talking = value }
    @Volatile var expression = Expression.NEUTRAL
        set(value) { field = value; renderer.expression = value }
    @Volatile var userSmiling = false
        set(value) { field = value; renderer.userSmiling = value }

    private val renderer = MiraRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setBackgroundColor(0xff0b1427.toInt())
    }
}

private class MiraRenderer : GLSurfaceView.Renderer {
    @Volatile var talking = false
    @Volatile var expression = Mira3DView.Expression.NEUTRAL
    @Volatile var userSmiling = false

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private lateinit var sphere: SphereMesh
    private var startTime = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.065f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        sphere = SphereMesh(28, 36)
        startTime = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 38f, ratio, 0.1f, 40f)
        Matrix.setLookAtM(view, 0, 0f, 0.05f, 6.4f, 0f, 0.05f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val t = (System.nanoTime() - startTime) / 1_000_000_000.0
        val bob = (sin(t * if (talking) 5.2 else 1.8) * if (talking) 0.045 else 0.018).toFloat()
        val armSwing = (sin(t * if (talking) 4.5 else 1.5) * if (talking) 19.0 else 5.0).toFloat()
        val happy = expression == Mira3DView.Expression.HAPPY || userSmiling
        val mouthOpen = if (talking) (0.045 + 0.075 * (0.5 + 0.5 * sin(t * 11.0))).toFloat() else 0.025f
        val mouthWidth = if (happy) 0.22f else 0.15f
        val mouthY = when (expression) {
            Mira3DView.Expression.SAD -> 0.285f
            Mira3DView.Expression.SURPRISED -> 0.31f
            else -> 0.34f
        }

        // Long hair behind the shoulders and face.
        ellipsoid(0f, 0.38f + bob, -0.18f, 0.76f, 1.18f, 0.34f, 0.105f, 0.065f, 0.085f)
        ellipsoid(-0.58f, 0.02f + bob, -0.02f, 0.19f, 0.92f, 0.22f, 0.12f, 0.075f, 0.09f)
        ellipsoid(0.58f, 0.02f + bob, -0.02f, 0.19f, 0.92f, 0.22f, 0.12f, 0.075f, 0.09f)

        // Torso, neck and shoulders.
        ellipsoid(0f, -1.05f + bob, 0f, 0.72f, 0.88f, 0.37f, 0.78f, 0.82f, 0.79f)
        ellipsoid(0f, -0.43f + bob, 0.02f, 0.20f, 0.32f, 0.19f, 0.78f, 0.52f, 0.40f)
        ellipsoid(-0.68f, -0.78f + bob, 0f, 0.20f, 0.62f, 0.20f, 0.78f, 0.82f, 0.79f, armSwing)
        ellipsoid(0.68f, -0.78f + bob, 0f, 0.20f, 0.62f, 0.20f, 0.78f, 0.82f, 0.79f, -armSwing)

        // Face and hair cap.
        ellipsoid(0f, 0.48f + bob, 0.12f, 0.57f, 0.73f, 0.39f, 0.91f, 0.70f, 0.59f)
        ellipsoid(0f, 0.93f + bob, 0.02f, 0.63f, 0.43f, 0.37f, 0.12f, 0.075f, 0.09f)
        ellipsoid(-0.49f, 0.48f + bob, 0.17f, 0.12f, 0.31f, 0.15f, 0.12f, 0.075f, 0.09f)
        ellipsoid(0.49f, 0.48f + bob, 0.17f, 0.12f, 0.31f, 0.15f, 0.12f, 0.075f, 0.09f)

        // Fringe, brows, eyes and irises.
        ellipsoid(0f, 0.94f + bob, 0.40f, 0.49f, 0.19f, 0.10f, 0.105f, 0.065f, 0.085f)
        ellipsoid(-0.22f, 0.63f + bob, 0.475f, 0.105f, 0.065f, 0.045f, 0.98f, 0.96f, 0.91f)
        ellipsoid(0.22f, 0.63f + bob, 0.475f, 0.105f, 0.065f, 0.045f, 0.98f, 0.96f, 0.91f)
        ellipsoid(-0.22f, 0.625f + bob, 0.518f, 0.044f, 0.049f, 0.025f, 0.19f, 0.12f, 0.085f)
        ellipsoid(0.22f, 0.625f + bob, 0.518f, 0.044f, 0.049f, 0.025f, 0.19f, 0.12f, 0.085f)
        ellipsoid(-0.22f, 0.63f + bob, 0.54f, 0.018f, 0.022f, 0.012f, 0.025f, 0.02f, 0.02f)
        ellipsoid(0.22f, 0.63f + bob, 0.54f, 0.018f, 0.022f, 0.012f, 0.025f, 0.02f, 0.02f)
        ellipsoid(0f, 0.47f + bob, 0.515f, 0.045f, 0.11f, 0.04f, 0.78f, 0.52f, 0.43f)

        // Mouth changes shape while speaking and with simple expression cues.
        val mouthHeight = if (expression == Mira3DView.Expression.SURPRISED) 0.105f else mouthOpen
        ellipsoid(0f, mouthY + bob, 0.505f, mouthWidth, mouthHeight, 0.035f, 0.34f, 0.075f, 0.10f)
        if (happy) {
            ellipsoid(0f, mouthY - 0.055f + bob, 0.535f, 0.15f, 0.018f, 0.015f, 0.98f, 0.87f, 0.78f)
        }

        // Small earrings and collar accents.
        ellipsoid(-0.57f, 0.30f + bob, 0.22f, 0.035f, 0.07f, 0.035f, 0.83f, 0.68f, 0.39f)
        ellipsoid(0.57f, 0.30f + bob, 0.22f, 0.035f, 0.07f, 0.035f, 0.83f, 0.68f, 0.39f)
    }

    private fun ellipsoid(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        rotateZ: Float = 0f
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotateZ != 0f) Matrix.rotateM(model, 0, rotateZ, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform3f(colorHandle, r, g, b)
        sphere.draw(positionHandle, normalHandle)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        return p
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            varying vec3 vNormal;
            void main() {
                gl_Position = uMvp * aPosition;
                vNormal = normalize(mat3(uModel) * aNormal);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uColor;
            varying vec3 vNormal;
            void main() {
                vec3 lightDir = normalize(vec3(-0.35, 0.65, 0.8));
                float diffuse = max(dot(normalize(vNormal), lightDir), 0.0);
                float light = 0.42 + diffuse * 0.68;
                gl_FragColor = vec4(uColor * light, 1.0);
            }
        """
    }
}

private class SphereMesh(latitudeBands: Int, longitudeBands: Int) {
    private val vertices: FloatBuffer
    private val normals: FloatBuffer
    private val indices: java.nio.ShortBuffer
    private val indexCount: Int

    init {
        val data = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (lat in 0..latitudeBands) {
            val theta = lat * Math.PI.toFloat() / latitudeBands
            val st = sin(theta); val ct = cos(theta)
            for (lon in 0..longitudeBands) {
                val phi = lon * 2f * Math.PI.toFloat() / longitudeBands
                val x = cos(phi) * st
                val y = ct
                val z = sin(phi) * st
                data.add(x); data.add(y); data.add(z)
            }
        }
        for (lat in 0 until latitudeBands) {
            for (lon in 0 until longitudeBands) {
                val first = (lat * (longitudeBands + 1) + lon).toShort()
                val second = (first.toInt() + longitudeBands + 1).toShort()
                idx.add(first); idx.add(second); idx.add((first + 1).toShort())
                idx.add(second); idx.add((second + 1).toShort()); idx.add((first + 1).toShort())
            }
        }
        val array = FloatArray(data.size) { data[it] }
        val vertexBytes = ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder())
        vertices = vertexBytes.asFloatBuffer().put(array).apply { position(0) }
        val normalBytes = ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder())
        normals = normalBytes.asFloatBuffer().put(array).apply { position(0) }
        val indexArray = ShortArray(idx.size) { idx[it] }
        val indexBytes = ByteBuffer.allocateDirect(indexArray.size * 2).order(ByteOrder.nativeOrder())
        indices = indexBytes.asShortBuffer().put(indexArray).apply { position(0) }
        indexCount = indexArray.size
    }

    fun draw(positionHandle: Int, normalHandle: Int) {
        vertices.position(0); normals.position(0); indices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertices)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 12, normals)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
}
