package com.move2unlock.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
        mutableStateOf("Stand where your full legs are visible.")
    }

    var phoneMoving by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val gyro =
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val movement =
                    abs(event.values[0]) +
                    abs(event.values[1]) +
                    abs(event.values[2])

                phoneMoving = movement > 0.25f
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int
            ) {}
        }

        gyro?.let {
            sensorManager.registerListener(
                listener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            hasPermission = it
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

        Text(status)

        if (!hasPermission) {
            Text("Camera permission is required.")
            return@Column
        }

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

                val detector =
                    PoseDetection.getClient(
                        PoseDetectorOptions.Builder()
                            .setDetectorMode(
                                PoseDetectorOptions.STREAM_MODE
                            )
                            .build()
                    )

                var downFrames = 0
                var upFrames = 0
                var wentDown = false
                var lastRepTime = 0L
                var lastPhoneMoveTime = 0L

                cameraProviderFuture.addListener({
                    val provider =
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

                                if (phoneMoving) {
                                    lastPhoneMoveTime =
                                        System.currentTimeMillis()

                                    downFrames = 0
                                    upFrames = 0
                                    wentDown = false

                                    activity.runOnUiThread {
                                        status = "Keep the phone still."
                                    }

                                    return@addOnSuccessListener
                                }

                                val now =
                                    System.currentTimeMillis()

                                if (now - lastPhoneMoveTime < 700) {
                                    downFrames = 0
                                    upFrames = 0
                                    wentDown = false

                                    return@addOnSuccessListener
                                }

                                val leftAngle =
                                    getLegAngle(
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_HIP
                                        )?.position,
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_KNEE
                                        )?.position,
                                        pose.getPoseLandmark(
                                            PoseLandmark.LEFT_ANKLE
                                        )?.position
                                    )

                                val rightAngle =
                                    getLegAngle(
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_HIP
                                        )?.position,
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_KNEE
                                        )?.position,
                                        pose.getPoseLandmark(
                                            PoseLandmark.RIGHT_ANKLE
                                        )?.position
                                    )

                                val angles =
                                    listOfNotNull(
                                        leftAngle,
                                        rightAngle
                                    )

                                if (angles.isEmpty()) {
                                    activity.runOnUiThread {
                                        status =
                                            "Move back so your hips, knees and ankles are visible."
                                    }
                                    return@addOnSuccessListener
                                }

                                val kneeAngle =
                                    angles.average()

                                if (!wentDown) {
                                    if (kneeAngle < 110) {
                                        downFrames++
                                    } else {
                                        downFrames = 0
                                    }

                                    if (downFrames >= 5) {
                                        wentDown = true
                                        upFrames = 0

                                        activity.runOnUiThread {
                                            status =
                                                "Good squat — stand up."
                                        }
                                    }
                                } else {
                                    if (kneeAngle > 155) {
                                        upFrames++
                                    } else {
                                        upFrames = 0
                                    }

                                    if (upFrames >= 5) {
                                        if (
                                            now - lastRepTime > 1200
                                        ) {
                                            lastRepTime = now
                                            wentDown = false
                                            downFrames = 0
                                            upFrames = 0

                                            activity.runOnUiThread {
                                                status = "Rep counted!"
                                                onRep()
                                            }
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

        Text(
            "Keep the phone stationary while completing each squat."
        )
    }
}

private fun getLegAngle(
    hip: PointF?,
    knee: PointF?,
    ankle: PointF?
): Double? {
    if (
        hip == null ||
        knee == null ||
        ankle == null
    ) {
        return null
    }

    val ax = hip.x - knee.x
    val ay = hip.y - knee.y

    val bx = ankle.x - knee.x
    val by = ankle.y - knee.y

    val dot =
        ax * bx +
        ay * by

    val magA =
        sqrt(
            (ax * ax + ay * ay).toDouble()
        )

    val magB =
        sqrt(
            (bx * bx + by * by).toDouble()
        )

    if (
        magA == 0.0 ||
        magB == 0.0
    ) {
        return null
    }

    val cosine =
        (dot / (magA * magB))
            .coerceIn(-1.0, 1.0)

    return Math.toDegrees(
        acos(cosine)
    )
}
