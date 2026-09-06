package com.move2unlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Move2UnlockApp() }
    }
}

@Composable
fun Move2UnlockApp() {
    var selectedApp by remember { mutableStateOf("Instagram") }
    var reps by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }

    val apps = listOf(
        "Instagram",
        "Facebook",
        "TikTok",
        "X",
        "YouTube",
        "Games"
    )

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Move2Unlock", style = MaterialTheme.typography.headlineLarge)
            Text("Earn your screen time.")

            Text(
                "Choose an app to lock",
                style = MaterialTheme.typography.titleMedium
            )

            apps.forEach { app ->
                OutlinedButton(
                    onClick = {
                        selectedApp = app
                        reps = 0
                        unlocked = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedApp == app) "✓ $app" else app)
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (unlocked) "🔓 $selectedApp unlocked"
                        else "🔒 $selectedApp locked",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (!unlocked) {
                        Text("Complete 20 squats to earn 30 minutes.")
                        Text("Squats: $reps / 20")

                        Button(
                            onClick = {
                                if (reps < 20) reps++
                                if (reps + 1 >= 20) unlocked = true
                            }
                        ) {
                            Text("Complete Rep")
                        }
                    } else {
                        Text("You earned 30 minutes of access.")
                    }
                }
            }

            Text("Next build: automatic app detection and blocking.")
        }
    }
}
