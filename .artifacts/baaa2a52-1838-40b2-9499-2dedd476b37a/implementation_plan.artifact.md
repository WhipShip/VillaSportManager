# Implement "My Bookings" Feature

This plan implements a new feature allowing users to view their active bookings. This feature will be accessible via the third placeholder button on the `HomeScreen`.

## User Review Required

> [!IMPORTANT]
> I am mapping this feature to **Button 3** on the `HomeScreen` (which is labeled "3. Placeholder Action"). This follows the pattern where Button 1 and 2 have already been replaced.

## Proposed Changes

### Data Layer

#### [MODIFY] [Models.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/model/Models.kt)
- Add a new data class `UserBooking` to represent a booking with associated court information.

#### [MODIFY] [BookingRepository.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/repository/BookingRepository.kt)
- Add `getUserBookings(userId: String)` to fetch bookings for a specific user, including court names.

### UI Layer

#### [NEW] [MyBookingsViewModel.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/booking/MyBookingsViewModel.kt)
- Create a ViewModel to manage the state of the "My Bookings" screen.
- Handle fetching data from the repository.

#### [NEW] [MyBookingsScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/booking/MyBookingsScreen.kt)
- Implement the UI for displaying the list of bookings.
- Show court name, start time, and status.

### Navigation

#### [MODIFY] [AppNavigation.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/navigation/AppNavigation.kt)
- Add a new composable route `"my_bookings"`.
- Wire up the `MyBookingsViewModel` and `MyBookingsScreen`.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/home/HomeScreen.kt)
- Add `onMyBookingsClick` callback to the `HomeScreen` composable.
- Replace Button 3 with a "My Bookings" button and update the loop to start from 4.

## Verification Plan

### Automated Tests
- I will verify the build after changes.

### Manual Verification
1. Open the app and log in.
2. On the Home screen, verify that Button 3 now says "3. My Bookings".
3. Click "3. My Bookings".
4. Verify it navigates to the My Bookings screen.
5. If there are bookings, they should be listed.
6. Verify the "Back" button works.
