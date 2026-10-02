package com.dhruv21.chromakit.core.model

/**
 * Quality tiers for adaptive performance scaling across device generations.
 */
enum class QualityTier {
    /**
     * Dynamically adjust frame resolution and inference frequency based on live device FPS/thermal metrics.
     */
    AUTO,

    /**
     * Full resolution mask inference (targeted for mid-to-high end devices).
     */
    HIGH_PERFORMANCE,

    /**
     * Balanced mode with 256x256 or downscaled mask inference and temporal smoothing.
     */
    BALANCED,

    /**
     * Power saving and legacy low-end device optimization (throttled inference frequency, low mask res).
     */
    BATTERY_SAVER
}

/**
 * Configuration options for segmentation and compositing.
 *
 * @param qualityTier Target quality strategy.
 * @param temporalSmoothingFactor Value between 0.0f (no smoothing) and 0.95f (heavy smoothing)
 * to stabilize jittering mask boundaries between successive frames.
 * @param edgeFeathering Softens mask contours for natural blending with custom backgrounds (0.0f to 0.1f).
 * @param confidenceThreshold Confidence cutoff for person detection (default 0.5f).
 * @param enableRawSizeMask If true, requests full-size mask from ML model when available.
 */
data class SegmentationConfig(
    val qualityTier: QualityTier = QualityTier.AUTO,
    val temporalSmoothingFactor: Float = 0.65f,
    val edgeFeathering: Float = 0.035f,
    val confidenceThreshold: Float = 0.5f,
    val enableRawSizeMask: Boolean = false
)
