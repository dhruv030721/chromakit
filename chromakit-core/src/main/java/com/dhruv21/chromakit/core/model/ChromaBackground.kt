package com.dhruv21.chromakit.core.model

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Strategy pattern representing the background composited behind segmented foreground subjects.
 */
sealed interface ChromaBackground {
    /**
     * Pass-through mode: no background replacement, plain camera preview.
     */
    data object None : ChromaBackground

    /**
     * Solid color background, such as virtual green-screen (#00FF00) or studio backdrop.
     *
     * @param color Int color representation (ARGB).
     */
    data class SolidColor(val color: Int = Color.GREEN) : ChromaBackground

    /**
     * Static custom image background (e.g. user-picked photo, studio banner).
     *
     * @param bitmap The bitmap image to render as the virtual background.
     */
    data class Image(val bitmap: Bitmap) : ChromaBackground

    /**
     * Blur effect applied to original camera background (portrait / bokeh effect).
     *
     * @param radius Blur intensity radius between 1.0f and 25.0f.
     */
    data class Blur(val radius: Float = 12f) : ChromaBackground

    /**
     * Transparent background, leaving background alpha as 0.0 for external blending/recording.
     */
    data object Transparent : ChromaBackground
}
