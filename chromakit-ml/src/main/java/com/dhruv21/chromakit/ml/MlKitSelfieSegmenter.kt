package com.dhruv21.chromakit.ml

import com.dhruv21.chromakit.core.engine.ChromaSegmenter
import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.model.ChromaMask
import com.dhruv21.chromakit.core.model.SegmentationConfig
import com.dhruv21.chromakit.ml.adaptive.AdaptiveGovernor
import com.dhruv21.chromakit.ml.smoothing.TemporalMaskSmoother
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-ready [ChromaSegmenter] implementation powered by Google ML Kit Selfie Segmentation.
 *
 * Incorporates:
 * - Stream mode optimization
 * - Temporal mask smoothing for flicker-free edges
 * - Adaptive device throttling via [AdaptiveGovernor]
 */
class MlKitSelfieSegmenter(
    private var config: SegmentationConfig = SegmentationConfig()
) : ChromaSegmenter {

    private var segmenter: Segmenter? = null
    private val smoother = TemporalMaskSmoother(config.temporalSmoothingFactor)
    private val governor = AdaptiveGovernor(config.qualityTier)
    private var reusableBitmap: android.graphics.Bitmap? = null

    override suspend fun initialize(config: SegmentationConfig) {
        this.config = config
        smoother.updateSmoothingFactor(config.temporalSmoothingFactor)
        governor.setQualityTier(config.qualityTier)

        val optionsBuilder = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)

        if (config.enableRawSizeMask) {
            optionsBuilder.enableRawSizeMask()
        }

        segmenter = Segmentation.getClient(optionsBuilder.build())
    }

    override suspend fun segment(frame: ChromaFrame): ChromaMask? = withContext(Dispatchers.Default) {
        val client = segmenter ?: return@withContext null

        if (!governor.shouldInferNextFrame()) {
            return@withContext null
        }

        val startTime = System.currentTimeMillis()

        try {
            frame.byteBuffer.rewind()
            var bmp = reusableBitmap
            if (bmp == null || bmp.width != frame.width || bmp.height != frame.height) {
                bmp = android.graphics.Bitmap.createBitmap(frame.width, frame.height, android.graphics.Bitmap.Config.ARGB_8888)
                reusableBitmap = bmp
            }
            bmp.copyPixelsFromBuffer(frame.byteBuffer)
            frame.byteBuffer.rewind()

            val inputImage = InputImage.fromBitmap(bmp, frame.rotationDegrees)

            val mlMask = Tasks.await(client.process(inputImage))
            val maskBuffer = mlMask.buffer
            val maskWidth = mlMask.width
            val maskHeight = mlMask.height

            val smoothed = smoother.smooth(maskBuffer, maskWidth, maskHeight)

            val latency = System.currentTimeMillis() - startTime
            governor.recordLatency(latency)

            ChromaMask(
                buffer = smoothed,
                width = maskWidth,
                height = maskHeight,
                timestampNs = frame.timestampNs
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun updateConfig(config: SegmentationConfig) {
        this.config = config
        smoother.updateSmoothingFactor(config.temporalSmoothingFactor)
        governor.setQualityTier(config.qualityTier)
    }

    override fun release() {
        segmenter?.close()
        segmenter = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        smoother.reset()
    }
}
