package com.example.villasportmanager.ui.features.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.villasportmanager.data.model.TimeSlot
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    viewModel: BookingViewModel = viewModel(factory = BookingViewModelFactory()),
    windowSizeClass: WindowSizeClass,
    onBackClick: () -> Unit,
    onBookingSuccess: (courtName: String, date: String, time: String) -> Unit
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val detailDateFormatter = remember { DateTimeFormatter.ofPattern("MMMM-dd-yyyy") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.bookingSuccess) {
        if (viewModel.bookingSuccess) {
            // Find details for the success screen
            val selection = viewModel.selectedSlot
            // WE MUST EXTRACT DATA BEFORE clearBookingStatus or resetting selection
            val courtId = selection?.first
            val slot = selection?.second
            
            if (courtId != null && slot != null) {
                val courtName = viewModel.courtStates[courtId]?.court?.name ?: ""
                val endTime = viewModel.getEndTimeForSlot(slot.time)
                val dateStr = viewModel.selectedDate.format(detailDateFormatter)
                val timeStr = "${slot.time.format(timeFormatter)} - ${endTime.format(timeFormatter)}"
                
                onBookingSuccess(courtName, dateStr, timeStr)
                // We keep bookingSuccess true until navigated, but reset selection if needed
                // Actually, let's reset status in the success screen or after nav
            }
        }
    }

    // Error Dialog
    viewModel.bookingError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBookingStatus() },
            title = { Text("Booking Status") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBookingStatus() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.sportsList.firstOrNull()?.name ?: "Booking") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isLoading || viewModel.isBooking) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
            // TABLET LANDSCAPE: Two-pane layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Left Pane: Dates and Courts
                Column(modifier = Modifier.weight(1.5f)) {
                    DatePickerSection(viewModel, dayFormatter, dateFormatter)
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                items(viewModel.courtStates.values.toList()) { courtState ->
                                    CourtSection(
                                        courtState = courtState,
                                        windowSizeClass = windowSizeClass,
                                        selectedSlot = if (viewModel.selectedSlot?.first == courtState.court.id) viewModel.selectedSlot?.second else null,
                                        onSlotSelected = { slot -> viewModel.onSlotSelected(courtState.court.id, slot) },
                                        timeFormatter = timeFormatter,
                                        isBooking = viewModel.isBooking
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Pane: Booking Details
                Surface(
                    modifier = Modifier.width(350.dp).fillMaxHeight(),
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (viewModel.selectedSlot != null) {
                        BookingDetailsContent(viewModel, detailDateFormatter, timeFormatter)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a slot to book", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            // PHONE / PORTRAIT: Vertical layout with bottom details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                DatePickerSection(viewModel, dayFormatter, dateFormatter)

                Box(modifier = Modifier.weight(1f)) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(viewModel.courtStates.values.toList()) { courtState ->
                                CourtSection(
                                    courtState = courtState,
                                    windowSizeClass = windowSizeClass,
                                    selectedSlot = if (viewModel.selectedSlot?.first == courtState.court.id) viewModel.selectedSlot?.second else null,
                                    onSlotSelected = { slot -> viewModel.onSlotSelected(courtState.court.id, slot) },
                                    timeFormatter = timeFormatter,
                                    isBooking = viewModel.isBooking
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = viewModel.selectedSlot != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 16.dp,
                        shape = MaterialTheme.shapes.extraLarge.copy(bottomEnd = CornerSize(0.dp), bottomStart = CornerSize(0.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BookingDetailsContent(viewModel, detailDateFormatter, timeFormatter)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerSection(
    viewModel: BookingViewModel,
    dayFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(viewModel.availableDates) { date ->
            val isSelected = date == viewModel.selectedDate
            FilterChip(
                selected = isSelected,
                onClick = { viewModel.onDateSelected(date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Text(date.format(dayFormatter), style = MaterialTheme.typography.bodySmall)
                        Text(date.format(dateFormatter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun BookingDetailsContent(
    viewModel: BookingViewModel,
    detailDateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Booking Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        viewModel.selectedSlot?.let { (courtId, slot) ->
            val courtName = viewModel.courtStates[courtId]?.court?.name ?: ""
            val endTime = viewModel.getEndTimeForSlot(slot.time)

            Text("Court: $courtName")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Date: ${viewModel.selectedDate.format(detailDateFormatter)}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Time: ${slot.time.format(timeFormatter)} - ${endTime.format(timeFormatter)}")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.confirmBooking() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isBooking
        ) {
            if (viewModel.isBooking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Confirm Booking")
            }
        }
    }
}

@Composable
fun CourtSection(
    courtState: CourtState,
    windowSizeClass: WindowSizeClass,
    selectedSlot: TimeSlot?,
    onSlotSelected: (TimeSlot) -> Unit,
    timeFormatter: DateTimeFormatter,
    isBooking: Boolean
) {
    Column {
        Text(
            text = courtState.court.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Adaptive grid for slots
        val columns = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 3
            WindowWidthSizeClass.Medium -> 4
            WindowWidthSizeClass.Expanded -> 6
            else -> 3
        }
        val rows = (courtState.slots.size + columns - 1) / columns
        
        for (i in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (j in 0 until columns) {
                    val index = i * columns + j
                    if (index < courtState.slots.size) {
                        val slot = courtState.slots[index]
                        val isSelected = slot == selectedSlot
                        
                        Box(modifier = Modifier.weight(1f)) {
                            SlotButton(
                                slot = slot,
                                isSelected = isSelected,
                                onSlotSelected = onSlotSelected,
                                timeFormatter = timeFormatter,
                                isBooking = isBooking
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotButton(
    slot: TimeSlot,
    isSelected: Boolean,
    onSlotSelected: (TimeSlot) -> Unit,
    timeFormatter: DateTimeFormatter,
    isBooking: Boolean
) {
    val containerColor = when {
        slot.isBooked -> MaterialTheme.colorScheme.surfaceVariant
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        slot.isBooked -> MaterialTheme.colorScheme.onSurfaceVariant
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    val border = if (!slot.isBooked && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

    Button(
        onClick = { onSlotSelected(slot) },
        enabled = !slot.isBooked && !isBooking,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        border = border,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(
            text = slot.time.format(timeFormatter),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}
