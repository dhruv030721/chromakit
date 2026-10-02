package com.dhruv21.chromakit.ml.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.SegmentationConfig
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance processor for static image segmentation, green screen,
 * background replacement, and bokeh blur.
 */
class ChromaImageProcessor {

    /**
     * Segments the foreground subject in [inputBitmap] and merges them with [background].
     *
     * @param inputBitmap Source image containing people.
     * @param background Target virtual background strategy.
     * @param config Segmentation tuning parameters.
     * @return New [Bitmap] with the composited result.
     */
    suspend fun processImage(
        inputBitmap: Bitmap,
        background: ChromaBackground,
        config: SegmentationConfig = SegmentationConfig()
    ): Bitmap = withContext(Dispatchers.Default) {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()

        val segmenter = Segmentation.getClient(options)

        try {
            val inputImage = InputImage.fromBitmap(inputBitmap, 0)
            val mask = Tasks.await(segmenter.process(inputImage))

            val width = inputBitmap.width
            val height = inputBitmap.height
            val maskWidth = mask.width
            val maskHeight = mask.height
            val maskBuffer = mask.buffer.asFloatBuffer()

            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val inputPixels = IntArray(width * height)
            inputBitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

            val outputPixels = IntArray(width * height)

            // Prepare background pixels
            val bgPixels = getBackgroundPixels(background, inputBitmap, width, height)

            val threshold = config.confidenceThreshold
            val feather = config.edgeFeathering

            for (y in 0 until height) {
                val maskY = (y * maskHeight) / height
                for (x in 0 until width) {
                    val maskX = (x * maskWidth) / width
                    val maskIndex = maskY * maskWidth + maskX
                    val confidence = maskBuffer.get(maskIndex).coerceIn(0.0f, 1.0f)

                    // Smoothstep feathering
                    val edgeLow = (threshold - feather).coerceIn(0f, 1f)
                    val edgeHigh = (threshold + feather).coerceIn(0f, 1f)
                    val alpha = if (edgeHigh > edgeLow) {
                        val t = ((confidence - edgeLow) / (edgeHigh - edgeLow)).coerceIn(0f, 1f)
                        t * t * (3f - 2f * t)
                    } else {
                        if (confidence >= threshold) 1.0f else 0.0f
                    }

                    val pixelIndex = y * width + x
                    val fgColor = inputPixels[pixelIndex]
                    val bgColor = bgPixels[pixelIndex]

                    outputPixels[pixelIndex] = blendColors(bgColor, fgColor, alpha)
                }
            }

            resultBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
            resultBitmap
        } finally {
            segmenter.close()
        }
    }

    private fun getBackgroundPixels(
        background: ChromaBackground,
        source: Bitmap,
        width: Int,
        height: Int
    ): IntArray {
        val pixels = IntArray(width * height)
        when (background) {
            is ChromaBackground.None -> {
                source.getPixels(pixels, 0, width, 0, 0, width, height)
            }
            is ChromaBackground.SolidColor -> {
                pixels.fill(background.color)
            }
            is ChromaBackground.Image -> {
                val scaledBg = Bitmap.createScaledBitmap(background.bitmap, width, height, true)
                scaledBg.getPixels(pixels, 0, width, 0, 0, width, height)
            }
            is ChromaBackground.Blur -> {
                val blurred = fastBoxBlur(source, background.radius.toInt().coerceIn(1, 25))
                blurred.getPixels(pixels, 0, width, 0, 0, width, height)
            }
            is ChromaBackground.Transparent -> {
                pixels.fill(Color.TRANSPARENT)
            }
        }
        return pixels
    }

    private fun blendColors(bgColor: Int, fgColor: Int, fgAlpha: Float): Int {
        if (fgAlpha >= 0.999f) return fgColor
        if (fgAlpha <= 0.001f) return bgColor

        val bgA = Color.alpha(bgColor)
        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        val fgA = Color.alpha(fgColor)
        val fgR = Color.red(fgColor)
        val fgG = Color.green(fgColor)
        val fgB = Color.blue(fgColor)

        val outA = (bgA * (1f - fgAlpha) + fgA * fgAlpha).toInt().coerceIn(0, 255)
        val outR = (bgR * (1f - fgAlpha) + fgR * fgAlpha).toInt().coerceIn(0, 255)
        val outG = (bgG * (1f - fgAlpha) + fgG * fgAlpha).toInt().coerceIn(0, 255)
        val outB = (bgB * (1f - fgAlpha) + fgB * fgAlpha).toInt().coerceIn(0, 255)

        return Color.argb(outA, outR, outG, outB)
    }

    /**
     * Fast CPU box blur fallback for static image processing.
     */
    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scaled = Bitmap.createScaledBitmap(src, (w / 4).coerceAtLeast(1), (h / 4).coerceAtLeast(1), true)
        val result = Bitmap.createScaledBitmap(scaled, w, h, true)
        return result
    }
}
