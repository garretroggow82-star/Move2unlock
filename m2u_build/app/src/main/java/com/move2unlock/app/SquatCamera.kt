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
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors
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
            Text("Camera permission is required to verify your squats.")
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

                    val options =
                        PoseDetectorOptions.Builder()
                            .setDetectorMode(
                                PoseDetectorOptions.STREAM_MODE
                            )
                            .build()

                    val detector =
                        PoseDetection.getClient(options)

                    var squatDown = false
                    var processing = false

                    cameraProviderFuture.addListener({

                        val cameraProvider =
                            cameraProviderFuture.get()

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

                        analysis.setAnalyzer(executor) { imageProxy ->

                            if (processing) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image

                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            processing = true

                            val image =
                                InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                            detector.process(image)
                                .addOnSuccessListener { pose ->

                                    val leftHip =
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_HIP
                                        )

                                    val leftKnee =
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_KNEE
                                        )

                                    val leftAnkle =
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_ANKLE
                                        )

                                    val rightHip =
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_HIP
                                        )

                                    val rightKnee =
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_KNEE
                                        )

                                    val rightAnkle =
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_ANKLE
                                        )

                                    val angles = mutableListOf<Double>()

                                    if (
                                        leftHip != null &&
                                        leftKnee != null &&
                                        leftAnkle != null &&
                                        leftHip.inFrameLikelihood > 0.5f &&
                                        leftKnee.inFrameLikelihood > 0.5f &&
                                        leftAnkle.inFrameLikelihood > 0.5f
                                    ) {
                                        angles += kneeAngle(
                                            leftHip.position,
                                            leftKnee.position,
                                            leftAnkle.position
                                        )
                                    }

                                    if (
                                        rightHip != null &&
                                        rightKnee != null &&
                                        rightAnkle != null &&
                                        rightHip.inFrameLikelihood > 0.5f &&
                                        rightKnee.inFrameLikelihood > 0.5f &&
                                        rightAnkle.inFrameLikelihood > 0.5f
                                    ) {
                                        angles += kneeAngle(
                                            rightHip.position,
                                            rightKnee.position,
                                            rightAnkle.position
                                        )
                                    }

                                    if (angles.isNotEmpty()) {
                                        val angle = angles.average()

                                        // Bent deeply enough = bottom of squat
                                        if (angle < 105) {
                                            squatDown = true
                                        }

                                        // Return to standing = completed rep
                                        if (
                                            squatDown &&
                                            angle > 160 &&
                                            reps < target
                                        ) {
                                            squatDown = false

                                            activity.runOnUiThread {
                                                onRep()
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    processing = false
                                    imageProxy.close()
                                }
                        }

                        try {
                            cameraProvider.unbindAll()

                            cameraProvider.bindToLifecycle(
                                activity,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (_: Exception) {
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            Text(
                "Stand far enough back so your hips, knees and ankles are visible."
            )
        }
    }
}

private fun kneeAngle(
    hip: PointF,
    knee: PointF,
    ankle: PointF
): Double {

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
        return 180.0
    }

    val cosine =
        (dot / (magA * magB))
            .coerceIn(-1.0, 1.0)

    return Math.toDegrees(
        acos(cosine)
    )
}
