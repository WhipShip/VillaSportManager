package com.example.villasportmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.villasportmanager.ui.theme.VillaSportManagerTheme
import com.example.villasportmanager.ui.navigation.AppNavigation
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.villasportmanager.ui.features.update.UpdaterViewModel
import com.example.villasportmanager.ui.features.update.UpdaterDialog
import androidx.lifecycle.viewmodel.compose.viewModel

//--------------------------------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val updaterViewModel: UpdaterViewModel = viewModel()

            LaunchedEffect(Unit) {
                updaterViewModel.checkForUpdates(isSilent = true)
            }

            VillaSportManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        windowSizeClass = windowSizeClass,
                        onUpdateClick = { updaterViewModel.checkForUpdates(isSilent = false) }
                    )
                    UpdaterDialog(viewModel = updaterViewModel)
                }
            }
        }
    }
}

