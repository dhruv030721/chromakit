package com.dhruv21.chromakit.ml.adaptive

import com.dhruv21.chromakit.core.model.QualityTier

/**
 * Adaptive inference governor designed to maintain high frame-rates across device tiers.
 *
 * For low-end or thermally throttled devices, inference cadence is dynamically adjusted
 * while the OpenGL rendering pipeline continues at 60 FPS.
 */
class AdaptiveGovernor(
    private var qualityTier: QualityTier = QualityTier.AUTO
) {
    private var movingAverageLatencyMs = 25.0
    private var frameCounter = 0L

    fun setQualityTier(tier: QualityTier) {
        this.qualityTier = tier
    }

    /**
     * Determines whether the current frame should run segmentation inference.
     */
    fun shouldInferNextFrame(): Boolean {
        frameCounter++
        return when (qualityTier) {
            QualityTier.HIGH_PERFORMANCE -> true
            QualityTier.BATTERY_SAVER -> frameCounter % 3L == 0L
            QualityTier.BALANCED -> frameCounter % 2L == 0L
            QualityTier.AUTO -> {
                if (movingAverageLatencyMs > 55.0) {
                    // Severely throttled / low-end device: run inference every 3rd frame
                    frameCounter % 3L == 0L
                } else if (movingAverageLatencyMs > 35.0) {
                    // Mid-range device: run inference every 2nd frame
                    frameCounter % 2L == 0L
                } else {
                    // High-performance: run inference every frame
                    true
                }
            }
        }
    }

    /**
     * Records elapsed latency to adjust future throttling decisions.
     */
    fun recordLatency(latencyMs: Long) {
        // Exponential moving average with alpha = 0.15
        movingAverageLatencyMs = (0.15 * latencyMs) + (0.85 * movingAverageLatencyMs)
    }
}
