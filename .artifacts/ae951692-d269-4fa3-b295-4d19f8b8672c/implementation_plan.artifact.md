# Fix Cold-Start Race Condition in Booking Screen

On a fresh install, the booking screen shows empty slot timings because the initial data fetch (sports/courts) fails or finishes prematurely before the user is authenticated. Since the sync is triggered only once on app startup, it doesn't retry after login, leaving the cache empty until the next app launch.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [BookingRepository.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/repository/BookingRepository.kt)
- Add a `Mutex` to `refreshAllData` to prevent redundant parallel sync operations.
- Add an `isInitialized` `StateFlow` to track if at least one successful sports fetch has completed.
- Move the `lastPreloadDate` update into the successful path of `refreshAllData`.
- Ensure `refreshAllData` returns success status or sets `isInitialized` correctly.

### [UI Layer]

#### [MODIFY] [AppNavigation.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/navigation/AppNavigation.kt)
- Update the `LaunchedEffect` that triggers `preloadData` and `initializeGlobalRealtime` to use `sessionStatus` as a key instead of `Unit`.
- Only trigger the sync when `sessionStatus is SessionStatus.Authenticated`. This ensures that on a fresh install, the sync starts as soon as the user logs in, rather than failing silently before they even see the sign-in screen.

#### [MODIFY] [BookingViewModel.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/booking/BookingViewModel.kt)
- Update the `isLoading` logic in the `combine` block. It should remain `true` until `sports` is not empty, even if `isSyncing` is currently `false` (e.g., between retries or if the first sync failed).

#### [MODIFY] [CourtViewModel.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/booking/CourtViewModel.kt)
- Similarly update `isCheckingDatabase` to stay `true` until `slots` is populated.

## Verification Plan

### Manual Verification
1. **Fresh Install Scenario**:
   - Clear app data/cache or perform a fresh install.
   - Launch the app and sign in.
   - Navigate to the Booking screen immediately after sign-in.
   - **Expected**: The screen should show a loading indicator until the sports and courts are fetched, then display the slot timings.
2. **Subsequent Launch Scenario**:
   - Close the app (process kill) and relaunch.
   - **Expected**: The app should still show data correctly, preloading it as soon as the authenticated session is restored.
3. **Offline Scenario**:
   - Launch the app without internet.
   - **Expected**: It should show a loading indicator or handle the error gracefully without indefinitely showing an empty screen that looks "ready" but has no data.
