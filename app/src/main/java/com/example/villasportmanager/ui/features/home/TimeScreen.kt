package com.example.villasportmanager.ui.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.villasportmanager.util.AppConstants
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeScreen(onBackClick: () -> Unit) {
    // Holds the current time state in the club's timezone
    var currentTime by remember { mutableStateOf(LocalTime.now(AppConstants.CLUB_ZONE_ID)) }

    // A formatter to make the time look clean (e.g., "06:42:23 PM")
    val formatter = remember { DateTimeFormatter.ofPattern("hh:mm:ss a") }

    // This loop updates the time every second while the screen is open
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now(AppConstants.CLUB_ZONE_ID)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Current Time") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "The current time is:", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentTime.format(formatter),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
