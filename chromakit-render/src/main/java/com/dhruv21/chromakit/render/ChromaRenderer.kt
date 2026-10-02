package com.dhruv21.chromakit.render

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.dhruv21.chromakit.core.engine.ChromaRendererContract
import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.model.ChromaMask
import com.dhruv21.chromakit.core.model.SegmentationConfig
import com.dhruv21.chromakit.render.gl.ChromaShaders
import com.dhruv21.chromakit.render.gl.GLShaderUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL ES 2.0/3.0 renderer compositing camera frames, ML alpha masks,
 * and virtual backgrounds at 60 FPS directly on the GPU.
 */
class ChromaRenderer : GLSurfaceView.Renderer, ChromaRendererContract {

    private val vertexCoordinates = floatArrayOf(
        -1.0f, -1.0f, // 0 bottom left
         1.0f, -1.0f, // 1 bottom right
        -1.0f,  1.0f, // 2 top left
         1.0f,  1.0f  // 3 top right
    )

    private val textureCoordinates = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexCoordinates.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertexCoordinates)
            position(0)
        }

    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(textureCoordinates)
            position(0)
        }

    private val transformMatrix = FloatArray(16)

    // GL Handles
    private var programHandle = 0
    private var aPositionHandle = 0
    private var aTexCoordHandle = 0
    private var uMatrixHandle = 0
    private var uCameraTextureHandle = 0
    private var uMaskTextureHandle = 0
    private var uBgTextureHandle = 0
    private var uBgTypeHandle = 0
    private var uBgColorHandle = 0
    private var uThresholdHandle = 0
    private var uFeatherHandle = 0
    private var uBlurRadiusHandle = 0
    private var uTexelSizeHandle = 0
    private var uHasMaskHandle = 0

    // Textures
    private var cameraTextureId = 0
    private var maskTextureId = 0
    private var bgTextureId = 0

    // State
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var cameraFrameWidth = 0
    private var cameraFrameHeight = 0
    private var cameraRotation = 0
    private var isFrontFacing = true

    // Pending Frame/Mask/Background updates
    private val framePending = AtomicBoolean(false)
    private var pendingFrameBuffer: ByteBuffer? = null

    private val maskPending = AtomicBoolean(false)
    private var pendingMaskBuffer: ByteBuffer? = null
    private var maskWidth = 0
    private var maskHeight = 0
    private var hasValidMask = false

    private val bgPending = AtomicBoolean(false)
    private var pendingBgBitmap: Bitmap? = null

    private var currentBackground: ChromaBackground = ChromaBackground.None
    private var config = SegmentationConfig()
    private var snapshotCallback: ((Bitmap) -> Unit)? = null

    fun requestSnapshot(callback: (Bitmap) -> Unit) {
        this.snapshotCallback = callback
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        programHandle = GLShaderUtil.createProgram(ChromaShaders.VERTEX_SHADER, ChromaShaders.FRAGMENT_SHADER)
        if (programHandle == 0) return

        aPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        aTexCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
        uMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMatrix")

        uCameraTextureHandle = GLES20.glGetUniformLocation(programHandle, "uCameraTexture")
        uMaskTextureHandle = GLES20.glGetUniformLocation(programHandle, "uMaskTexture")
        uBgTextureHandle = GLES20.glGetUniformLocation(programHandle, "uBgTexture")

        uBgTypeHandle = GLES20.glGetUniformLocation(programHandle, "uBgType")
        uBgColorHandle = GLES20.glGetUniformLocation(programHandle, "uBgColor")
        uThresholdHandle = GLES20.glGetUniformLocation(programHandle, "uThreshold")
        uFeatherHandle = GLES20.glGetUniformLocation(programHandle, "uFeather")
        uBlurRadiusHandle = GLES20.glGetUniformLocation(programHandle, "uBlurRadius")
        uTexelSizeHandle = GLES20.glGetUniformLocation(programHandle, "uTexelSize")
        uHasMaskHandle = GLES20.glGetUniformLocation(programHandle, "uHasMask")

        cameraTextureId = GLShaderUtil.generateTexture()
        maskTextureId = GLShaderUtil.generateTexture()
        bgTextureId = GLShaderUtil.generateTexture()

        Matrix.setIdentityM(transformMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        updateTransformMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (programHandle == 0) return

        // 1. Upload new camera frame if available
        if (framePending.compareAndSet(true, false)) {
            val buffer = pendingFrameBuffer
            if (buffer != null && cameraFrameWidth > 0 && cameraFrameHeight > 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTextureId)
                buffer.position(0)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    cameraFrameWidth, cameraFrameHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                )
            }
        }

        // 2. Upload new mask if available
        if (maskPending.compareAndSet(true, false)) {
            val buffer = pendingMaskBuffer
            if (buffer != null && maskWidth > 0 && maskHeight > 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
                buffer.position(0)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                    maskWidth, maskHeight, 0,
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer
                )
                hasValidMask = true
            }
        }

        // 3. Upload background image bitmap if pending
        if (bgPending.compareAndSet(true, false)) {
            val bitmap = pendingBgBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
        }

        GLES20.glUseProgram(programHandle)

        // Bind attributes
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordHandle)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        // Bind uniforms
        GLES20.glUniformMatrix4fv(uMatrixHandle, 1, false, transformMatrix, 0)

        // Texture Unit 0: Camera
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTextureId)
        GLES20.glUniform1i(uCameraTextureHandle, 0)

        // Texture Unit 1: Mask
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glUniform1i(uMaskTextureHandle, 1)

        // Texture Unit 2: Background Image
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
        GLES20.glUniform1i(uBgTextureHandle, 2)

        // Shaders & Background parameters
        GLES20.glUniform1i(uHasMaskHandle, if (hasValidMask) 1 else 0)
        GLES20.glUniform1f(uThresholdHandle, config.confidenceThreshold)
        GLES20.glUniform1f(uFeatherHandle, config.edgeFeathering)
        GLES20.glUniform2f(uTexelSizeHandle, 1.0f / surfaceWidth.coerceAtLeast(1), 1.0f / surfaceHeight.coerceAtLeast(1))

        applyBackgroundUniforms()

        // Draw Quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Readback snapshot if requested
        val cb = snapshotCallback
        if (cb != null && surfaceWidth > 0 && surfaceHeight > 0) {
            val totalBytes = surfaceWidth * surfaceHeight * 4
            val snapshotBuffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, surfaceWidth, surfaceHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, snapshotBuffer)
            val bmp = Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(snapshotBuffer)
            // OpenGL origin is bottom-left, flip vertically for Android Bitmap standard
            val flipMatrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
            val corrected = Bitmap.createBitmap(bmp, 0, 0, surfaceWidth, surfaceHeight, flipMatrix, true)
            cb.invoke(corrected)
            snapshotCallback = null
        }

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    }

    private fun applyBackgroundUniforms() {
        when (val bg = currentBackground) {
            is ChromaBackground.None -> {
                GLES20.glUniform1i(uBgTypeHandle, 0)
            }
            is ChromaBackground.SolidColor -> {
                GLES20.glUniform1i(uBgTypeHandle, 1)
                val c = bg.color
                GLES20.glUniform4f(
                    uBgColorHandle,
                    Color.red(c) / 255f,
                    Color.green(c) / 255f,
                    Color.blue(c) / 255f,
                    Color.alpha(c) / 255f
                )
            }
            is ChromaBackground.Image -> {
                GLES20.glUniform1i(uBgTypeHandle, 2)
            }
            is ChromaBackground.Blur -> {
                GLES20.glUniform1i(uBgTypeHandle, 3)
                GLES20.glUniform1f(uBlurRadiusHandle, bg.radius)
            }
            is ChromaBackground.Transparent -> {
                GLES20.glUniform1i(uBgTypeHandle, 4)
            }
        }
    }

    private fun updateTransformMatrix() {
        Matrix.setIdentityM(transformMatrix, 0)

        // Aspect fill scaling: ensures full edge-to-edge coverage without aspect distortion
        if (surfaceWidth > 0 && surfaceHeight > 0 && cameraFrameWidth > 0 && cameraFrameHeight > 0) {
            val isRotated = cameraRotation == 90 || cameraRotation == 270
            val effectiveWidth = if (isRotated) cameraFrameHeight else cameraFrameWidth
            val effectiveHeight = if (isRotated) cameraFrameWidth else cameraFrameHeight

            val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
            val frameAspect = effectiveWidth.toFloat() / effectiveHeight.toFloat()

            var scaleX = 1.0f
            var scaleY = 1.0f

            if (frameAspect > surfaceAspect) {
                scaleX = frameAspect / surfaceAspect
            } else {
                scaleY = surfaceAspect / frameAspect
            }

            Matrix.scaleM(transformMatrix, 0, scaleX, scaleY, 1f)
        }

        // Camera rotation
        Matrix.rotateM(transformMatrix, 0, -cameraRotation.toFloat(), 0f, 0f, 1f)

        // Front camera horizontal mirror
        if (isFrontFacing) {
            Matrix.scaleM(transformMatrix, 0, -1f, 1f, 1f)
        }
    }

    override fun onNewFrame(frame: ChromaFrame) {
        val sizeChanged = cameraFrameWidth != frame.width || cameraFrameHeight != frame.height ||
                cameraRotation != frame.rotationDegrees || isFrontFacing != frame.isFrontCamera

        cameraFrameWidth = frame.width
        cameraFrameHeight = frame.height
        cameraRotation = frame.rotationDegrees
        isFrontFacing = frame.isFrontCamera

        if (sizeChanged) {
            updateTransformMatrix()
        }

        // Cache byte buffer for GL texture upload
        val capacity = frame.byteBuffer.remaining()
        var targetBuf = pendingFrameBuffer
        if (targetBuf == null || targetBuf.capacity() != capacity) {
            targetBuf = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
            pendingFrameBuffer = targetBuf
        }
        targetBuf.clear()
        frame.byteBuffer.rewind()
        targetBuf.put(frame.byteBuffer)
        targetBuf.flip()

        framePending.set(true)
    }

    override fun onNewMask(mask: ChromaMask) {
        maskWidth = mask.width
        maskHeight = mask.height

        val totalPixels = maskWidth * maskHeight
        var targetBuf = pendingMaskBuffer
        if (targetBuf == null || targetBuf.capacity() != totalPixels) {
            targetBuf = ByteBuffer.allocateDirect(totalPixels).order(ByteOrder.nativeOrder())
            pendingMaskBuffer = targetBuf
        }
        targetBuf.clear()

        // Convert floats (0.0f..1.0f) to byte luminance (0..255) for ultra-wide OpenGL compatibility
        mask.buffer.rewind()
        val floatBuf = mask.buffer.asFloatBuffer()
        for (i in 0 until totalPixels) {
            val confidence = floatBuf.get(i).coerceIn(0.0f, 1.0f)
            targetBuf.put((confidence * 255.0f).toInt().toByte())
        }
        targetBuf.flip()

        maskPending.set(true)
    }

    override fun setBackground(background: ChromaBackground) {
        this.currentBackground = background
        if (background is ChromaBackground.Image) {
            pendingBgBitmap = background.bitmap
            bgPending.set(true)
        }
    }

    override fun updateConfig(config: SegmentationConfig) {
        this.config = config
    }

    override fun release() {
        if (programHandle != 0) {
            GLES20.glDeleteProgram(programHandle)
            programHandle = 0
        }
        val textures = intArrayOf(cameraTextureId, maskTextureId, bgTextureId)
        GLES20.glDeleteTextures(3, textures, 0)
        hasValidMask = false
    }
}
