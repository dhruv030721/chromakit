package com.dhruv21.chromakit.core.model

/**
 * Diagnostics and real-time performance telemetry.
 *
 * @param fps Live rendered frames per second.
 * @param inferenceLatencyMs Time taken by the ML segmenter to process a frame.
 * @param renderLatencyMs Time taken by the OpenGL compositor to blend and display.
 * @param droppedFrames Number of dropped or skipped inference frames.
 */
data class PipelineStats(
    val fps: Double = 0.0,
    val inferenceLatencyMs: Long = 0L,
    val renderLatencyMs: Long = 0L,
    val droppedFrames: Long = 0L
)
