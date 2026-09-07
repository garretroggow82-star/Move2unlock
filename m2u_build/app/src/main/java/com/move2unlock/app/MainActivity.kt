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

                    if (blockedPackage == null) {
                        Text("Open Facebook, Instagram, or TikTok to start.")
                        return@Column
                    }

                    if (!unlocked) {

                        Text(
                            "Complete 20 squats to unlock $appName for 30 minutes."
                        )

                        Text(
                            "$reps / 20",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                reps++

                                if (reps >= 20) {

                                    val unlockUntil =
                                        System.currentTimeMillis() + 1_800_000L

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
                        ) {
                            Text("Complete Rep")
                        }

                    } else {

                        Text("$appName is unlocked for 30 minutes.")

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
