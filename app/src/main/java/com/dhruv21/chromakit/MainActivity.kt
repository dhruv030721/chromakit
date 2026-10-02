package com.dhruv21.chromakit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.dhruv21.chromakit.compose.ChromaKitPreview
import com.dhruv21.chromakit.compose.rememberChromaController
import com.dhruv21.chromakit.core.model.ChromaBackground
import com.dhruv21.chromakit.core.model.QualityTier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ChromaKitDemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaKitDemoScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required for ChromaKit live video segmentation.", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val controller = rememberChromaController()
    val stats by controller.stats.collectAsStateWithLifecycle()
    val config by controller.config.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }

    // Gallery Image Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                controller.setBackground(ChromaBackground.Image(bitmap))
            } catch (_: Exception) {}
        }
    }

    var capturedSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingStaticPhoto by remember { mutableStateOf(false) }

    // Static Photo Picker (Processes gallery photo directly)
    val staticPhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                @Suppress("DEPRECATION")
                val inputBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                isProcessingStaticPhoto = true
                coroutineScope.launch {
                    val result = controller.processStaticImage(inputBitmap)
                    capturedSnapshot = result
                    isProcessingStaticPhoto = false
                }
            } catch (_: Exception) {
                isProcessingStaticPhoto = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Live Composited Preview
        ChromaKitPreview(
            controller = controller,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Bar HUD (Telemetry + Controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Diagnostics Badge
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "FPS: ${stats.fps}",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "ML: ${stats.inferenceLatencyMs}ms | GL: ${stats.renderLatencyMs}ms",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    if (stats.droppedFrames > 0) {
                        Text(
                            text = "Skipped frames: ${stats.droppedFrames}",
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { controller.switchCamera() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        // 3. Shutter Capture Button & Static Photo Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Static Photo Segmentation Button
            Button(
                onClick = { staticPhotoPickerLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(if (isProcessingStaticPhoto) "Segmenting..." else "Segment Photo", fontSize = 12.sp)
            }

            // Live Camera Shutter Capture Button (Instagram / YouTube style)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .clickable {
                        controller.captureSnapshot { snapshot ->
                            capturedSnapshot = snapshot
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // 4. Bottom Remix Background Bar (Instagram / YouTube Remix style)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            color = Color.Transparent
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VIRTUAL BACKGROUND",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BackgroundPresetChip("Original") {
                        controller.setBackground(ChromaBackground.None)
                    }
                    BackgroundPresetChip("Green Screen") {
                        controller.setBackground(ChromaBackground.SolidColor(AndroidColor.GREEN))
                    }
                    BackgroundPresetChip("Bokeh Blur") {
                        controller.setBackground(ChromaBackground.Blur(radius = 16f))
                    }
                    BackgroundPresetChip("Cyberpunk") {
                        val cyberBmp = createPresetGradientBitmap(0xFF8A2387.toInt(), 0xFFE94057.toInt())
                        controller.setBackground(ChromaBackground.Image(cyberBmp))
                    }
                    BackgroundPresetChip("Studio Cyan") {
                        controller.setBackground(ChromaBackground.SolidColor(0xFF00E5FF.toInt()))
                    }
                    BackgroundPresetChip("Studio Dark") {
                        controller.setBackground(ChromaBackground.SolidColor(0xFF1E1E1E.toInt()))
                    }
                    BackgroundPresetChip(
                        name = "Gallery",
                        icon = { Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    ) {
                        imagePickerLauncher.launch("image/*")
                    }
                }
            }
        }

        // 5. Snapshot Preview Dialog
        capturedSnapshot?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { capturedSnapshot = null },
                confirmButton = {
                    TextButton(onClick = { capturedSnapshot = null }) {
                        Text("Close")
                    }
                },
                title = { Text("Captured Result") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            bitmap = snapshot.asImageBitmap(),
                            contentDescription = "Captured Snapshot",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            )
        }

        // 6. Settings & Tuning Bottom Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xFF1E1E24)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "Segmentation Pipeline Controls",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Edge Feathering: ${(config.edgeFeathering * 1000).toInt() / 10f}%", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = config.edgeFeathering,
                        onValueChange = { controller.setEdgeFeathering(it) },
                        valueRange = 0.0f..0.08f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Temporal Smoothing (Anti-Jitter): ${(config.temporalSmoothingFactor * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = config.temporalSmoothingFactor,
                        onValueChange = { controller.setTemporalSmoothing(it) },
                        valueRange = 0.0f..0.95f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Confidence Threshold: ${(config.confidenceThreshold * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = config.confidenceThreshold,
                        onValueChange = { controller.setConfidenceThreshold(it) },
                        valueRange = 0.1f..0.9f
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Device Quality Tier", color = Color.White, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QualityTier.entries.forEach { tier ->
                            FilterChip(
                                selected = config.qualityTier == tier,
                                onClick = { controller.setQualityTier(tier) },
                                label = { Text(tier.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun BackgroundPresetChip(
    name: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.invoke()
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

fun createPresetGradientBitmap(startColor: Int, endColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 400f, 400f,
        startColor, endColor,
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, 400f, 400f, paint)
    return bmp
}
