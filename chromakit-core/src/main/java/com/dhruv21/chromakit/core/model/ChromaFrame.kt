package com.dhruv21.chromakit.core.model

import java.nio.ByteBuffer

/**
 * Represents an input camera frame passing through the ChromaKit pipeline.
 *
 * @param byteBuffer Direct ByteBuffer holding frame pixel data.
 * @param width Width of the frame in pixels.
 * @param height Height of the frame in pixels.
 * @param rotationDegrees Sensor rotation degrees (0, 90, 180, 270).
 * @param isFrontCamera Whether this frame originates from a front-facing camera.
 * @param timestampNs Timestamp in nanoseconds.
 */
data class ChromaFrame(
    val byteBuffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean,
    val timestampNs: Long = System.nanoTime()
)
