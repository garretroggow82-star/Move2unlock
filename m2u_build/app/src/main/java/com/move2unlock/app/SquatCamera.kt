package com.move2unlock.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun SquatCamera(
    reps: Int,
    target: Int,
    onRep: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "$reps / $target",
            style = MaterialTheme.typography.headlineMedium
        )

        if (!hasPermission) {
            Text("Camera permission is required.")
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                factory = { ctx ->

                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture =
                        ProcessCameraProvider.getInstance(ctx)

                    val executor =
                        Executors.newSingleThreadExecutor()

                    var baseline = -1.0
                    var downDetected = false
                    var lastRepTime = 0L

                    cameraProviderFuture.addListener({

                        val provider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        val analysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        analysis.setAnalyzer(executor) { imageProxy ->

                            val plane = imageProxy.planes.firstOrNull()

                            if (plane == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val buffer = plane.buffer
                            val remaining = buffer.remaining()

                            if (remaining < 1000) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val step = maxOf(1, remaining / 500)

                            var sum = 0L
                            var count = 0
                            var i = 0

                            while (i < remaining) {
                                val value = buffer.get(i).toInt() and 0xFF
                                sum += value
                                count++
                                i += step
                            }

                            val brightness =
                                if (count > 0) sum.toDouble() / count else 0.0

                            if (baseline < 0) {
                                baseline = brightness
                            } else {
                                baseline = baseline * 0.98 + brightness * 0.02

                                val movement = abs(brightness - baseline)

                                if (movement > 12) {
                                    downDetected = true
                                }

                                val now = System.currentTimeMillis()

                                if (
                                    downDetected &&
                                    movement < 4 &&
                                    now - lastRepTime > 1200 &&
                                    reps < target
                                ) {
                                    downDetected = false
                                    lastRepTime = now

                                    activity.runOnUiThread {
                                        onRep()
                                    }
                                }
                            }

                            imageProxy.close()
                        }

                        provider.unbindAll()

                        provider.bindToLifecycle(
                            activity,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                        )

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            Text("Stand back so your upper body is visible. Squat down and return to standing.")
        }
    }
}
