# ChromaKit 🎬

> A high-performance, lightweight, modular Android library for real-time video/selfie segmentation, green-screen effects, background blur, and custom image compositing (Instagram / YouTube Remix style) built with **Jetpack Compose**, **CameraX**, **Google ML Kit**, and **OpenGL ES**.

---

## 🏗️ Architecture Overview

ChromaKit is built following **Clean Architecture** and strict design patterns, completely decoupled across 5 specialized modules:

```
                      ┌──────────────────────────────────────────────┐
                      │              :chromakit-compose              │
                      │  (ChromaKitPreview, rememberChromaController)│
                      └──────────────────────┬───────────────────────┘
                                             │
                      ┌──────────────────────▼───────────────────────┐
                      │               :chromakit-core                │
                      │   (ChromaPipeline, Strategy & Buffer Pools)  │
                      └───────────────┬──────────────┬───────────────┘
                                      │              │
         ┌────────────────────────────┼──────────────┼────────────────────────────┐
         │                            │              │                            │
┌────────▼──────────┐        ┌────────▼─────────┐    │     ┌──────────────────────▼───┐
│ :chromakit-camera │        │   :chromakit-ml  │    │     │    :chromakit-render     │
│ (CameraX / Zero-  │ ─────> │ (ML Kit Segmenter│ ───┼───> │ (OpenGL ES 2.0/3.0 Shaders│
│  copy Buffers)    │ Frame  │ + Temporal EMA)  │Mask│     │  & Real-Time Compositing)│
└───────────────────┘        └──────────────────┘    │     └──────────────────────────┘
                                                     │
                                      ┌──────────────▼───────────────┐
                                      │      Adaptive Governor       │
                                      │ (Dynamic Frame Throttling &  │
                                      │  Low-End Device Scaling)     │
                                      └──────────────────────────────┘
```

---

## 📦 Modules

| Module | Purpose | Key Components |
|---|---|---|
| [`:chromakit-core`](file:///Users/dhruvgodhani/Desktop/chromakit/chromakit-core) | Domain contracts, pipeline coordinator, telemetry & buffer pooling | `ChromaPipeline`, `ChromaFrame`, `ChromaMask`, `ChromaBackground`, `BufferPool` |
| [`:chromakit-camera`](file:///Users/dhruvgodhani/Desktop/chromakit/chromakit-camera) | CameraX capture & zero-copy frame management | `CameraXFrameSource`, `ImageAnalysis` analyzer |
| [`:chromakit-ml`](file:///Users/dhruvgodhani/Desktop/chromakit/chromakit-ml) | Real-time selfie segmentation & edge stabilization | `MlKitSelfieSegmenter`, `TemporalMaskSmoother`, `AdaptiveGovernor` |
| [`:chromakit-render`](file:///Users/dhruvgodhani/Desktop/chromakit/chromakit-render) | 60 FPS GPU-accelerated alpha blending & shaders | `ChromaRenderer`, `ChromaGLSurfaceView`, `ChromaShaders` |
| [`:chromakit-compose`](file:///Users/dhruvgodhani/Desktop/chromakit/chromakit-compose) | Developer-facing Jetpack Compose integration | `ChromaKitPreview`, `rememberChromaController`, `ChromaController` |
| [`:app`](file:///Users/dhruvgodhani/Desktop/chromakit/app) | Showcase sample app with remix controls & diagnostics | `MainActivity`, preset chips, tuning bottom sheet |

---

## ⚡ Design Patterns & Performance Engineering

1. **Pipeline Pattern**: Frames flow in a decoupled pipeline (`FrameSource` $\rightarrow$ `ChromaSegmenter` $\rightarrow$ `ChromaRenderer`).
2. **Strategy Pattern (`ChromaBackground`)**:
   - `ChromaBackground.None`: Pass-through camera feed.
   - `ChromaBackground.SolidColor(Color.GREEN)`: Traditional Green Screen / Chroma Keying.
   - `ChromaBackground.Image(bitmap)`: Virtual background image (YouTube Remix / Instagram).
   - `ChromaBackground.Blur(radius)`: Real-time Kawase background bokeh blur directly on the GPU.
   - `ChromaBackground.Transparent`: Transparent background for export/overlay.
3. **Object Pooling (`BufferPool`)**:
   Pre-allocated native ByteBuffers and Bitmaps prevent Garbage Collection (GC) pauses during 30–60 FPS streaming.
4. **Temporal Mask Smoothing (Exponential Moving Average)**:
   Stabilizes segmentation edges across successive frames to eliminate mask jittering and edge flicker.
5. **Decoupled Inference Loop**:
   Camera and OpenGL rendering operate continuously at 60 FPS, while ML inference runs asynchronously.
6. **Adaptive Hardware Governor**:
   Dynamically profiles frame latency on low-end chipsets and balances inference cadence without freezing the preview.

---

## 🚀 Quick Start (Plug & Play)

### 1. Add Dependency
```kotlin
dependencies {
    implementation(project(":chromakit-compose"))
}
```

### 2. Plug Into Any Jetpack Compose UI
```kotlin
@Composable
fun VideoScreen() {
    val controller = rememberChromaController()

    Box(modifier = Modifier.fillMaxSize()) {
        // Plug-and-play preview
        ChromaKitPreview(
            controller = controller,
            modifier = Modifier.fillMaxSize()
        )

        // Switch background dynamically
        Button(
            onClick = {
                controller.setBackground(ChromaBackground.SolidColor(android.graphics.Color.GREEN))
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text("Enable Green Screen")
        }
    }
}
```

### 3. Controller Controls & Features
```kotlin
// Switch camera lens (Front / Back)
controller.switchCamera()

// Set custom virtual background image
controller.setBackground(ChromaBackground.Image(pickedBitmap))

// Set background bokeh blur
controller.setBackground(ChromaBackground.Blur(radius = 16f))

// Real-time snapshot capture (OpenGL readback flipped to standard Bitmap)
controller.captureSnapshot { compositedBitmap ->
    // Save to gallery or share
}

// Static Image Segmentation (Green screen / blur on gallery photos directly)
val result = controller.processStaticImage(
    inputBitmap = galleryPhoto,
    background = ChromaBackground.SolidColor(Color.GREEN)
)

// Fine-tune edge feathering and smoothing
controller.setEdgeFeathering(0.04f)
controller.setTemporalSmoothing(0.70f)
controller.setConfidenceThreshold(0.50f)
controller.setQualityTier(QualityTier.AUTO)
```
