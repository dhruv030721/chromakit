package com.dhruv21.chromakit.core.engine

import com.dhruv21.chromakit.core.model.ChromaFrame
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a source of image frames (e.g. CameraX, Video playback, Image file).
 */
interface ChromaFrameSource {
    /**
     * Whether the frame source is currently streaming.
     */
    val isStreaming: StateFlow<Boolean>

    /**
     * Starts delivering frames to the provided listener.
     */
    fun start(onFrame: (ChromaFrame) -> Unit)

    /**
     * Pauses or stops frame generation.
     */
    fun stop()

    /**
     * Toggles between front and rear cameras (if applicable to this source).
     */
    fun switchCamera()

    /**
     * Returns true if the active lens is front-facing.
     */
    fun isFrontFacing(): Boolean

    /**
     * Releases camera sessions and frame analyzers.
     */
    fun release()
}
