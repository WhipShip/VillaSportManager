package com.example.villasportmanager.ui.features.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtScreen(
    courtName: String,
    viewModel: CourtViewModel,
    windowSizeClass: WindowSizeClass,
    onBackClick: () -> Unit
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val detailDateFormatter = remember { DateTimeFormatter.ofPattern("MMMM-dd-yyyy") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.bookingSuccess, viewModel.bookingError) {
        if (viewModel.bookingSuccess) {
            snackbarHostState.showSnackbar("Booking confirmed!")
            viewModel.clearBookingStatus()
        }
        viewModel.bookingError?.let { error ->
            snackbarHostState.showSnackbar("Error: $error")
            viewModel.clearBookingStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(courtName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isCheckingDatabase || viewModel.isBooking) {
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
        val columnCount = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 3
            WindowWidthSizeClass.Medium -> 4
            WindowWidthSizeClass.Expanded -> 6
            else -> 3
        }

        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    DatePickerSection(viewModel, dayFormatter, dateFormatter)
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(viewModel.slots) { slot ->
                            SlotButton(
                                slot = slot,
                                isSelected = slot == viewModel.selectedSlot,
                                onSlotSelected = { viewModel.selectedSlot = it },
                                timeFormatter = timeFormatter,
                                isBooking = viewModel.isBooking
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.width(350.dp).fillMaxHeight(),
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (viewModel.selectedSlot != null) {
                        CourtBookingDetailsContent(courtName, viewModel, detailDateFormatter, timeFormatter)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a slot to book", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                DatePickerSection(viewModel, dayFormatter, dateFormatter)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(viewModel.slots) { slot ->
                        SlotButton(
                            slot = slot,
                            isSelected = slot == viewModel.selectedSlot,
                            onSlotSelected = { viewModel.selectedSlot = it },
                            timeFormatter = timeFormatter,
                            isBooking = viewModel.isBooking
                        )
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
                        CourtBookingDetailsContent(courtName, viewModel, detailDateFormatter, timeFormatter)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerSection(
    viewModel: CourtViewModel,
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
private fun SlotButton(
    slot: com.example.villasportmanager.data.model.TimeSlot,
    isSelected: Boolean,
    onSlotSelected: (com.example.villasportmanager.data.model.TimeSlot) -> Unit,
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
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = slot.time.format(timeFormatter))
    }
}

@Composable
fun CourtBookingDetailsContent(
    courtName: String,
    viewModel: CourtViewModel,
    detailDateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Booking Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        viewModel.selectedSlot?.let { slot ->
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
