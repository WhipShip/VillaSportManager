package com.example.villasportmanager.ui.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.data.model.Sport
import com.example.villasportmanager.data.model.UserBooking
import com.example.villasportmanager.ui.components.GlassIconButton
import com.example.villasportmanager.ui.components.KayanCrest
import com.example.villasportmanager.ui.components.KayanFormat
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.LimeButton
import com.example.villasportmanager.ui.components.PhotoHero
import com.example.villasportmanager.ui.components.bookingTitle
import com.example.villasportmanager.ui.components.isShort
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist
import com.example.villasportmanager.ui.theme.OnKayanLime
import com.example.villasportmanager.ui.theme.OnSlotAvailable
import com.example.villasportmanager.ui.theme.PhotoScrim
import com.example.villasportmanager.ui.theme.VillaSportManagerTheme
import com.example.villasportmanager.util.AppConstants
import java.time.LocalDate
import java.time.ZonedDateTime

// ------------------------------------------------------------------------------------------
// Models
// ------------------------------------------------------------------------------------------

/** The next upcoming booking, already formatted for display. */
data class NextBookingInfo(
    val title: String,      // "Padel · Court 1"
    val dayLabel: String,   // "Today" / "Tomorrow" / "Sat 24 Oct"
    val timeLabel: String   // "8:00 – 9:00 PM"
)

/** Club facilities shown as tiles. [sportName] is set when the service is bookable as a sport. */
enum class ClubService(val label: String, val shortLabel: String, val sportName: String?) {
    Gym("Gym", "Gym", null),
    Swimming("Swimming", "Swimming", null),
    Tennis("Tennis", "Tennis", "Tennis"),
    BeautyCenter("Beauty Center", "Beauty", null),
    Vr("VR", "VR", null),
    Aerobics("Aerobics Room", "Aerobics", null);

    val icon: ImageVector
        get() = when (this) {
            Gym -> KayanIcons.Gym
            Swimming -> KayanIcons.Swimming
            Tennis -> KayanIcons.Tennis
            BeautyCenter -> KayanIcons.Beauty
            Vr -> KayanIcons.Vr
            Aerobics -> KayanIcons.Aerobics
        }
}

/**
 * Picks the soonest confirmed booking that hasn't ended yet.
 * Returns the formatted booking (or null) and the number of upcoming bookings.
 */
fun summarizeUpcoming(bookings: List<UserBooking>, sports: List<Sport>): Pair<NextBookingInfo?, Int> {
    val zone = AppConstants.CLUB_ZONE_ID
    val now = ZonedDateTime.now(zone)
    val upcoming = bookings.mapNotNull { booking ->
        if (!booking.status.equals("confirmed", ignoreCase = true)) return@mapNotNull null
        try {
            val start = ZonedDateTime.parse(booking.startTime).withZoneSameInstant(zone)
            val end = ZonedDateTime.parse(booking.endTime).withZoneSameInstant(zone)
            if (end.isAfter(now)) Triple(booking, start, end) else null
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.second }

    val first = upcoming.firstOrNull() ?: return null to 0
    val (booking, start, end) = first
    val sportName = sports.firstOrNull { it.id == booking.courts?.sportId }?.name
    val info = NextBookingInfo(
        title = bookingTitle(sportName, booking.courts?.name),
        dayLabel = KayanFormat.relativeDay(start.toLocalDate(), now.toLocalDate()),
        timeLabel = KayanFormat.range(start.toLocalTime(), end.toLocalTime())
    )
    return info to upcoming.size
}

// ------------------------------------------------------------------------------------------
// Screen
// ------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    displayName: String?,
    windowSizeClass: WindowSizeClass,
    nextBooking: NextBookingInfo?,
    upcomingCount: Int,
    onSportBookingClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onServiceClick: (ClubService) -> Unit,
    onUpdateClick: () -> Unit,
    onLogOutClick: () -> Unit
) {
    val greetingName = if (displayName.isNullOrBlank()) "Villa 14!" else "$displayName!"
    val useSplit = windowSizeClass.layoutTier == LayoutTier.Expanded && !windowSizeClass.isShort

    if (useSplit) {
        HomeTabletLayout(
            greetingName = greetingName,
            nextBooking = nextBooking,
            upcomingCount = upcomingCount,
            onSportBookingClick = onSportBookingClick,
            onMyBookingsClick = onMyBookingsClick,
            onServiceClick = onServiceClick,
            onUpdateClick = onUpdateClick,
            onLogOutClick = onLogOutClick
        )
    } else {
        HomePhoneLayout(
            greetingName = greetingName,
            isMedium = windowSizeClass.layoutTier != LayoutTier.Compact,
            nextBooking = nextBooking,
            onSportBookingClick = onSportBookingClick,
            onMyBookingsClick = onMyBookingsClick,
            onServiceClick = onServiceClick,
            onUpdateClick = onUpdateClick,
            onLogOutClick = onLogOutClick
        )
    }
}

