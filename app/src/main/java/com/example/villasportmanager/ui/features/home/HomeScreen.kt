package com.example.villasportmanager.ui.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.ui.theme.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun HomeScreen(
    displayName: String?,
    windowSizeClass: WindowSizeClass,
    onTimeButtonClick: () -> Unit,
    onBookingButtonClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onLogOutClick: () -> Unit
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val heroHeight = if (isExpanded) 400.dp else 300.dp
    val overlapAmount = if (isExpanded) 120.dp else 20.dp // How much the buttons climb onto the hero

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        // 1. Hero in the background
        HeroSection(displayName, isExpanded = isExpanded, height = heroHeight)

        // Header text in top left
        Text(
            text = "SPORTS CENTER",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = if (isExpanded) 150.dp else 24.dp)
        )

        // 2. Main content in the foreground
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Push buttons down, but leave room for overlap
            Spacer(modifier = Modifier.height(heroHeight - overlapAmount))

            ButtonsGrid(
                windowSizeClass = windowSizeClass,
                onTimeButtonClick = onTimeButtonClick,
                onBookingButtonClick = onBookingButtonClick,
                onMyBookingsClick = onMyBookingsClick
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onLogOutClick,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Log Out", color = Color.Gray, fontSize = 12.sp)
            }
        }

        // Hamburger Menu
        IconButton(
            onClick = { /* Handle menu */ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White
            )
        }
    }
}

@Composable
fun HeroSection(displayName: String?, isExpanded: Boolean, height: Dp) {
    val heroShape = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height * 0.75f) // Higher on right
        quadraticTo(
            size.width * 0.2f, size.height,
            0f, size.height * 0.8f // Lower on left
        )
        close()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(heroShape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.main_hero_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(HeroGreenStart, Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isExpanded) 150.dp else 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                fontSize = if (isExpanded) 60.sp else 25.sp,
                text = "Welcome",
                color = Color.White,
                style = if (isExpanded) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                fontSize = if (isExpanded) 90.sp else 40.sp,
                text = if (displayName.isNullOrEmpty()) "Villa 14!" else "$displayName!",
                color = HeroYellow,
                style = if (isExpanded) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun ButtonsGrid(
    windowSizeClass: WindowSizeClass,
    onTimeButtonClick: () -> Unit,
    onBookingButtonClick: () -> Unit,
    onMyBookingsClick: () -> Unit
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    
    val items = listOf(
        "Sport Booking" to onBookingButtonClick,
        "My Bookings" to onMyBookingsClick,
        "View Time" to onTimeButtonClick,
        "Food Court Order" to {},
        "Upcoming Events" to {},
        "Guests" to {}
    )

    if (isExpanded) {
        val hGap = 50.dp // 100px
        val vGap = 60.dp // 120px
        val w720 = 360.dp // 720px
        val w610 = 305.dp // 610px
        val h400 = 200.dp // 400px

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
                HomeButton(text = items[0].first, onClick = items[0].second, isExpanded = true, width = w720, height = h400)
                HomeButton(text = items[3].first, onClick = items[3].second, isExpanded = true, width = w720, height = h400)
            }
            Spacer(modifier = Modifier.width(hGap))
            Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
                HomeButton(text = items[1].first, onClick = items[1].second, isExpanded = true, width = w720, height = h400)
                HomeButton(text = items[4].first, onClick = items[4].second, isExpanded = true, width = w720, height = h400)
            }
            Spacer(modifier = Modifier.width(hGap))
            Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
                HomeButton(text = items[2].first, onClick = items[2].second, isExpanded = true, width = w610, height = h400)
                HomeButton(text = items[5].first, onClick = items[5].second, isExpanded = true, width = w610, height = h400)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false
        ) {
            items(items.size) { index ->
                val item = items[index]
                HomeButton(text = item.first, onClick = item.second, isExpanded = false)
            }
        }
    }
}

@Composable
fun HomeButton(
    text: String,
    onClick: () -> Unit,
    isExpanded: Boolean,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified
) {
    val modifier = if (width != Dp.Unspecified && height != Dp.Unspecified) {
        Modifier.size(width, height)
    } else {
        Modifier.aspectRatio(if (isExpanded) 1.6f else 1.2f)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .border(2.dp, Color.Black, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = ButtonGray
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = if (isExpanded) 22.sp else 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    VillaSportManagerTheme {
        HomeScreen(
            displayName = "John Doe",
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)),
            onTimeButtonClick = {},
            onBookingButtonClick = {},
            onMyBookingsClick = {},
            onLogOutClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun HomeScreenTabletPreview() {
    VillaSportManagerTheme {
        HomeScreen(
            displayName = "John Doe",
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 800.dp)),
            onTimeButtonClick = {},
            onBookingButtonClick = {},
            onMyBookingsClick = {},
            onLogOutClick = {}
        )
    }
}
