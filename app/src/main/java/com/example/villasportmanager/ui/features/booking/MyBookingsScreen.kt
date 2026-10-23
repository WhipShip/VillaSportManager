package com.example.villasportmanager.ui.features.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.data.model.Sport
import com.example.villasportmanager.data.model.UserBooking
import com.example.villasportmanager.ui.components.GlassIconButton
import com.example.villasportmanager.ui.components.HeroCurveShape
import com.example.villasportmanager.ui.components.KayanFormat
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.PhotoHero
import com.example.villasportmanager.ui.components.bookingTitle
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.components.sportImageRes
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.ErrorContainer
import com.example.villasportmanager.ui.theme.ErrorRed
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanGreenContainer
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist
import com.example.villasportmanager.ui.theme.OnKayanGreenContainer
import com.example.villasportmanager.ui.theme.OnSlotBooked
import com.example.villasportmanager.ui.theme.SlotBooked
import com.example.villasportmanager.util.AppConstants
import java.time.ZonedDateTime
import java.util.Locale

private class BookingRow(
    val booking: UserBooking,
    val title: String,
    val sportName: String?,
    val whenLabel: String,
    val isUpcoming: Boolean
)

@Composable
fun MyBookingsScreen(
    viewModel: MyBookingsViewModel,
    windowSizeClass: WindowSizeClass,
    sports: List<Sport>,
    onBackClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var bookingToCancel by remember { mutableStateOf<BookingRow?>(null) }

    LaunchedEffect(viewModel.cancellationSuccess, viewModel.cancellationError) {
        if (viewModel.cancellationSuccess) {
            snackbarHostState.showSnackbar("Booking cancelled")
            viewModel.clearCancellationStatus()
        }
        viewModel.cancellationError?.let { error ->
            snackbarHostState.showSnackbar("Error: $error")
            viewModel.clearCancellationStatus()
        }
    }

    val rows = remember(viewModel.bookings, sports) { buildRows(viewModel.bookings, sports) }
    val upcoming = rows.filter { it.isUpcoming }
    val past = rows.filterNot { it.isUpcoming }
    val tier = windowSizeClass.layoutTier
    val sidePadding = if (tier == LayoutTier.Compact) 16.dp else 40.dp
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Mist,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding())) {
            PhotoHero(
                imageRes = R.drawable.sport_hero_background,
                shape = HeroCurveShape(rightEdge = 0.75f, controlX = 0.5f, controlY = 1.05f, leftEdge = 0.85f),
                modifier = Modifier.fillMaxWidth().height((if (tier == LayoutTier.Compact) 150.dp else 180.dp) + topInset)
            ) {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(KayanIcons.Back, "Back", onBackClick)
                    Text(
                        "My Bookings",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (tier == LayoutTier.Compact) 30.sp else 40.sp,
                        color = KayanLime
                    )
                    if (viewModel.isCancelling) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    viewModel.isLoading && rows.isEmpty() ->
                        CircularProgressIndicator(color = KayanGreen, modifier = Modifier.align(Alignment.Center))
                    viewModel.error != null && rows.isEmpty() ->
                        CenteredText(viewModel.error ?: "Unknown error", ErrorRed)
                    rows.isEmpty() ->
                        CenteredText("You have no bookings yet.", InkMuted)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 24.dp + bottomInset),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (upcoming.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Upcoming") }
                            items(upcoming, key = { it.booking.id }) { row ->
                                BookingCard(row, onCancelClick = { bookingToCancel = row })
                            }
                        }
                        if (past.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Past & cancelled") }
                            items(past, key = { it.booking.id }) { row ->
                                BookingCard(row, onCancelClick = null)
                            }
                        }
                    }
                }
            }
        }

        bookingToCancel?.let { row ->
            AlertDialog(
                onDismissRequest = { bookingToCancel = null },
                title = { Text("Cancel this booking?", style = MaterialTheme.typography.titleLarge) },
                text = { Text("${row.title} · ${row.whenLabel} will be released for other villas.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancelBooking(row.booking.id)
                        bookingToCancel = null
                    }) {
                        Text("Cancel booking", color = ErrorRed, style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bookingToCancel = null }) {
                        Text("Keep it", color = KayanGreen, style = MaterialTheme.typography.labelLarge)
                    }
                },
                containerColor = Color.White
            )
        }
    }
}

private fun buildRows(bookings: List<UserBooking>, sports: List<Sport>): List<BookingRow> {
    val zone = AppConstants.CLUB_ZONE_ID
    val now = ZonedDateTime.now(zone)
    val rows = bookings.map { booking ->
        val sportName = sports.firstOrNull { it.id == booking.courts?.sportId }?.name
        val start = runCatching { ZonedDateTime.parse(booking.startTime).withZoneSameInstant(zone) }.getOrNull()
        val end = runCatching { ZonedDateTime.parse(booking.endTime).withZoneSameInstant(zone) }.getOrNull()
        val whenLabel = if (start != null && end != null) {
            "${KayanFormat.relativeDay(start.toLocalDate(), now.toLocalDate())} · ${KayanFormat.range(start.toLocalTime(), end.toLocalTime())}"
        } else booking.startTime
        val isUpcoming = booking.status.equals("confirmed", ignoreCase = true) && (end?.isAfter(now) ?: false)
        BookingRow(booking, bookingTitle(sportName, booking.courts?.name), sportName, whenLabel, isUpcoming) to start
    }
    val upcoming = rows.filter { it.first.isUpcoming }.sortedBy { it.second }
    val past = rows.filterNot { it.first.isUpcoming }.sortedByDescending { it.second }
    return (upcoming + past).map { it.first }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun CenteredText(text: String, color: Color) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BookingCard(row: BookingRow, onCancelClick: (() -> Unit)?) {
    val status = row.booking.status.lowercase(Locale.ROOT)
    val (chipColor, chipText) = when {
        status == "cancelled" -> ErrorContainer to ErrorRed
        row.isUpcoming -> KayanGreenContainer to OnKayanGreenContainer
        else -> SlotBooked to OnSlotBooked
    }
    val chipLabel = when {
        status == "cancelled" -> "CANCELLED"
        row.isUpcoming -> "CONFIRMED"
        status == "confirmed" -> "PLAYED"
        else -> status.uppercase(Locale.ROOT)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(sportImageRes(row.sportName)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.whenLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .background(chipColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(chipLabel, style = MaterialTheme.typography.labelSmall, color = chipText)
                }
            }
            if (onCancelClick != null) {
                TextButton(onClick = onCancelClick, modifier = Modifier.height(48.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = ErrorRed)
                }
            }
        }
    }
}
