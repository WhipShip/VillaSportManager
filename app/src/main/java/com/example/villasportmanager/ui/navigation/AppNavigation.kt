package com.example.villasportmanager.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.villasportmanager.di.SupabaseModule
import com.example.villasportmanager.di.getDisplayName
import com.example.villasportmanager.ui.features.auth.SignInScreen
import com.example.villasportmanager.ui.features.booking.BookingScreen
import com.example.villasportmanager.ui.features.booking.BookingViewModel
import com.example.villasportmanager.ui.features.booking.BookingViewModelFactory
import com.example.villasportmanager.ui.features.booking.CourtScreen
import com.example.villasportmanager.ui.features.booking.CourtViewModel
import com.example.villasportmanager.ui.features.booking.CourtViewModelFactory
import com.example.villasportmanager.ui.features.booking.MyBookingsScreen
import com.example.villasportmanager.ui.features.booking.MyBookingsViewModel
import com.example.villasportmanager.ui.features.booking.MyBookingsViewModelFactory
import com.example.villasportmanager.ui.features.booking.SportSelectionScreen
import com.example.villasportmanager.ui.features.booking.BookingSuccessScreen
import com.example.villasportmanager.ui.features.home.HomeScreen
import com.example.villasportmanager.ui.features.home.TimeScreen
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    windowSizeClass: WindowSizeClass,
    onUpdateClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Observe the current session state from Supabase
    val sessionStatus by SupabaseModule.client.auth.sessionStatus.collectAsState()

    // Initialize global realtime sync and PRELOAD all data for background caching
    // We trigger this when the user becomes authenticated to ensure data is fetched
    // correctly after login on a fresh install.
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            com.example.villasportmanager.data.repository.BookingRepository.initializeGlobalRealtime(
                SupabaseModule.client, 
                scope
            )
            com.example.villasportmanager.data.repository.BookingRepository.preloadData(
                SupabaseModule.client,
                scope
            )
        }
    }

    // 2. Show a loading screen while Supabase checks local storage
    if (sessionStatus is SessionStatus.Initializing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 3. Dynamically set the start destination based on the result
    val startDestination = if (sessionStatus is SessionStatus.Authenticated) {
        "home"
    } else {
        "signin"
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // --- SIGN IN SCREEN ---
        composable("signin") {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate("home") {
                        // Clear the backstack so pressing 'back' doesn't reopen the login page
                        popUpTo("signin") { inclusive = true }
                    }
                }
            )
        }

        // --- HOME SCREEN ---
        composable("home") {
            val displayName = remember { SupabaseModule.client.getDisplayName() }

            HomeScreen(
                displayName = displayName,
                windowSizeClass = windowSizeClass,
                onTimeButtonClick = {
                    // Navigates to the time screen
                    navController.navigate("time")
                },
                onBookingButtonClick = {
                    // Navigates to the sport selection screen
                    navController.navigate("sport_selection")
                },
                onMyBookingsClick = {
                    navController.navigate("my_bookings")
                },
                onUpdateClick = onUpdateClick,
                onLogOutClick = {
                    scope.launch {
                        SupabaseModule.client.auth.signOut()
                    }
                }
            )
        }

        // --- SPORT SELECTION SCREEN ---
        composable("sport_selection") {
            SportSelectionScreen(
                windowSizeClass = windowSizeClass,
                onSportSelected = { sportName ->
                    navController.navigate("booking/$sportName")
                },
                onBackClick = { navController.popBackStack() },
                onHomeClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        // --- MY BOOKINGS SCREEN ---
        composable("my_bookings") {
            val viewModel: MyBookingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = MyBookingsViewModelFactory()
            )
            MyBookingsScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                onBackClick = { navController.popBackStack() }
            )
        }

        // --- BOOKING SCREEN ---
        composable(
            route = "booking/{sportName}",
            arguments = listOf(
                androidx.navigation.navArgument("sportName") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val sportName = backStackEntry.arguments?.getString("sportName") ?: ""
            val viewModel: BookingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = BookingViewModelFactory()
            )

            LaunchedEffect(sportName) {
                viewModel.filterBySport(sportName)
            }

            BookingScreen(
                viewModel = viewModel,
                windowSizeClass = windowSizeClass,
                onBackClick = { navController.popBackStack() },
                onBookingSuccess = { courtName, date, time ->
                    val encCourt = android.net.Uri.encode(courtName)
                    val encDate = android.net.Uri.encode(date)
                    val encTime = android.net.Uri.encode(time)
                    navController.navigate("booking_success/$encCourt/$encDate/$encTime")
                }
            )
        }

        composable(
            route = "booking_success/{courtName}/{date}/{time}",
            arguments = listOf(
                androidx.navigation.navArgument("courtName") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("date") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("time") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val courtName = backStackEntry.arguments?.getString("courtName") ?: ""
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val time = backStackEntry.arguments?.getString("time") ?: ""
            
            // We need a ViewModel to handle the cancellation. 
            // We can reuse the BookingViewModel if it's scoped properly, 
            // but for a clean success screen, let's use the same instance or a shared one.
            // For now, we'll get a fresh one or handle it via a new navigation call.
            val viewModel: BookingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = BookingViewModelFactory()
            )
            
            BookingSuccessScreen(
                courtName = courtName,
                date = date,
                time = time,
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack("sport_selection", inclusive = false)
                },
                onCancelClick = {
                    viewModel.cancelLastBooking()
                }
            )
        }

        // --- TIME SCREEN ---
        composable("time") {
            TimeScreen(
                onBackClick = {
                    // Pops the current screen off the stack to go back home
                    navController.popBackStack()
                }
            )
        }

// 2. The Screen that shows the Time Slots
        composable(
            route = "court/{courtId}/{courtName}/{openTime}/{closeTime}/{duration}",
            arguments = listOf(
                androidx.navigation.navArgument("courtId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("courtName") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("openTime") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("closeTime") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("duration") { type = androidx.navigation.NavType.IntType }
            )
        ) { backStackEntry ->

            // Extract the arguments
            val courtId = backStackEntry.arguments?.getString("courtId") ?: ""
            val courtName = backStackEntry.arguments?.getString("courtName") ?: ""
            val openTime = backStackEntry.arguments?.getString("openTime") ?: "08:00:00"
            val closeTime = backStackEntry.arguments?.getString("closeTime") ?: "22:00:00"
            val duration = backStackEntry.arguments?.getInt("duration") ?: 60

            // Get the ViewModel and trigger the load
            val viewModel: CourtViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = CourtViewModelFactory()
            )

            // LaunchedEffect ensures this only runs once when the screen opens
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
