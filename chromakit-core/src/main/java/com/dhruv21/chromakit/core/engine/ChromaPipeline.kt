package com.dhruv21.chromakit.core.engine

import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.ChromaFrame
import com.dhruv21.chromakit.core.model.PipelineStats
import com.dhruv21.chromakit.core.model.SegmentationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Clean Architecture Pipeline orchestrator coordinating:
 * Frame Source (CameraX) -> ML Segmenter (ML Kit / Custom) -> Compositor (OpenGL ES).
 *
 * Implements decoupled inference rendering: high FPS preview is maintained even on
 * older devices when ML inference runs at a lower cadence.
 */
class ChromaPipeline(
    private val frameSource: ChromaFrameSource,
    private val segmenter: ChromaSegmenter,
    private val renderer: ChromaRendererContract,
    private var config: SegmentationConfig = SegmentationConfig()
) {
    private val pipelineScope = CoroutineScope(Dispatchers.Default + Job())

    private val isInferring = AtomicBoolean(false)
    private val droppedFramesCounter = AtomicLong(0L)

    private val _stats = MutableStateFlow(PipelineStats())
    val stats: StateFlow<PipelineStats> = _stats.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var frameCount = 0
    private var lastFpsCalculationTimeNs = System.nanoTime()

    /**
     * Initializes ML segmenter and starts frame delivery.
     */
    suspend fun start() {
        if (_isRunning.value) return

        segmenter.initialize(config)
        renderer.updateConfig(config)

        frameSource.start { frame ->
            processFrame(frame)
        }

        _isRunning.value = true
    }

    /**
     * Process each incoming camera frame.
     */
    private fun processFrame(frame: ChromaFrame) {
        val renderStartNs = System.nanoTime()

        // 1. Send the raw frame immediately to the OpenGL renderer for zero-lag rendering
        renderer.onNewFrame(frame)
        val renderLatencyMs = (System.nanoTime() - renderStartNs) / 1_000_000

        // 2. ML Segmentation step: decouple inference from frame delivery
        if (isInferring.compareAndSet(false, true)) {
            pipelineScope.launch {
                val inferenceStartNs = System.nanoTime()
                try {
                    val mask = segmenter.segment(frame)
                    val inferenceLatencyMs = (System.nanoTime() - inferenceStartNs) / 1_000_000

                    if (mask != null) {
                        renderer.onNewMask(mask)
                    }

                    updateTelemetry(inferenceLatencyMs, renderLatencyMs)
                } finally {
                    isInferring.set(false)
                }
            }
        } else {
            // Frame skipped for inference to maintain smooth camera display (ideal for low-end devices)
            droppedFramesCounter.incrementAndGet()
        }
    }

    private fun updateTelemetry(inferenceLatencyMs: Long, renderLatencyMs: Long) {
        frameCount++
        val now = System.nanoTime()
        val elapsedSec = (now - lastFpsCalculationTimeNs) / 1_000_000_000.0

        if (elapsedSec >= 1.0) {
            val currentFps = frameCount / elapsedSec
            frameCount = 0
            lastFpsCalculationTimeNs = now

            _stats.value = PipelineStats(
                fps = (currentFps * 10.0).toLong() / 10.0,
                inferenceLatencyMs = inferenceLatencyMs,
                renderLatencyMs = renderLatencyMs,
                droppedFrames = droppedFramesCounter.get()
            )
        }
    }

    /**
     * Changes virtual background dynamically.
     */
    fun setBackground(background: ChromaBackground) {
        renderer.setBackground(background)
    }

    /**
     * Updates pipeline configuration.
     */
    fun updateConfig(newConfig: SegmentationConfig) {
        config = newConfig
        segmenter.updateConfig(newConfig)
        renderer.updateConfig(newConfig)
    }

    /**
     * Switches between front and back camera lenses.
     */
    fun switchCamera() {
        frameSource.switchCamera()
    }

    /**
     * Stops the pipeline and releases resources.
     */
    fun stop() {
        if (!_isRunning.value) return
        frameSource.stop()
        segmenter.release()
        renderer.release()
        _isRunning.value = false
    }
}
