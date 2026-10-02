package com.dhruv21.chromakit.compose

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhruv21.chromakit.core.model.SegmentationConfig
import com.dhruv21.chromakit.render.ChromaGLSurfaceView

/**
 * Creates and remembers a [ChromaController] scoped to the current composable lifecycle.
 */
@Composable
fun rememberChromaController(
    config: SegmentationConfig = SegmentationConfig()
): ChromaController {
    val context = LocalContext.current.applicationContext
    return remember {
        ChromaController(context, config)
    }
}

/**
 * Production-ready, plug-and-play Jetpack Compose composable for real-time video segmentation,
 * green-screening, and background compositing.
 *
 * @param controller The [ChromaController] instance managing the pipeline.
 * @param modifier Compose layout modifier.
 */
@Composable
fun ChromaKitPreview(
    controller: ChromaController,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(controller, lifecycleOwner) {
        controller.attach(lifecycleOwner, coroutineScope)
        onDispose {
            controller.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            ChromaGLSurfaceView(ctx).apply {
                // Wire the controller's shared renderer
                setEGLContextClientVersion(2)
                setRenderer(controller.renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier
    )
}
