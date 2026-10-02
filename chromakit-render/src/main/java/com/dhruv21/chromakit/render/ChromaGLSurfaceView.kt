package com.dhruv21.chromakit.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Custom [GLSurfaceView] hosting the [ChromaRenderer].
 */
class ChromaGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = ChromaRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
