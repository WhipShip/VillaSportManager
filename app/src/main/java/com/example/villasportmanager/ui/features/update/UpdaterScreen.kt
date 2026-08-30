package com.example.villasportmanager.ui.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UpdaterDialog(
    viewModel: UpdaterViewModel = viewModel(),
    onDismiss: () -> Unit = { viewModel.dismiss() }
) {
    val state by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    if (state is UpdateState.Idle) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state) {
                    is UpdateState.Checking -> "Checking for Updates"
                    is UpdateState.UpdateAvailable -> "Update Available"
                    is UpdateState.Downloading -> "Downloading Update"
                    is UpdateState.Patching -> "Applying Patch"
                    is UpdateState.ReadyToInstall -> "Ready to Install"
                    is UpdateState.Error -> "Update Error"
                    else -> ""
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val currentState = state) {
                    is UpdateState.Checking -> {
                        CircularProgressIndicator()
                        Text("Looking for new versions...")
                    }
                    is UpdateState.UpdateAvailable -> {
                        Text("A new version is available (${currentState.version.versionCode}).")
                        if (currentState.patch != null) {
                            Text("A small patch is available to update your app.")
                        } else {
                            Text("A full update will be downloaded.")
                        }
                    }
                    is UpdateState.Downloading -> {
                        CircularProgressIndicator()
                        Text("Downloading update files...")
                    }
                    is UpdateState.Patching -> {
                        CircularProgressIndicator()
                        Text("Applying delta patch (This may take a moment)...")
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text("The update is ready to be installed.")
                    }
                    is UpdateState.Error -> {
                        Text("Error: ${currentState.message}", color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.UpdateAvailable -> {
                    Button(onClick = { viewModel.downloadAndApply(context) }) {
                        Text("Update Now")
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Button(onClick = { viewModel.install(context) }) {
                        Text("Install")
                    }
                }
                is UpdateState.Error -> {
                    Button(onClick = { viewModel.checkForUpdates() }) {
                        Text("Retry")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading && state !is UpdateState.Patching) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
