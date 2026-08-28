package com.example.villasportmanager.ui.features.booking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.villasportmanager.data.model.UserBooking
import com.example.villasportmanager.util.AppConstants
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    viewModel: MyBookingsViewModel,
    windowSizeClass: WindowSizeClass,
    onBackClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var bookingToCancel by remember { mutableStateOf<UserBooking?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadBookings()
    }

    LaunchedEffect(viewModel.cancellationSuccess, viewModel.cancellationError) {
        if (viewModel.cancellationSuccess) {
            snackbarHostState.showSnackbar("Booking cancelled successfully")
            viewModel.clearCancellationStatus()
        }
        viewModel.cancellationError?.let { error ->
            snackbarHostState.showSnackbar("Error: $error")
            viewModel.clearCancellationStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.error != null) {
                Text(
                    text = viewModel.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (viewModel.bookings.isEmpty()) {
                Text(
                    text = "You have no bookings yet.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val columnCount = when (windowSizeClass.widthSizeClass) {
                    WindowWidthSizeClass.Compact -> 1
                    WindowWidthSizeClass.Medium -> 2
                    WindowWidthSizeClass.Expanded -> 3
                    else -> 1
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.bookings) { booking ->
                        BookingItem(
                            booking = booking,
                            onCancelClick = { bookingToCancel = booking }
                        )
                    }
                }
            }
        }

        // --- Cancellation Confirmation Dialog ---
        bookingToCancel?.let { booking ->
            AlertDialog(
                onDismissRequest = { bookingToCancel = null },
                title = { Text("Cancel Booking") },
                text = { Text("Are you sure you want to cancel your booking for ${booking.courts?.name}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelBooking(booking.id)
                            bookingToCancel = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Booking")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bookingToCancel = null }) {
                        Text("Keep Booking")
                    }
                }
            )
        }
    }
}

@Composable
fun BookingItem(
    booking: UserBooking,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.courts?.name ?: "Unknown Court",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val dateTime = try {
                        val zdt = ZonedDateTime.parse(booking.startTime)
                            .withZoneSameInstant(AppConstants.CLUB_ZONE_ID)
                        zdt.format(DateTimeFormatter.ofPattern("EEEE, MMM d 'at' HH:mm"))
                    } catch (e: Exception) {
                        booking.startTime
                    }
                    
                    Text(text = dateTime, style = MaterialTheme.typography.bodyMedium)
                }

                if (booking.status.lowercase() == "confirmed") {
                    TextButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                color = when (booking.status.lowercase()) {
                    "confirmed" -> Color(0xFFE8F5E9)
                    "cancelled" -> Color(0xFFFFEBEE)
                    else -> Color.LightGray
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = booking.status.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (booking.status.lowercase()) {
                        "confirmed" -> Color(0xFF2E7D32)
                        "cancelled" -> Color(0xFFC62828)
                        else -> Color.DarkGray
                    }
                )
            }
        }
    }
}
