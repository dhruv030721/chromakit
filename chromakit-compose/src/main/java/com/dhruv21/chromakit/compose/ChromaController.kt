package com.dhruv21.chromakit.compose

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.dhruv21.chromakit.camera.CameraXFrameSource
import com.dhruv21.chromakit.core.engine.ChromaPipeline
import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.PipelineStats
import com.dhruv21.chromakit.core.model.QualityTier
import com.dhruv21.chromakit.core.model.SegmentationConfig
import com.dhruv21.chromakit.ml.MlKitSelfieSegmenter
import com.dhruv21.chromakit.render.ChromaRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller providing a fluent, reactive API for Jetpack Compose applications.
 */
class ChromaController(
    private val context: Context,
    initialConfig: SegmentationConfig = SegmentationConfig()
) {
    internal val renderer = ChromaRenderer()

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<SegmentationConfig> = _config.asStateFlow()

    private val _background = MutableStateFlow<ChromaBackground>(ChromaBackground.None)
    val background: StateFlow<ChromaBackground> = _background.asStateFlow()

    private var frameSource: CameraXFrameSource? = null
    private var pipeline: ChromaPipeline? = null

    val stats: StateFlow<PipelineStats>
        get() = pipeline?.stats ?: _fallbackStats

    private val _fallbackStats = MutableStateFlow(PipelineStats())

    val isRunning: StateFlow<Boolean>
        get() = pipeline?.isRunning ?: _fallbackIsRunning

    private val _fallbackIsRunning = MutableStateFlow(false)

    /**
     * Initializes the CameraX feed, ML inference engine, and connects the pipeline.
     */
    fun attach(lifecycleOwner: LifecycleOwner, scope: CoroutineScope) {
        if (pipeline != null) return

        val source = CameraXFrameSource(context, lifecycleOwner)
        val segmenter = MlKitSelfieSegmenter(_config.value)
        val pipe = ChromaPipeline(
            frameSource = source,
            segmenter = segmenter,
            renderer = renderer,
            config = _config.value
        )

        this.frameSource = source
        this.pipeline = pipe

        // Restore current background
        pipe.setBackground(_background.value)

        scope.launch(Dispatchers.Main) {
            pipe.start()
        }
    }

    /**
     * Updates the virtual background (Image, Color, Blur, None, Transparent).
     */
    fun setBackground(newBackground: ChromaBackground) {
        _background.value = newBackground
        pipeline?.setBackground(newBackground)
    }

    /**
     * Switches between front and back camera lenses.
     */
    fun switchCamera() {
        pipeline?.switchCamera()
    }

    /**
     * Adjusts the mask edge feathering softening factor (0.0f .. 0.1f).
     */
    fun setEdgeFeathering(feathering: Float) {
        val updated = _config.value.copy(edgeFeathering = feathering.coerceIn(0.0f, 0.1f))
        updateConfig(updated)
    }

    /**
     * Adjusts the confidence threshold for person segmentation (0.0f .. 1.0f).
     */
    fun setConfidenceThreshold(threshold: Float) {
        val updated = _config.value.copy(confidenceThreshold = threshold.coerceIn(0.0f, 1.0f))
        updateConfig(updated)
    }

    /**
     * Adjusts the temporal mask smoothing factor (0.0f .. 0.95f) to remove edge jitter.
     */
    fun setTemporalSmoothing(factor: Float) {
        val updated = _config.value.copy(temporalSmoothingFactor = factor.coerceIn(0.0f, 0.95f))
        updateConfig(updated)
    }

    /**
     * Updates the quality tier for device performance scaling.
     */
    fun setQualityTier(tier: QualityTier) {
        val updated = _config.value.copy(qualityTier = tier)
        updateConfig(updated)
    }

    private fun updateConfig(newConfig: SegmentationConfig) {
        _config.value = newConfig
        pipeline?.updateConfig(newConfig)
    }

    /**
     * Captures a real-time snapshot of the composited OpenGL viewport.
     */
    fun captureSnapshot(onCaptured: (android.graphics.Bitmap) -> Unit) {
        renderer.requestSnapshot(onCaptured)
    }

    /**
     * Processes a static image directly without camera feed.
     */
    suspend fun processStaticImage(
        inputBitmap: android.graphics.Bitmap,
        background: ChromaBackground = _background.value,
        config: SegmentationConfig = _config.value
    ): android.graphics.Bitmap {
        val processor = com.dhruv21.chromakit.ml.image.ChromaImageProcessor()
        return processor.processImage(inputBitmap, background, config)
    }

    /**
     * Releases pipeline and camera resources.
     */
    fun release() {
        pipeline?.stop()
        frameSource?.release()
        pipeline = null
        frameSource = null
    }
}
