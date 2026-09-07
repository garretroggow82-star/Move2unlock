package com.move2unlock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var blockedPackage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadBlockedPackage(intent)

        setContent {
            Move2UnlockApp(blockedPackage)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadBlockedPackage(intent)
    }

    private fun loadBlockedPackage(intent: Intent?) {
        val prefs = getSharedPreferences("move2unlock", MODE_PRIVATE)

        blockedPackage =
            intent?.getStringExtra("blocked_package")
                ?: prefs.getString("pending_package", null)
    }
}

@Composable
fun Move2UnlockApp(blockedPackage: String?) {

    val context = LocalContext.current

    val appName = when (blockedPackage) {
        "com.facebook.katana" -> "Facebook"
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically" -> "TikTok"
        else -> "Choose a blocked app"
    }

    var reps by remember(blockedPackage) {
        mutableIntStateOf(0)
    }

    var unlocked by remember(blockedPackage) {
        mutableStateOf(false)
    }

    var unlockMinutes by remember {
        mutableStateOf(
            context.getSharedPreferences("move2unlock", 0)
                .getInt("unlock_minutes", 30)
        )
    }

    var targetReps by remember {
        mutableStateOf(
            context.getSharedPreferences("move2unlock", 0)
                .getInt("target_reps", 20)
        )
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Text(
                "Move2Unlock",
                style = MaterialTheme.typography.headlineLarge
            )

            Text("Earn your screen time.")

            Text("Squats required: $targetReps")

            Text("Unlock time: $unlockMinutes minutes")

            Slider(
                value = unlockMinutes.toFloat(),
                onValueChange = { value ->
                    val rounded = ((value / 5).toInt() * 5).coerceIn(5, 60)
                    unlockMinutes = rounded
                },
                onValueChangeFinished = {
                    context.getSharedPreferences("move2unlock", 0)
                        .edit()
                        .putInt("unlock_minutes", unlockMinutes)
                        .apply()
                },
                valueRange = 5f..60f,
                steps = 10
            )

            Slider(
                value = targetReps.toFloat(),
                onValueChange = { value ->
                    val rounded = ((value / 5).toInt() * 5).coerceIn(5, 100)
                    targetReps = rounded
                },
                onValueChangeFinished = {
                    context.getSharedPreferences("move2unlock", 0)
                        .edit()
                        .putInt("target_reps", targetReps)
                        .apply()
                },
                valueRange = 5f..100f,
                steps = 18
            )


            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {

                    Text(
                        if (unlocked)
                            "🔓 $appName Unlocked"
                        else
                            "🔒 $appName Locked",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    if (blockedPackage == null) {
                        Text("Open Facebook, Instagram, or TikTok to start.")
                        return@Column
                    }

                    if (!unlocked) {

                        Text(
                            "Complete $targetReps squats to unlock $appName for $unlockMinutes minutes."
                        )

                        SquatCamera(
                            reps = reps,
                            target = targetReps,
                            onRep = {
                                val newReps = reps + 1
                                reps = newReps

                                if (newReps >= targetReps) {

                                    val unlockUntil =
                                        System.currentTimeMillis() + (unlockMinutes * 60_000L)

                                    context
                                        .getSharedPreferences("move2unlock", 0)
                                        .edit()
                                        .putLong(
                                            "unlock_$blockedPackage",
                                            unlockUntil
                                        )
                                        .apply()

                                    unlocked = true
                                }
                            }
                        )

                    } else {

                        Text("$appName is unlocked for $unlockMinutes minutes.")

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                                val launchIntent =
                                    context.packageManager
                                        .getLaunchIntentForPackage(blockedPackage)

                                if (launchIntent != null) {
                                    launchIntent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                    )

                                    context.startActivity(launchIntent)
                                }
                            }
                        ) {
                            Text("Open $appName")
                        }
                    }
                }
            }
        }
    }
}
