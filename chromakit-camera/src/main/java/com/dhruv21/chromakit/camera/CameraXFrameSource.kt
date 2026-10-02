package com.dhruv21.chromakit.camera

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dhruv21.chromakit.core.engine.ChromaFrameSource
import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.pool.BufferPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX implementation of [ChromaFrameSource].
 *
 * Designed with:
 * 1. Zero frame-time GC overhead through reusable buffer pools.
 * 2. Automatic sensor rotation & front camera orientation handling.
 * 3. Lifecycle-aware binding.
 */
class CameraXFrameSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private var targetResolution: Size = Size(720, 1280)
) : ChromaFrameSource {

    private val tag = "CameraXFrameSource"

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var frameListener: ((ChromaFrame) -> Unit)? = null
    private var bufferPool: BufferPool? = null

    override fun start(onFrame: (ChromaFrame) -> Unit) {
        this.frameListener = onFrame
        bindCamera()
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(tag, "Failed to get ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        this.imageAnalysis = analysis

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                analysis
            )
            _isStreaming.value = true
        } catch (e: Exception) {
            Log.e(tag, "Use case binding failed", e)
            _isStreaming.value = false
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val pixelBuffer = plane.buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val expectedRowBytes = width * 4
            val totalBytes = width * height * 4

            if (bufferPool == null) {
                bufferPool = BufferPool(totalBytes, maxPoolSize = 3)
            }

            val pool = bufferPool ?: return
            val frameBuffer = pool.acquire()

            // Handle row stride padding on OEM camera hardware
            pixelBuffer.rewind()
            if (rowStride == expectedRowBytes) {
                frameBuffer.put(pixelBuffer)
            } else {
                val rowByteArray = ByteArray(expectedRowBytes)
                for (row in 0 until height) {
                    pixelBuffer.position(row * rowStride)
                    pixelBuffer.get(rowByteArray, 0, expectedRowBytes)
                    frameBuffer.put(rowByteArray)
                }
            }
            frameBuffer.flip()

            val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
            val frame = ChromaFrame(
                byteBuffer = frameBuffer,
                width = width,
                height = height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                isFrontCamera = isFront
            )

            frameListener?.invoke(frame)

            // Return buffer back to pool
            pool.release(frameBuffer)
        } catch (e: Exception) {
            Log.e(tag, "Error processing image proxy", e)
        } finally {
            imageProxy.close()
        }
    }

    override fun stop() {
        cameraProvider?.unbindAll()
        _isStreaming.value = false
    }

    override fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindUseCases()
    }

    override fun isFrontFacing(): Boolean {
        return lensFacing == CameraSelector.LENS_FACING_FRONT
    }

    override fun release() {
        stop()
        cameraExecutor.shutdown()
        bufferPool?.clear()
    }
}
