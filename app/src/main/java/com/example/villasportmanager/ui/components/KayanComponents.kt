package com.example.villasportmanager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.villasportmanager.R
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanGreenDeep
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.OnKayanLime
import com.example.villasportmanager.ui.theme.OnSlotAvailable
import com.example.villasportmanager.ui.theme.OnSlotBooked
import com.example.villasportmanager.ui.theme.SlotAvailableBorder
import com.example.villasportmanager.ui.theme.SlotBooked
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------------------------------
// Layout tiers
// ------------------------------------------------------------------------------------------

/** Compact = phones, Medium = foldables / portrait tablets, Expanded = landscape tablets. */
enum class LayoutTier { Compact, Medium, Expanded }

val WindowSizeClass.layoutTier: LayoutTier
    get() = when (widthSizeClass) {
        WindowWidthSizeClass.Expanded -> LayoutTier.Expanded
        WindowWidthSizeClass.Medium -> LayoutTier.Medium
        else -> LayoutTier.Compact
    }

/** Landscape phones: wide but very short. Tablet split layouts are skipped for these. */
val WindowSizeClass.isShort: Boolean
    get() = heightSizeClass == WindowHeightSizeClass.Compact

// ------------------------------------------------------------------------------------------
// Formatting
// ------------------------------------------------------------------------------------------

object KayanFormat {
    private val locale = Locale.ENGLISH
    val timeWithPeriod: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val timeNoPeriod = DateTimeFormatter.ofPattern("h:mm", locale)
    private val hourOnly = DateTimeFormatter.ofPattern("h a", locale)
    private val period = DateTimeFormatter.ofPattern("a", locale)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", locale)
    val dayNumber: DateTimeFormatter = DateTimeFormatter.ofPattern("d", locale)
    val dateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    val dateLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)

    /** "6 PM" (or "6:30 PM" when not on the hour). */
    fun hourLabel(time: LocalTime): String =
        if (time.minute == 0) time.format(hourOnly) else time.format(timeWithPeriod)

    /** "6:00 – 7:00" — no AM/PM, for tight cells. */
    fun rangeShort(start: LocalTime, end: LocalTime): String =
        "${start.format(timeNoPeriod)} – ${end.format(timeNoPeriod)}"

    /** "6:00 – 7:00 PM", or "11:30 AM – 12:30 PM" when the period changes. */
    fun range(start: LocalTime, end: LocalTime): String =
        if (start.format(period) == end.format(period)) {
            "${start.format(timeNoPeriod)} – ${end.format(timeWithPeriod)}"
        } else {
            "${start.format(timeWithPeriod)} – ${end.format(timeWithPeriod)}"
        }

    /** "Today", "Tomorrow" or "Sat 24 Oct". */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(dateShort)
    }
}

// ------------------------------------------------------------------------------------------
// Sport imagery
// ------------------------------------------------------------------------------------------

@DrawableRes
fun sportImageRes(sportName: String?): Int {
    val name = sportName?.lowercase(Locale.ROOT).orEmpty()
    return when {
        "padel" in name -> R.drawable.padel_background
        "tennis" in name -> R.drawable.tennis_background
        "foot" in name || "soccer" in name -> R.drawable.football_background
        "basket" in name -> R.drawable.basketball_background
        else -> R.drawable.sport_hero_background
    }
}

/** "Padel · Court 1", or just the court name when it already contains the sport. */
fun bookingTitle(sportName: String?, courtName: String?): String {
    val court = courtName.orEmpty().ifBlank { "Court" }
    if (sportName.isNullOrBlank()) return court
    return if (court.contains(sportName, ignoreCase = true)) court else "$sportName · $court"
}

// ------------------------------------------------------------------------------------------
// Hero
// ------------------------------------------------------------------------------------------

/**
 * The brand's curved bottom edge: the right side sits higher than the left.
 * Fractions are of the hero height.
 */
class HeroCurveShape(
    private val rightEdge: Float = 0.7f,
    private val controlX: Float = 0.6f,
    private val controlY: Float = 1.1f,
    private val leftEdge: Float = 0.9f
) : Shape by GenericShape({ size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * rightEdge)
    quadraticTo(size.width * controlX, size.height * controlY, 0f, size.height * leftEdge)
    close()
})

/** Photo with the brand green wash. Content is drawn on top (status-bar insets are up to the caller). */
@Composable
fun PhotoHero(
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier,
    shape: Shape = HeroCurveShape(),
    imageAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val wash = remember {
        Brush.horizontalGradient(
            listOf(KayanGreen.copy(alpha = 0.95f), KayanGreen.copy(alpha = 0.6f), KayanGreen.copy(alpha = 0.15f))
        )
    }
    Box(modifier = modifier.clip(shape)) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = imageAlignment,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(wash))
        content()
    }
}

