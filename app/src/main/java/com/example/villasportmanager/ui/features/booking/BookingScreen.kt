package com.example.villasportmanager.ui.features.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.data.model.TimeSlot
import com.example.villasportmanager.ui.components.DateStrip
import com.example.villasportmanager.ui.components.GlassIconButton
import com.example.villasportmanager.ui.components.HeroCurveShape
import com.example.villasportmanager.ui.components.KayanFormat
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.PhotoCard
import com.example.villasportmanager.ui.components.PhotoHero
import com.example.villasportmanager.ui.components.SlotCell
import com.example.villasportmanager.ui.components.SlotVisual
import com.example.villasportmanager.ui.components.SummaryBar
import com.example.villasportmanager.ui.components.bookingTitle
import com.example.villasportmanager.ui.components.isShort
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.components.sportImageRes
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanGreenDeep
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist
import com.example.villasportmanager.ui.theme.OnSlotBooked
import com.example.villasportmanager.ui.theme.SlotBooked
import com.example.villasportmanager.util.AppConstants
import java.time.LocalDate
import java.time.LocalTime

/**
 * Booking screen, design direction B.
 * - Compact: sport chips, day pills, court tabs and a vertical time list; floating summary bar.
 * - Medium / Expanded: court × time availability matrix; on large tablets the sports sit in a
 *   photo column on the left. The summary bar is always visible at the bottom.
 */
@Composable
fun BookingScreen(
    viewModel: BookingViewModel,
    windowSizeClass: WindowSizeClass,
    onBackClick: () -> Unit,
    onBookingConfirmed: (ConfirmedBooking) -> Unit
) {
    // Navigate once a booking is created, then reset the ViewModel's one-shot state.
    val confirmed = viewModel.confirmedBooking
    LaunchedEffect(confirmed) {
        if (confirmed != null) {
            onBookingConfirmed(confirmed)
            viewModel.consumeConfirmedBooking()
        }
    }

    viewModel.bookingError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBookingStatus() },
            title = { Text("Couldn't book this slot", style = MaterialTheme.typography.titleLarge) },
            text = { Text(error, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBookingStatus() }) {
                    Text("OK", color = KayanGreen, style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = Color.White
        )
    }

    val state = rememberBookingUiState(viewModel)
    val tier = windowSizeClass.layoutTier

    if (tier == LayoutTier.Compact) {
        BookingPhoneLayout(state, viewModel, onBackClick)
    } else {
        BookingTabletLayout(
            state = state,
            viewModel = viewModel,
            showSportColumn = tier == LayoutTier.Expanded && !windowSizeClass.isShort,
            onBackClick = onBackClick
        )
    }
}

// ------------------------------------------------------------------------------------------
// UI state derived from the ViewModel
// ------------------------------------------------------------------------------------------

private class BookingUiState(
    val sportName: String,
    val sportNames: List<String>,
    val courts: List<CourtState>,
    val times: List<LocalTime>,
    val selection: Pair<String, TimeSlot>?,
    val durationMinutes: Int,
    val date: LocalDate,
    val isLoading: Boolean,
    val isBooking: Boolean
) {
    fun endOf(start: LocalTime): LocalTime = start.plusMinutes(durationMinutes.toLong())

    fun visualFor(courtId: String, slot: TimeSlot): SlotVisual = when {
        slot.isMine -> SlotVisual.Mine
        slot.isBooked -> SlotVisual.Booked
        selection?.first == courtId && selection.second.time == slot.time -> SlotVisual.Selected
        else -> SlotVisual.Available
    }

    val selectedCourtName: String?
        get() = selection?.let { (courtId, _) -> courts.firstOrNull { it.court.id == courtId }?.court?.name }
}

@Composable
private fun rememberBookingUiState(viewModel: BookingViewModel): BookingUiState {
    val courts = viewModel.courtStates.values.sortedBy { it.court.name }
    val times = courts.flatMap { court -> court.slots.map { it.time } }.distinct().sorted()
    return BookingUiState(
        sportName = viewModel.currentSportName ?: viewModel.sportsList.firstOrNull()?.name.orEmpty(),
        sportNames = viewModel.allSports.map { it.name }.ifEmpty { DefaultSportNames },
        courts = courts,
        times = times,
        selection = viewModel.selectedSlot,
        durationMinutes = viewModel.sportsList.firstOrNull()?.slotDurationMinutes ?: 60,
        date = viewModel.selectedDate,
        isLoading = viewModel.isLoading,
        isBooking = viewModel.isBooking
    )
}

/** On today's schedule, start the list around the current hour instead of opening time. */
@Composable
private fun ScrollToNow(listState: LazyListState, date: LocalDate, times: List<LocalTime>) {
    LaunchedEffect(date, times.size) {
        if (times.isEmpty()) return@LaunchedEffect
        val today = LocalDate.now(AppConstants.CLUB_ZONE_ID)
        val index = if (date == today) {
            val now = LocalTime.now(AppConstants.CLUB_ZONE_ID)
            times.indexOfFirst { !it.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }
                .let { if (it < 0) times.lastIndex else it }
        } else 0
        listState.scrollToItem(index)
    }
}

