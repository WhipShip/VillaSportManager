package com.example.villasportmanager.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.villasportmanager.data.repository.BookingRepository
import com.example.villasportmanager.di.SupabaseModule
import com.example.villasportmanager.di.getDisplayName
import com.example.villasportmanager.ui.components.KayanFormat
import com.example.villasportmanager.ui.features.auth.SignInScreen
import com.example.villasportmanager.ui.features.booking.BookingScreen
import com.example.villasportmanager.ui.features.booking.BookingSuccessScreen
import com.example.villasportmanager.ui.features.booking.BookingViewModel
import com.example.villasportmanager.ui.features.booking.BookingViewModelFactory
import com.example.villasportmanager.ui.features.booking.CourtScreen
import com.example.villasportmanager.ui.features.booking.CourtViewModel
import com.example.villasportmanager.ui.features.booking.CourtViewModelFactory
import com.example.villasportmanager.ui.features.booking.MyBookingsScreen
import com.example.villasportmanager.ui.features.booking.MyBookingsViewModel
import com.example.villasportmanager.ui.features.booking.MyBookingsViewModelFactory
import com.example.villasportmanager.ui.features.booking.SportSelectionScreen
import com.example.villasportmanager.ui.features.home.HomeScreen
import com.example.villasportmanager.ui.features.home.summarizeUpcoming
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.Mist
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    windowSizeClass: WindowSizeClass,
    onUpdateClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Observe the current session state from Supabase
    val sessionStatus by SupabaseModule.client.auth.sessionStatus.collectAsState()
    // Sports + courts cache, shared by several screens
    val sports by BookingRepository.sports.collectAsState()

    // Initialize global realtime sync and preload all data once the user is authenticated
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            BookingRepository.initializeGlobalRealtime(SupabaseModule.client, scope)
            BookingRepository.preloadData(SupabaseModule.client, scope)
        }
    }

    // Show a loading screen while Supabase checks local storage
    if (sessionStatus is SessionStatus.Initializing) {
        Box(modifier = Modifier.fillMaxSize().background(Mist), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KayanGreen)
        }
        return
    }

    val startDestination = if (sessionStatus is SessionStatus.Authenticated) "home" else "signin"
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // --- SIGN IN ---
        composable("signin") {
            SignInScreen(
                windowSizeClass = windowSizeClass,
                onSignInSuccess = {
                    navController.navigate("home") {
                        // Clear the backstack so pressing 'back' doesn't reopen the login page
                        popUpTo("signin") { inclusive = true }
                    }
                }
            )
        }

        // --- HOME ---
        composable("home") {
            val displayName = remember { SupabaseModule.client.getDisplayName() }
            val bookingsViewModel: MyBookingsViewModel = viewModel(factory = MyBookingsViewModelFactory())
            val (nextBooking, upcomingCount) = remember(bookingsViewModel.bookings, sports) {
                summarizeUpcoming(bookingsViewModel.bookings, sports)
            }

            HomeScreen(
                displayName = displayName,
                windowSizeClass = windowSizeClass,
                nextBooking = nextBooking,
                upcomingCount = upcomingCount,
                onSportBookingClick = { navController.navigate("sport_selection") },
                onMyBookingsClick = { navController.navigate("my_bookings") },
                onServiceClick = { service ->
                    // Only services that are also sports can be booked in the app today
                    service.sportName?.let { navController.navigate("booking/${Uri.encode(it)}") }
                },
                onUpdateClick = onUpdateClick,
                onLogOutClick = {
                    scope.launch { SupabaseModule.client.auth.signOut() }
                }
            )
        }

        // --- SPORT SELECTION ---
        composable("sport_selection") {
            SportSelectionScreen(
                windowSizeClass = windowSizeClass,
                sportNames = sports.map { it.name },
                onSportSelected = { sportName -> navController.navigate("booking/${Uri.encode(sportName)}") },
                onBackClick = { navController.popBackStack() },
                onHomeClick = { navController.popToHome() }
            )
        }

        // --- MY BOOKINGS ---
        composable("my_bookings") {
            val viewModel: MyBookingsViewModel = viewModel(factory = MyBookingsViewModelFactory())
            MyBookingsScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                sports = sports,
                onBackClick = { navController.popBackStack() }
            )
        }

        // --- BOOKING ---
        composable(
            route = "booking/{sportName}",
            arguments = listOf(navArgument("sportName") { type = NavType.StringType })
        ) { backStackEntry ->
            val sportName = backStackEntry.arguments?.getString("sportName") ?: ""
            val viewModel: BookingViewModel = viewModel(factory = BookingViewModelFactory())

            LaunchedEffect(sportName) {
                if (viewModel.currentSportName == null) viewModel.filterBySport(sportName)
            }

            BookingScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                onBackClick = { navController.popBackStack() },
                onBookingConfirmed = { booking ->
                    val args = listOf(
                        booking.bookingId,
                        booking.sportName,
                        booking.courtName,
                        booking.date.format(KayanFormat.dateLong),
                        KayanFormat.range(booking.startTime, booking.endTime)
                    ).joinToString("/") { Uri.encode(it) }
                    navController.navigate("booking_success/$args")
                }
            )
        }

        // --- BOOKING SUCCESS ---
        composable(
            route = "booking_success/{bookingId}/{sportName}/{courtName}/{date}/{time}",
            arguments = listOf(
                navArgument("bookingId") { type = NavType.StringType },
                navArgument("sportName") { type = NavType.StringType },
                navArgument("courtName") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
                navArgument("time") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            // The success screen owns its own ViewModel; it cancels by the booking id passed in the route.
            val viewModel: BookingViewModel = viewModel(factory = BookingViewModelFactory())

            BookingSuccessScreen(
                bookingId = args?.getString("bookingId") ?: "",
                sportName = args?.getString("sportName") ?: "",
                courtName = args?.getString("courtName") ?: "",
                dateLabel = args?.getString("date") ?: "",
                timeLabel = args?.getString("time") ?: "",
                windowSizeClass = windowSizeClass,
                viewModel = viewModel,
                onDoneClick = {
                    // Back to the sport list if we came through it, otherwise home
                    if (!navController.popBackStack("sport_selection", inclusive = false)) {
                        navController.popToHome()
                    }
                }
            )
        }

        // --- COURT (single-court schedule, kept for direct links) ---
        composable(
            route = "court/{courtId}/{courtName}/{openTime}/{closeTime}/{duration}",
            arguments = listOf(
                navArgument("courtId") { type = NavType.StringType },
                navArgument("courtName") { type = NavType.StringType },
                navArgument("openTime") { type = NavType.StringType },
                navArgument("closeTime") { type = NavType.StringType },
                navArgument("duration") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val courtId = backStackEntry.arguments?.getString("courtId") ?: ""
            val courtName = backStackEntry.arguments?.getString("courtName") ?: ""
            val openTime = backStackEntry.arguments?.getString("openTime") ?: "08:00:00"
            val closeTime = backStackEntry.arguments?.getString("closeTime") ?: "22:00:00"
            val duration = backStackEntry.arguments?.getInt("duration") ?: 60

            val viewModel: CourtViewModel = viewModel(factory = CourtViewModelFactory())
            LaunchedEffect(courtId) {
                viewModel.loadSchedule(courtId, openTime, closeTime, duration)
            }

            CourtScreen(
                courtName = courtName,
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.popToHome() {
    navigate("home") {
        popUpTo("home") { inclusive = true }
    }
}
