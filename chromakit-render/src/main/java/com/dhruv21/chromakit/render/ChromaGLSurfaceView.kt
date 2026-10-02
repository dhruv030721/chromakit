package com.dhruv21.chromakit.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Custom [GLSurfaceView] hosting the [ChromaRenderer].
 */
class ChromaGLSurfaceView(
    context: Context,
    attrs: AttributeSet? = null,
    val renderer: ChromaRenderer = ChromaRenderer()
) : GLSurfaceView(context, attrs) {

    constructor(context: Context, renderer: ChromaRenderer) : this(context, null, renderer)
    constructor(context: Context) : this(context, null, ChromaRenderer())

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