@Composable
fun KayanCrest(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kayan_crest),
        contentDescription = "Kayan Club crest",
        modifier = modifier.size(size)
    )
}

/** Translucent round button used on top of photos. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------------------------------
// Date strip
// ------------------------------------------------------------------------------------------

/**
 * Seven-day picker. [fillWidth] = true spreads the days evenly (tablets);
 * false makes a horizontally scrolling row of pills (phones).
 */
@Composable
fun DateStrip(
    dates: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (fillWidth) {
        Row(
            modifier = modifier.fillMaxWidth().padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            dates.forEach { date ->
                DatePill(date, date == selected, { onSelect(date) }, horizontal = true, modifier = Modifier.weight(1f).height(64.dp))
            }
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dates) { date ->
                DatePill(date, date == selected, { onSelect(date) }, horizontal = false, modifier = Modifier.width(54.dp).height(62.dp))
            }
        }
    }
}

@Composable
private fun DatePill(
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
    horizontal: Boolean,
    modifier: Modifier
) {
    val container = if (isSelected) Ink else Color.White
    val dayColor = if (isSelected) Color.White else InkMuted
    val numberColor = if (isSelected) Color.White else Ink
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        modifier = modifier.semantics { selected = isSelected }
    ) {
        val day: @Composable () -> Unit = {
            Text(
                date.format(KayanFormat.dayShort).uppercase(Locale.ENGLISH),
                style = MaterialTheme.typography.labelMedium,
                color = dayColor
            )
        }
        val number: @Composable () -> Unit = {
            Text(
                date.format(KayanFormat.dayNumber),
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (horizontal) 24.sp else 20.sp,
                color = numberColor
            )
        }
        if (horizontal) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) { day(); number() }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { day(); number() }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Slot cell
// ------------------------------------------------------------------------------------------

enum class SlotVisual { Available, Booked, Mine, Selected }

/**
 * One bookable cell. Used by the phone timeline (label on the left, check on the right)
 * and the tablet matrix (everything centered).
 */
@Composable
fun SlotCell(
    visual: SlotVisual,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    val (container, content) = when (visual) {
        SlotVisual.Available -> Color.Transparent to OnSlotAvailable
        SlotVisual.Booked -> SlotBooked to OnSlotBooked
        SlotVisual.Mine -> KayanLime to OnKayanLime
        SlotVisual.Selected -> KayanGreen to Color.White
    }
    val clickable = enabled && (visual == SlotVisual.Available || visual == SlotVisual.Selected)
    val dashed = if (visual == SlotVisual.Available) {
        Modifier.drawWithCache {
            val stroke = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
            val radius = CornerRadius(14.dp.toPx())
            onDrawBehind {
                drawRoundRect(color = SlotAvailableBorder, cornerRadius = radius, style = stroke)
            }
        }
    } else Modifier

    Surface(
        onClick = onClick,
        enabled = clickable,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier
            .then(dashed)
            .semantics {
                role = Role.Button
                selected = visual == SlotVisual.Selected
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (visual == SlotVisual.Booked) FontWeight.SemiBold else FontWeight.Bold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = if (centered) Modifier else Modifier.weight(1f, fill = false)
            )
            if (visual == SlotVisual.Selected) {
                Icon(KayanIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Summary bar
// ------------------------------------------------------------------------------------------

/** Dark green bar with the current selection and the primary lime action. */
@Composable
fun SummaryBar(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    loading: Boolean = false,
    wide: Boolean = false
) {
    Surface(
        color = KayanGreenDeep,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = if (wide) 28.dp else 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wide) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LimeButton(
                label = actionLabel,
                onClick = onAction,
                enabled = actionEnabled && !loading,
                loading = loading,
                large = wide
            )
        }
    }
}

@Composable
fun LimeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    large: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KayanLime,
            contentColor = OnKayanLime,
            disabledContainerColor = KayanLime.copy(alpha = 0.45f),
            disabledContentColor = OnKayanLime.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(horizontal = if (large) 36.dp else 24.dp),
        modifier = modifier.height(if (large) 56.dp else 52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = OnKayanLime, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(label, fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold, fontSize = if (large) 19.sp else 17.sp)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Photo card
// ------------------------------------------------------------------------------------------

/** Image card with a bottom scrim and a label, used for sports. */
@Composable
fun PhotoCard(
    @DrawableRes imageRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    labelSize: Int = 20,
    shape: Shape = RoundedCornerShape(22.dp)
) {
    val scrim = remember {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.62f)))
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = Ink,
        modifier = modifier
            .then(if (selected) Modifier.border(3.dp, KayanLime, shape) else Modifier)
            .semantics { this.selected = selected }
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(scrim))
            Text(
                text = label.uppercase(Locale.ENGLISH),
                color = Color.White,
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.ExtraBold,
                fontSize = labelSize.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }
}
