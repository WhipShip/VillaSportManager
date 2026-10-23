package com.example.villasportmanager.ui.features.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.bookingTitle
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.components.sportImageRes
import com.example.villasportmanager.ui.theme.ErrorRed
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist
import com.example.villasportmanager.ui.theme.OnKayanLime
import kotlinx.coroutines.delay

@Composable
fun BookingSuccessScreen(
    bookingId: String,
    sportName: String,
    courtName: String,
    dateLabel: String,
    timeLabel: String,
    windowSizeClass: WindowSizeClass,
    viewModel: BookingViewModel,
    onDoneClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var askCancel by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.cancellationSuccess) {
        if (viewModel.cancellationSuccess) {
            snackbarHostState.showSnackbar("Booking cancelled")
            delay(1500)
            onDoneClick()
        }
    }
    LaunchedEffect(viewModel.cancellationError) {
        viewModel.cancellationError?.let { snackbarHostState.showSnackbar("Error: $it") }
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Cancel this booking?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("${bookingTitle(sportName, courtName)} on $dateLabel, $timeLabel will be released for other villas.") },
            confirmButton = {
                TextButton(onClick = { askCancel = false; viewModel.cancelBooking(bookingId) }) {
                    Text("Cancel booking", color = ErrorRed, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { askCancel = false }) {
                    Text("Keep it", color = KayanGreen, style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = Color.White
        )
    }

    val wide = windowSizeClass.layoutTier != LayoutTier.Compact
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Mist
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    modifier = Modifier.widthIn(max = if (wide) 880.dp else 520.dp).fillMaxWidth()
                ) {
                    if (wide) {
                        Row(Modifier.height(440.dp)) {
                            SuccessPhoto(sportName, Modifier.weight(1f).fillMaxHeight())
                            SuccessDetails(
                                sportName, courtName, dateLabel, timeLabel,
                                isCancelling = viewModel.isCancelling,
                                onDoneClick = onDoneClick,
                                onCancelClick = { askCancel = true },
                                modifier = Modifier.weight(1f).padding(32.dp)
                            )
                        }
                    } else {
                        Column {
                            SuccessPhoto(sportName, Modifier.fillMaxWidth().height(200.dp))
                            SuccessDetails(
                                sportName, courtName, dateLabel, timeLabel,
                                isCancelling = viewModel.isCancelling,
                                onDoneClick = onDoneClick,
                                onCancelClick = { askCancel = true },
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessPhoto(sportName: String, modifier: Modifier) {
    val wash = remember {
        Brush.verticalGradient(listOf(KayanGreen.copy(alpha = 0.35f), KayanGreen.copy(alpha = 0.85f)))
    }
    Box(modifier) {
        Image(
            painter = painterResource(sportImageRes(sportName)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(wash))
        Box(
            modifier = Modifier.align(Alignment.Center).size(88.dp).background(KayanLime, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(KayanIcons.Check, contentDescription = null, tint = OnKayanLime, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun SuccessDetails(
    sportName: String,
    courtName: String,
    dateLabel: String,
    timeLabel: String,
    isCancelling: Boolean,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("BOOKING CONFIRMED", style = MaterialTheme.typography.labelSmall, color = KayanGreen)
            Text(bookingTitle(sportName, courtName), style = MaterialTheme.typography.headlineMedium, color = Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailRow("Date", dateLabel)
            DetailRow("Time", timeLabel)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDoneClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KayanGreen, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            TextButton(
                onClick = onCancelClick,
                enabled = !isCancelling,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(color = ErrorRed, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Cancel booking", style = MaterialTheme.typography.labelLarge, color = ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = InkMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.End)
    }
}