// ------------------------------------------------------------------------------------------
// Phone / foldable layout: curved hero + bento overlapping it
// ------------------------------------------------------------------------------------------

@Composable
private fun HomePhoneLayout(
    greetingName: String,
    isMedium: Boolean,
    nextBooking: NextBookingInfo?,
    onSportBookingClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onServiceClick: (ClubService) -> Unit,
    onUpdateClick: () -> Unit,
    onLogOutClick: () -> Unit
) {
    val heroHeight = if (isMedium) 340.dp else 300.dp
    val overlap = 52.dp
    val sidePadding = if (isMedium) 32.dp else 16.dp
    val serviceColumns = if (isMedium) 6 else 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mist)
            .verticalScroll(rememberScrollState())
    ) {
        PhotoHero(
            imageRes = R.drawable.main_hero_background,
            modifier = Modifier.fillMaxWidth().height(heroHeight)
        ) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KayanCrest(size = 34.dp)
                    Text(
                        "Kayan Club",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    HomeMenuButton(onUpdateClick = onUpdateClick, onLogOutClick = onLogOutClick, onPhoto = true)
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(start = 4.dp, end = 12.dp)) {
                    Text(
                        "Welcome",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isMedium) 36.sp else 30.sp,
                        lineHeight = 34.sp,
                        color = Color.White
                    )
                    GreetingName(greetingName, maxSize = if (isMedium) 60.sp else 50.sp, minSize = 28.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = heroHeight - overlap, start = sidePadding, end = sidePadding)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SportBookingTile(
                onClick = onSportBookingClick,
                large = isMedium,
                modifier = Modifier.fillMaxWidth().height(if (isMedium) 200.dp else 156.dp)
            )
            MyBookingsRow(nextBooking = nextBooking, onClick = onMyBookingsClick)

            ClubService.entries.chunked(serviceColumns).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { service ->
                        ServiceTile(
                            service = service,
                            onClick = { onServiceClick(service) },
                            compact = true,
                            modifier = Modifier.weight(1f).height(84.dp)
                        )
                    }
                    repeat(serviceColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onUpdateClick) {
                    Text("Update app", style = MaterialTheme.typography.labelLarge, color = OnSlotAvailable)
                }
                TextButton(onClick = onLogOutClick) {
                    Text("Log out", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = InkMuted)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Tablet layout: photo panel on the left, bento grid on the right
// ------------------------------------------------------------------------------------------

@Composable
private fun HomeTabletLayout(
    greetingName: String,
    nextBooking: NextBookingInfo?,
    upcomingCount: Int,
    onSportBookingClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onServiceClick: (ClubService) -> Unit,
    onUpdateClick: () -> Unit,
    onLogOutClick: () -> Unit
) {
    val today = remember { LocalDate.now(AppConstants.CLUB_ZONE_ID) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Mist)) {
        val panelWidth: Dp = (maxWidth * 0.39f).coerceIn(360.dp, 520.dp)
        Row(Modifier.fillMaxSize()) {
            PhotoHero(
                imageRes = R.drawable.main_hero_background,
                shape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp),
                imageAlignment = BiasAlignment(0.4f, 0f),
                modifier = Modifier.width(panelWidth).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 40.dp, end = 36.dp, top = 28.dp, bottom = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KayanCrest(size = 48.dp)
                        Text("Kayan Club", style = MaterialTheme.typography.titleSmall, fontSize = 18.sp, color = Color.White)
                    }
                    Spacer(Modifier.weight(0.6f))
                    Text(
                        "Welcome",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        lineHeight = 46.sp,
                        color = Color.White
                    )
                    GreetingName(greetingName, maxSize = 80.sp, minSize = 36.sp)
                    Spacer(Modifier.weight(1f))
                    if (nextBooking != null) {
                        NextBookingGlassCard(nextBooking, onView = onMyBookingsClick)
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onUpdateClick) {
                            Text("Update app", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        TextButton(onClick = onLogOutClick) {
                            Text("Log out", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        today.format(KayanFormat.dateLong),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    HomeMenuButton(onUpdateClick = onUpdateClick, onLogOutClick = onLogOutClick, onPhoto = false)
                }
                TabletBento(
                    nextBooking = nextBooking,
                    upcomingCount = upcomingCount,
                    onSportBookingClick = onSportBookingClick,
                    onMyBookingsClick = onMyBookingsClick,
                    onServiceClick = onServiceClick
                )
            }
        }
    }
}

/** 4 × 3 bento: Sport Booking (2×2) · My Bookings (2×1) · Gym · Swimming / four services. */
@Composable
private fun ColumnScope.TabletBento(
    nextBooking: NextBookingInfo?,
    upcomingCount: Int,
    onSportBookingClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onServiceClick: (ClubService) -> Unit
) {
    val gap = 16.dp
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            SportBookingTile(onClick = onSportBookingClick, large = true, modifier = Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                MyBookingsTile(
                    subtitle = when {
                        upcomingCount == 0 -> "No upcoming bookings"
                        nextBooking != null -> "$upcomingCount upcoming · next ${nextBooking.dayLabel.lowercaseFirstIfRelative()}, ${nextBooking.timeLabel}"
                        else -> "$upcomingCount upcoming"
                    },
                    onClick = onMyBookingsClick,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ServiceTile(ClubService.Gym, { onServiceClick(ClubService.Gym) }, compact = false, modifier = Modifier.weight(1f).fillMaxHeight())
                    ServiceTile(ClubService.Swimming, { onServiceClick(ClubService.Swimming) }, compact = false, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            listOf(ClubService.Tennis, ClubService.BeautyCenter, ClubService.Vr, ClubService.Aerobics).forEach { service ->
                ServiceTile(service, { onServiceClick(service) }, compact = false, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

private fun String.lowercaseFirstIfRelative(): String =
    if (this == "Today" || this == "Tomorrow") lowercase() else this

// ------------------------------------------------------------------------------------------
// Pieces
// ------------------------------------------------------------------------------------------

@Composable
private fun GreetingName(text: String, maxSize: androidx.compose.ui.unit.TextUnit, minSize: androidx.compose.ui.unit.TextUnit) {
    BasicText(
        text = text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize),
        style = TextStyle(
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.ExtraBold,
            color = KayanLime,
            letterSpacing = (-1).sp
        )
    )
}

@Composable
private fun HomeMenuButton(onUpdateClick: () -> Unit, onLogOutClick: () -> Unit, onPhoto: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (onPhoto) {
            IconButton(onClick = { expanded = true }) {
                Icon(KayanIcons.Menu, contentDescription = "Menu", tint = Color.White)
            }
        } else {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(KayanIcons.Menu, contentDescription = "Menu", tint = Ink)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Update app") },
                leadingIcon = { Icon(KayanIcons.Update, contentDescription = null, tint = KayanGreen) },
                onClick = { expanded = false; onUpdateClick() }
            )
            DropdownMenuItem(
                text = { Text("Log out") },
                leadingIcon = { Icon(KayanIcons.Logout, contentDescription = null, tint = InkMuted) },
                onClick = { expanded = false; onLogOutClick() }
            )
        }
    }
}

@Composable
private fun SportBookingTile(onClick: () -> Unit, large: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (large) 28.dp else 24.dp)
    val scrim = remember {
        Brush.verticalGradient(listOf(PhotoScrim.copy(alpha = 0.08f), PhotoScrim.copy(alpha = 0.8f)))
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = Ink,
        shadowElevation = 8.dp,
        modifier = modifier.border(2.dp, KayanLime, shape)
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.sport_hero_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, 0.2f),
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(scrim))
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = if (large) 24.dp else 18.dp, vertical = if (large) 22.dp else 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Sport Booking",
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (large) 38.sp else 26.sp,
                        lineHeight = if (large) 40.sp else 28.sp,
                        color = Color.White
                    )
                    Text(
                        "Padel · Tennis · Football · Basketball",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (large) 15.sp else 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(if (large) 56.dp else 48.dp)
                        .background(KayanLime, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(KayanIcons.Forward, contentDescription = null, tint = OnKayanLime)
                }
            }
        }
    }
}

