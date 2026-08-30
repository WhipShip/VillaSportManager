package com.example.villasportmanager.ui.features.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.ui.theme.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun SportSelectionScreen(
    windowSizeClass: WindowSizeClass,
    onSportSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    
    val sports = listOf(
        SportItem("Padel", R.drawable.padel_background),
        SportItem("Basketball", R.drawable.basketball_background),
        SportItem("Football", R.drawable.football_background),
        SportItem("Tennis", R.drawable.tennis_background)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .verticalScroll(rememberScrollState())
    ) {
        SportHeroSection(
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            isExpanded = isExpanded
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Grid of sports buttons (2 columns)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val rows = sports.chunked(2)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    rowItems.forEach { sport ->
                        Box(modifier = Modifier.weight(1f)) {
                            SportButton(
                                sport = sport,
                                onClick = { onSportSelected(sport.name) }
                            )
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SportHeroSection(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    isExpanded: Boolean
) {
    val height = if (isExpanded) 400.dp else 260.dp
    val heroShape = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height * 0.75f)
        quadraticTo(
            size.width * 0.5f, size.height * 1.05f,
            0f, size.height * 0.85f
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
            painter = painterResource(id = R.drawable.sport_hero_background),
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

        // Dark top overlay for better control visibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        // Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            IconButton(
                onClick = onHomeClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Home, "Home", tint = Color.White)
            }
        }

        Text(
            text = "Sport Booking",
            color = HeroYellow,
            fontSize = if (isExpanded) 56.sp else 38.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

data class SportItem(
    val name: String,
    val imageRes: Int
)

@Composable
fun SportButton(
    sport: SportItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .border(2.dp, HeroYellow, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = sport.imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )

            Text(
                text = sport.name.uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SportSelectionScreenPreview() {
    VillaSportManagerTheme {
        SportSelectionScreen(
            windowSizeClass = androidx.compose.material3.windowsizeclass.WindowSizeClass.calculateFromSize(androidx.compose.ui.unit.DpSize(400.dp, 800.dp)),
            onSportSelected = {},
            onBackClick = {},
            onHomeClick = {}
        )
    }
}
