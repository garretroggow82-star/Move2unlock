package com.move2unlock.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

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

    var status by remember {
        mutableStateOf("Stand where your hips, knees and ankles are visible.")
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text(
            "$reps / $target",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(status)

        if (hasPermission) {

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                factory = { ctx ->

                    val previewView = PreviewView(ctx)

                    val providerFuture =
                        ProcessCameraProvider.getInstance(ctx)

                    val executor =
                        Executors.newSingleThreadExecutor()

                    val options =
                        PoseDetectorOptions.Builder()
                            .setDetectorMode(
                                PoseDetectorOptions.STREAM_MODE
                            )
                            .build()

                    val detector =
                        PoseDetection.getClient(options)

                    var wentDown = false
                    var lastRep = 0L
                    var lastShoulderY: Float? = null
                    var cameraMoved = false

                    providerFuture.addListener({

                        val provider = providerFuture.get()

                        val preview =
                            Preview.Builder().build()

                        preview.setSurfaceProvider(
                            previewView.surfaceProvider
                        )

                        val analysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        analysis.setAnalyzer(executor) { proxy ->

                            val mediaImage = proxy.image

                            if (mediaImage == null) {
                                proxy.close()
                                return@setAnalyzer
                            }

                            val image =
                                InputImage.fromMediaImage(
                                    mediaImage,
                                    proxy.imageInfo.rotationDegrees
                                )

                            detector.process(image)
                                .addOnSuccessListener { pose ->

                                    val leftShoulder =
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_SHOULDER
                                        )

                                    val rightShoulder =
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_SHOULDER
                                        )

                                    val shoulderYs = listOfNotNull(
                                        leftShoulder?.position?.y,
                                        rightShoulder?.position?.y
                                    )

                                    if (shoulderYs.isNotEmpty()) {
                                        val shoulderY = shoulderYs.average().toFloat()

                                        lastShoulderY?.let { previous ->
                                            val shift = abs(shoulderY - previous)

                                            if (shift > 55f) {
                                                cameraMoved = true
                                                wentDown = false

                                                activity.runOnUiThread {
                                                    status = "Keep the phone still."
                                                }
                                            }
                                        }

                                        lastShoulderY = shoulderY
                                    }

                                    val leftAngle = getLegAngle(
                                        pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position,
                                        pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position,
                                        pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)?.position
                                    )

                                    val rightAngle = getLegAngle(
                                        pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)?.position,
                                        pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)?.position,
                                        pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)?.position
                                    )

                                    val validAngles =
                                        listOfNotNull(leftAngle, rightAngle)

                                    if (validAngles.isEmpty()) {
                                        activity.runOnUiThread {
                                            status = "Move back so your full legs are visible."
                                        }
                                        return@addOnSuccessListener
                                    }

                                    val angle = validAngles.average()

                                    if (angle < 105 && !cameraMoved) {
                                        wentDown = true

                                        activity.runOnUiThread {
                                            status = "Good depth — stand back up."
                                        }
                                    }

                                    if (wentDown && angle > 160 && !cameraMoved) {

                                        val now = System.currentTimeMillis()

                                        if (now - lastRep > 1000) {
                                            wentDown = false
                                            lastRep = now

                                            activity.runOnUiThread {
                                                status = "Rep counted!"
                                                onRep()
                                            }
                                        }
                                    }

                                    if (angle > 160) {
                                        cameraMoved = false

                                        if (!wentDown) {
                                            activity.runOnUiThread {
                                                status = "Standing — squat down."
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    proxy.close()
                                }
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
        } else {
            Text("Camera permission is required.")
        }
    }
}

private fun getLegAngle(
    hip: PointF?,
    knee: PointF?,
    ankle: PointF?
): Double? {

    if (hip == null || knee == null || ankle == null) {
        return null
    }

    val ax = hip.x - knee.x
    val ay = hip.y - knee.y

    val bx = ankle.x - knee.x
    val by = ankle.y - knee.y

    val dot = ax * bx + ay * by

    val magA =
        sqrt((ax * ax + ay * ay).toDouble())

    val magB =
        sqrt((bx * bx + by * by).toDouble())

    if (magA == 0.0 || magB == 0.0) {
        return null
    }

    val cosine =
        (dot / (magA * magB))
            .coerceIn(-1.0, 1.0)

    return Math.toDegrees(acos(cosine))
}