/** Phone: full-width row. */
@Composable
private fun MyBookingsRow(nextBooking: NextBookingInfo?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(KayanIcons.Bookings, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("My Bookings", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    nextBooking?.let { "Next: ${it.dayLabel.lowercaseFirstIfRelative()}, ${it.timeLabel}" } ?: "No upcoming bookings",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(KayanIcons.ChevronRight, contentDescription = null, tint = InkMuted)
        }
    }
}

/** Tablet: 2×1 bento tile. */
@Composable
private fun MyBookingsTile(subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = Color.White, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(KayanIcons.Bookings, size = 44.dp)
                Spacer(Modifier.weight(1f))
                Icon(KayanIcons.ChevronRight, contentDescription = null, tint = InkMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("My Bookings", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, fontSize = 14.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ServiceTile(
    service: ClubService,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
        color = Color.White,
        modifier = modifier
    ) {
        if (compact) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                Icon(service.icon, contentDescription = null, tint = KayanGreen, modifier = Modifier.size(22.dp))
                Text(service.shortLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            }
        } else {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(service.icon, contentDescription = null, tint = KayanGreen, modifier = Modifier.size(28.dp))
                Text(service.label, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, color = Ink, maxLines = 2)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, size: Dp) {
    Box(
        modifier = Modifier.size(size).background(Mist, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = KayanGreen, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun NextBookingGlassCard(info: NextBookingInfo, onView: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .background(PhotoScrim.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("NEXT BOOKING", style = MaterialTheme.typography.labelSmall, fontSize = 12.sp, letterSpacing = 1.sp, color = Color.White)
            Text(info.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${info.dayLabel} · ${info.timeLabel}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color.White)
        }
        LimeButton(label = "View", onClick = onView)
    }
}

// ------------------------------------------------------------------------------------------
// Previews
// ------------------------------------------------------------------------------------------

private val previewBooking = NextBookingInfo("Padel · Court 1", "Today", "8:00 – 9:00 PM")

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePhonePreview() {
    VillaSportManagerTheme {
        HomeScreen(
            displayName = null,
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(390.dp, 844.dp)),
            nextBooking = previewBooking,
            upcomingCount = 2,
            onSportBookingClick = {}, onMyBookingsClick = {}, onServiceClick = {},
            onUpdateClick = {}, onLogOutClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun HomeTabletPreview() {
    VillaSportManagerTheme {
        HomeScreen(
            displayName = null,
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 800.dp)),
            nextBooking = previewBooking,
            upcomingCount = 2,
            onSportBookingClick = {}, onMyBookingsClick = {}, onServiceClick = {},
            onUpdateClick = {}, onLogOutClick = {}
        )
    }
}