// ------------------------------------------------------------------------------------------
// Phone
// ------------------------------------------------------------------------------------------

@Composable
private fun BookingPhoneLayout(state: BookingUiState, viewModel: BookingViewModel, onBackClick: () -> Unit) {
    var chosenCourtId by rememberSaveable(state.sportName) { mutableStateOf<String?>(null) }
    val activeCourtId = chosenCourtId?.takeIf { id -> state.courts.any { it.court.id == id } }
        ?: state.selection?.first
        ?: state.courts.firstOrNull()?.court?.id
    val activeCourt = state.courts.firstOrNull { it.court.id == activeCourtId }
    val listState = rememberLazyListState()
    ScrollToNow(listState, state.date, activeCourt?.slots?.map { it.time }.orEmpty())

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Mist)) {
        Column(Modifier.fillMaxSize()) {
            PhotoHero(
                imageRes = R.drawable.sport_hero_background,
                shape = HeroCurveShape(rightEdge = 0.75f, controlX = 0.5f, controlY = 1.05f, leftEdge = 0.85f),
                modifier = Modifier.fillMaxWidth().height(150.dp + topInset)
            ) {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(KayanIcons.Back, "Back", onBackClick)
                    Text(
                        "Sport Booking",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = KayanLime
                    )
                }
            }

            SportChipRow(
                sportNames = state.sportNames,
                selected = state.sportName,
                onSelect = { viewModel.switchSport(it) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )

            DateStrip(
                dates = viewModel.availableDates,
                selected = state.date,
                onSelect = { viewModel.onDateSelected(it) },
                fillWidth = false,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (state.courts.size > 1) {
                CourtTabs(
                    courts = state.courts,
                    activeCourtId = activeCourtId,
                    onSelect = { chosenCourtId = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    activeCourt == null && state.isLoading ->
                        CircularProgressIndicator(color = KayanGreen, modifier = Modifier.align(Alignment.Center))
                    activeCourt == null ->
                        EmptyMessage("No courts are available for ${state.sportName.ifBlank { "this sport" }}.")
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(activeCourt.slots, key = { it.time.toSecondOfDay() }) { slot ->
                            val visual = state.visualFor(activeCourt.court.id, slot)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    KayanFormat.hourLabel(slot.time),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (visual == SlotVisual.Selected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (visual == SlotVisual.Selected) Ink else InkMuted,
                                    modifier = Modifier.width(62.dp)
                                )
                                SlotCell(
                                    visual = visual,
                                    label = slotLabel(visual, slot.time, state),
                                    onClick = { viewModel.onSlotSelected(activeCourt.court.id, slot) },
                                    enabled = !state.isBooking,
                                    modifier = Modifier.weight(1f).height(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.selection != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selection = state.selection
            if (selection != null) {
                SummaryBar(
                    title = bookingTitle(state.sportName, state.selectedCourtName),
                    subtitle = "${state.date.format(KayanFormat.dateShort)} · ${KayanFormat.range(selection.second.time, state.endOf(selection.second.time))}",
                    actionLabel = "Book it",
                    onAction = { viewModel.confirmBooking() },
                    loading = state.isBooking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }
        }
    }
}

private fun slotLabel(visual: SlotVisual, time: LocalTime, state: BookingUiState): String = when (visual) {
    SlotVisual.Available -> "Available"
    SlotVisual.Booked -> "Booked"
    SlotVisual.Mine -> "Your booking"
    SlotVisual.Selected -> "Selected · ${KayanFormat.rangeShort(time, state.endOf(time))}"
}

@Composable
private fun SportChipRow(
    sportNames: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chip: @Composable (String, Modifier) -> Unit = { name, chipModifier ->
        val isSelected = name.equals(selected, ignoreCase = true)
        Column(
            modifier = chipModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val shape = RoundedCornerShape(14.dp)
            Surface(
                onClick = { onSelect(name) },
                shape = shape,
                color = Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .then(if (isSelected) Modifier.border(3.dp, KayanGreen, shape) else Modifier)
                    .semantics { this.selected = isSelected }
            ) {
                Image(
                    painter = painterResource(sportImageRes(name)),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape)
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Ink else InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (sportNames.size <= 4) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sportNames.forEach { chip(it, Modifier.weight(1f)) }
        }
    } else {
        LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sportNames) { chip(it, Modifier.width(84.dp)) }
        }
    }
}

@Composable
private fun CourtTabs(
    courts: List<CourtState>,
    activeCourtId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val fitsEvenly = courts.size <= 3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SlotBooked, RoundedCornerShape(24.dp))
            .padding(4.dp)
            .then(if (fitsEvenly) Modifier else Modifier.horizontalScroll(scroll)),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        courts.forEach { courtState ->
            val isActive = courtState.court.id == activeCourtId
            Surface(
                onClick = { onSelect(courtState.court.id) },
                shape = RoundedCornerShape(20.dp),
                color = if (isActive) Color.White else Color.Transparent,
                modifier = Modifier
                    .height(40.dp)
                    .then(if (fitsEvenly) Modifier.weight(1f) else Modifier.width(120.dp))
                    .semantics { selected = isActive }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        courtState.court.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) Ink else OnSlotBooked,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Tablet / foldable
// ------------------------------------------------------------------------------------------

@Composable
private fun BookingTabletLayout(
    state: BookingUiState,
    viewModel: BookingViewModel,
    showSportColumn: Boolean,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        if (showSportColumn) {
            Column(Modifier.width(272.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TabletHeader(onBackClick)
                state.sportNames.forEach { name ->
                    PhotoCard(
                        imageRes = sportImageRes(name),
                        label = name,
                        onClick = { viewModel.switchSport(name) },
                        selected = name.equals(state.sportName, ignoreCase = true),
                        labelSize = 22,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!showSportColumn) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TabletHeader(onBackClick)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(state.sportNames) { name ->
                            val isSelected = name.equals(state.sportName, ignoreCase = true)
                            Surface(
                                onClick = { viewModel.switchSport(name) },
                                shape = RoundedCornerShape(24.dp),
                                color = if (isSelected) KayanGreen else Color.White,
                                modifier = Modifier.height(48.dp).semantics { selected = isSelected }
                            ) {
                                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) Color.White else Ink
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DateStrip(
                dates = viewModel.availableDates,
                selected = state.date,
                onSelect = { viewModel.onDateSelected(it) },
                fillWidth = true
            )

            AvailabilityMatrix(
                state = state,
                onSelect = { courtId, slot -> viewModel.onSlotSelected(courtId, slot) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            val selection = state.selection
            SummaryBar(
                title = if (selection != null) bookingTitle(state.sportName, state.selectedCourtName) else state.sportName,
                subtitle = if (selection != null) {
                    "${state.date.format(KayanFormat.dateLong)} · ${KayanFormat.range(selection.second.time, state.endOf(selection.second.time))} · ${state.durationMinutes} min"
                } else {
                    "Pick a free slot to book"
                },
                actionLabel = "Book it",
                onAction = { viewModel.confirmBooking() },
                actionEnabled = selection != null,
                loading = state.isBooking,
                wide = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TabletHeader(onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = onBackClick,
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(KayanIcons.Back, contentDescription = "Back", tint = Ink)
            }
        }
        Text(
            "Sport Booking",
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = KayanGreenDeep,
            maxLines = 1
        )
    }
}

/** Courts across the top, times down the side. Scrolls sideways when there are many courts. */
@Composable
private fun AvailabilityMatrix(
    state: BookingUiState,
    onSelect: (String, TimeSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    ScrollToNow(listState, state.date, state.times)

    Surface(color = Color.White, shape = RoundedCornerShape(28.dp), modifier = modifier) {
        when {
            state.courts.isEmpty() && state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KayanGreen)
            }
            state.courts.isEmpty() -> EmptyMessage("No courts are available for ${state.sportName.ifBlank { "this sport" }}.")
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                val timeColumn = 76.dp
                val gap = 10.dp
                val count = state.courts.size
                val fittedColumn: Dp = (maxWidth - timeColumn - gap * count) / count
                val columnWidth = max(fittedColumn, 150.dp)
                val contentWidth = timeColumn + (columnWidth + gap) * count
                val horizontal = rememberScrollState()

                Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = contentWidth > maxWidth)) {
                    Column(Modifier.width(contentWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.height(46.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(timeColumn))
                            state.courts.forEach { courtState ->
                                Text(
                                    courtState.court.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ink,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(columnWidth)
                                )
                            }
                        }
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.times, key = { it.toSecondOfDay() }) { time ->
                                val rowSelected = state.selection?.second?.time == time
                                Row(Modifier.height(46.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        KayanFormat.hourLabel(time),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (rowSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (rowSelected) Ink else InkMuted,
                                        modifier = Modifier.width(timeColumn)
                                    )
                                    state.courts.forEach { courtState ->
                                        val slot = courtState.slots.firstOrNull { it.time == time }
                                        if (slot == null) {
                                            Spacer(Modifier.width(columnWidth))
                                        } else {
                                            val visual = state.visualFor(courtState.court.id, slot)
                                            SlotCell(
                                                visual = visual,
                                                label = slotLabel(visual, time, state),
                                                onClick = { onSelect(courtState.court.id, slot) },
                                                centered = true,
                                                enabled = !state.isBooking,
                                                modifier = Modifier.width(columnWidth).fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = InkMuted, textAlign = TextAlign.Center)
    }
}
