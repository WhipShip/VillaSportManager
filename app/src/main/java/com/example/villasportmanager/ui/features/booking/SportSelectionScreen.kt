package com.example.villasportmanager.ui.features.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.villasportmanager.R
import com.example.villasportmanager.ui.components.GlassIconButton
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.PhotoCard
import com.example.villasportmanager.ui.components.PhotoHero
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.components.sportImageRes
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist
import com.example.villasportmanager.ui.theme.VillaSportManagerTheme

/** Shown until the club's sports have been loaded from the server. */
val DefaultSportNames = listOf("Padel", "Basketball", "Football", "Tennis")

@Composable
fun SportSelectionScreen(
    windowSizeClass: WindowSizeClass,
    sportNames: List<String>,
    onSportSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val tier = windowSizeClass.layoutTier
    val names = sportNames.ifEmpty { DefaultSportNames }
    val columns = if (tier == LayoutTier.Expanded) 4 else 2
    val heroHeight = when (tier) {
        LayoutTier.Expanded -> 300.dp
        LayoutTier.Medium -> 280.dp
        LayoutTier.Compact -> 240.dp
    }
    val sidePadding = if (tier == LayoutTier.Compact) 16.dp else 40.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mist)
            .verticalScroll(rememberScrollState())
    ) {
        PhotoHero(
            imageRes = R.drawable.sport_hero_background,
            modifier = Modifier.fillMaxWidth().height(heroHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassIconButton(KayanIcons.Back, "Back", onBackClick)
                GlassIconButton(KayanIcons.Home, "Home", onHomeClick)
            }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sport Booking",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (tier == LayoutTier.Compact) 38.sp else 56.sp,
                    color = KayanLime
                )
            }
        }

        Text(
            "Choose a sport",
            style = MaterialTheme.typography.titleMedium,
            color = InkMuted,
            modifier = Modifier.padding(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 16.dp)
        )

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                names.chunked(columns).forEach { rowNames ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowNames.forEach { name ->
                            PhotoCard(
                                imageRes = sportImageRes(name),
                                label = name,
                                onClick = { onSportSelected(name) },
                                labelSize = if (tier == LayoutTier.Compact) 18 else 24,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(if (columns == 4) 0.9f else 1.25f)
                            )
                        }
                        repeat(columns - rowNames.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SportSelectionPhonePreview() {
    VillaSportManagerTheme {
        SportSelectionScreen(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(390.dp, 844.dp)),
            sportNames = DefaultSportNames,
            onSportSelected = {}, onBackClick = {}, onHomeClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun SportSelectionTabletPreview() {
    VillaSportManagerTheme {
        SportSelectionScreen(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 800.dp)),
            sportNames = DefaultSportNames,
            onSportSelected = {}, onBackClick = {}, onHomeClick = {}
        )
    }
}
