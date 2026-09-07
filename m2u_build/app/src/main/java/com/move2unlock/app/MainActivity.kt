package com.move2unlock.app

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

    private var blockedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra("blocked_package")

        setContent {
            Move2UnlockApp(blockedPackage)
        }
    }
}

@Composable
fun Move2UnlockApp(blockedPackage: String?) {

    val context = LocalContext.current

    val appName = when (blockedPackage) {
        "com.facebook.katana" -> "Facebook"
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically" -> "TikTok"
        else -> "App"
    }

    var reps by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }

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

                    if (!unlocked) {

                        Text("Complete 20 squats to unlock $appName for 30 minutes.")

                        Text(
                            "$reps / 20",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                                val newReps = reps + 1
                                reps = newReps

                                if (newReps >= 20) {

                                    blockedPackage?.let { pkg ->

                                        val unlockUntil =
                                            System.currentTimeMillis() +
                                            (30 * 60 * 1000)

                                        context
                                            .getSharedPreferences(
                                                "move2unlock",
                                                0
                                            )
                                            .edit()
                                            .putLong(
                                                "unlock_$pkg",
                                                unlockUntil
                                            )
                                            .apply()
                                    }

                                    unlocked = true
                                }
                            }
                        ) {
                            Text("Complete Rep")
                        }

                    } else {

                        Text("You earned 30 minutes of access.")

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                                blockedPackage?.let { pkg ->

                                    val launchIntent =
                                        context.packageManager
                                            .getLaunchIntentForPackage(pkg)

                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
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
