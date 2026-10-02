package com.dhruv21.chromakit.ml.smoothing

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stabilizes segmentation mask boundaries across consecutive frames.
 *
 * Employs Exponential Moving Average (EMA) to eliminate edge flickering and jitter,
 * delivering production-grade stability on both low-end and high-end hardware.
 */
class TemporalMaskSmoother(
    private var smoothingFactor: Float = 0.65f
) {
    private var previousMaskBuffer: ByteBuffer? = null
    private var bufferCapacity = 0

    fun updateSmoothingFactor(factor: Float) {
        this.smoothingFactor = factor.coerceIn(0.0f, 0.95f)
    }

    /**
     * Smooths [currentBuffer] against historical mask data.
     * Both input and output buffers are direct float ByteBuffers (values 0.0f .. 1.0f).
     */
    fun smooth(currentBuffer: ByteBuffer, width: Int, height: Int): ByteBuffer {
        val totalFloats = width * height
        val requiredBytes = totalFloats * 4

        var prev = previousMaskBuffer
        if (prev == null || bufferCapacity != requiredBytes) {
            prev = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder())
            previousMaskBuffer = prev
            bufferCapacity = requiredBytes

            // First frame: copy current directly to previous and return
            currentBuffer.rewind()
            prev.rewind()
            prev.put(currentBuffer)
            currentBuffer.rewind()
            prev.rewind()
            return prev
        }

        currentBuffer.rewind()
        prev.rewind()

        val currentFloatBuffer = currentBuffer.asFloatBuffer()
        val prevFloatBuffer = prev.asFloatBuffer()

        val factor = smoothingFactor
        val invFactor = 1.0f - factor

        for (i in 0 until totalFloats) {
            val currVal = currentFloatBuffer.get(i)
            val prevVal = prevFloatBuffer.get(i)
            val blendedVal = (currVal * (1f - factor)) + (prevVal * factor)
            prevFloatBuffer.put(i, blendedVal)
        }

        prev.rewind()
        return prev
    }

    fun reset() {
        previousMaskBuffer = null
        bufferCapacity = 0
    }
}
