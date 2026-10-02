package com.dhruv21.chromakit.core.engine

import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.model.ChromaMask
import com.dhruv21.chromakit.core.model.SegmentationConfig

/**
 * Interface contract implemented by OpenGL render engines to composite camera frames,
 * alpha masks, and virtual backgrounds.
 */
interface ChromaRendererContract {
    /**
     * Submits a new camera frame texture for display.
     */
    fun onNewFrame(frame: ChromaFrame)

    /**
     * Submits an updated alpha segmentation mask for blending.
     */
    fun onNewMask(mask: ChromaMask)

    /**
     * Updates the current virtual background strategy.
     */
    fun setBackground(background: ChromaBackground)

    /**
     * Updates shader parameters (feathering, threshold, etc.).
     */
    fun updateConfig(config: SegmentationConfig)

    /**
     * Releases OpenGL ES programs, framebuffers, and textures.
     */
    fun release()
}
