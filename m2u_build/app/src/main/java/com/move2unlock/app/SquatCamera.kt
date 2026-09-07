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

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { hasPermission = it }

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

                var previousPoints =
                    emptyMap<Int, PointF>()

                var downFrames = 0
                var upFrames = 0
                var wentDown = false
                var lastRepTime = 0L

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

                                val landmarkTypes = listOf(
                                    PoseLandmark.LEFT_SHOULDER,
                                    PoseLandmark.RIGHT_SHOULDER,
                                    PoseLandmark.LEFT_HIP,
                                    PoseLandmark.RIGHT_HIP,
                                    PoseLandmark.LEFT_KNEE,
                                    PoseLandmark.RIGHT_KNEE,
                                    PoseLandmark.LEFT_ANKLE,
                                    PoseLandmark.RIGHT_ANKLE
                                )

                                val currentPoints =
                                    mutableMapOf<Int, PointF>()

                                landmarkTypes.forEach { type ->

                                    val landmark =
                                        pose.getPoseLandmark(type)

                                    if (
                                        landmark != null &&
                                        landmark.inFrameLikelihood > 0.55f
                                    ) {
                                        currentPoints[type] =
                                            landmark.position
                                    }
                                }

                                val cameraMoved =
                                    detectCameraMovement(
                                        previousPoints,
                                        currentPoints
                                    )

                                previousPoints = currentPoints

                                if (cameraMoved) {

                                    downFrames = 0
                                    upFrames = 0
                                    wentDown = false

                                    activity.runOnUiThread {
                                        status =
                                            "Keep the phone still."
                                    }

                                    return@addOnSuccessListener
                                }

                                val leftAngle =
                                    getLegAngle(
                                        currentPoints[
                                            PoseLandmark.LEFT_HIP
                                        ],
                                        currentPoints[
                                            PoseLandmark.LEFT_KNEE
                                        ],
                                        currentPoints[
                                            PoseLandmark.LEFT_ANKLE
                                        ]
                                    )

                                val rightAngle =
                                    getLegAngle(
                                        currentPoints[
                                            PoseLandmark.RIGHT_HIP
                                        ],
                                        currentPoints[
                                            PoseLandmark.RIGHT_KNEE
                                        ],
                                        currentPoints[
                                            PoseLandmark.RIGHT_ANKLE
                                        ]
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

                                    if (downFrames >= 4) {

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

                                    if (upFrames >= 4) {

                                        val now =
                                            System.currentTimeMillis()

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
            "Keep the phone stationary. Move your body, not the camera."
        )
    }
}

private fun detectCameraMovement(
    previous: Map<Int, PointF>,
    current: Map<Int, PointF>
): Boolean {

    val common =
        previous.keys.intersect(current.keys)

    if (common.size < 5) {
        return false
    }

    val changes = common.map { key ->

        val old = previous[key]!!
        val new = current[key]!!

        Pair(
            new.x - old.x,
            new.y - old.y
        )
    }

    val meanX =
        changes.map { it.first }.average()

    val meanY =
        changes.map { it.second }.average()

    val overallMovement =
        sqrt(
            meanX * meanX +
            meanY * meanY
        )

    val differenceFromGroup =
        changes.map { change ->

            val dx =
                change.first - meanX

            val dy =
                change.second - meanY

            sqrt(
                dx * dx +
                dy * dy
            )
        }.average()

    /*
       If nearly every body landmark moves together
       in the same direction, the phone probably moved.
    */

    return overallMovement > 14.0 &&
           differenceFromGroup < 8.0
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
            (ax * ax + ay * ay)
                .toDouble()
        )

    val magB =
        sqrt(
            (bx * bx + by * by)
                .toDouble()
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
