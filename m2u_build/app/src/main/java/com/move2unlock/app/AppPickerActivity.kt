package com.move2unlock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AppPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AppPicker()
            }
        }
    }

    @Composable
    fun AppPicker() {
        val prefs = getSharedPreferences("move2unlock", MODE_PRIVATE)

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = remember {
            packageManager
                .queryIntentActivities(launchIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    Pair(
                        it.loadLabel(packageManager).toString(),
                        it.activityInfo.packageName
                    )
                }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
        }

        var selected by remember {
            mutableStateOf(
                prefs.getStringSet(
                    "blocked_apps",
                    setOf(
                        "com.facebook.katana",
                        "com.instagram.android",
                        "com.zhiliaoapp.musically"
                    )
                )?.toSet() ?: emptySet()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                "Choose Apps to Block",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(8.dp))

            Text("${selected.size} apps selected")

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(apps) { app ->
                    val checked = app.second in selected

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected =
                                    if (checked) selected - app.second
                                    else selected + app.second
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            app.first,
                            modifier = Modifier.weight(1f)
                        )

                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected =
                                    if (checked) selected - app.second
                                    else selected + app.second
                            }
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    prefs.edit()
                        .putStringSet("blocked_apps", selected)
                        .apply()

                    finish()
                }
            ) {
                Text("Save Selected Apps")
            }
        }
    }
}
