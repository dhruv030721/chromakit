package com.dhruv21.chromakit.core.engine

import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.model.ChromaMask
import com.dhruv21.chromakit.core.model.SegmentationConfig

/**
 * Strategy interface for segmentation ML inference engines.
 * Pluggable architecture allows swapping ML Kit, MediaPipe, or custom TFLite models.
 */
interface ChromaSegmenter {
    /**
     * Initializes any ML engine resources, delegates, or model weights.
     */
    suspend fun initialize(config: SegmentationConfig)

    /**
     * Executes inference on the input frame and returns an alpha mask.
     *
     * @param frame Raw camera frame with orientation and dimensions.
     * @return Segmentation mask or null if inference failed or was skipped.
     */
    suspend fun segment(frame: ChromaFrame): ChromaMask?

    /**
     * Dynamically updates runtime configuration (e.g. quality tier, smoothing).
     */
    fun updateConfig(config: SegmentationConfig)

    /**
     * Releases model delegates and memory.
     */
    fun release()
}
