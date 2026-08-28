package com.example.villasportmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.example.villasportmanager.util.UpdateState
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.villasportmanager.ui.theme.VillaSportManagerTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import com.example.villasportmanager.ui.navigation.AppNavigation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

//--------------------------------------------------------------------------------------------------



import androidx.compose.runtime.rememberCoroutineScope
import com.example.villasportmanager.util.UpdateManager
import kotlinx.coroutines.launch

//--------------------------------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val updateManager = remember { UpdateManager(this) }

            DisposableEffect(Unit) {
                onDispose {
                    updateManager.unregisterReceiver()
                }
            }

            LaunchedEffect(Unit) {
                updateManager.checkForUpdates()
            }

            val updateState by updateManager.updateState.collectAsState()

            VillaSportManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(windowSizeClass)

                    // Update UI Overlay
                    when (val state = updateState) {
                        is UpdateState.UpdateAvailable -> {
                            AlertDialog(
                                onDismissRequest = { updateManager.dismissUpdate() },
                                title = { Text("Update Available") },
                                text = { Text("A new version of Kayan Club is available. Would you like to download it now?") },
                                confirmButton = {
                                    Button(onClick = { updateManager.startDownload(state.update) }) {
                                        Text("Download")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { updateManager.dismissUpdate() }) {
                                        Text("Later")
                                    }
                                }
                            )
                        }
                        is UpdateState.Downloading -> {
                            Dialog(onDismissRequest = {}) {
                                Card(
                                    modifier = Modifier.padding(16.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Downloading update...")
                                    }
                                }
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("Update Ready") },
                                text = { Text("The update has been downloaded. Click install to update the app.") },
                                confirmButton = {
                                    Button(onClick = { updateManager.installApk() }) {
                                        Text("Install Now")
                                    }
                                }
                            )
                        }
                        is UpdateState.Error -> {
                            // Optionally show error snackbar or toast
                            LaunchedEffect(state) {
                                // updateManager.dismissUpdate()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

