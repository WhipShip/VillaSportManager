# Wire BookingScreen into Navigation

The goal is to make the `BookingScreen` accessible from the `HomeScreen` by wiring it into the `AppNavigation` graph. This will allow the user to test the booking feature and see the list of courts.

## User Review Required

> [!NOTE]
> I am replacing the second placeholder button on the `HomeScreen` with a "Book a Court" button.

## Proposed Changes

### UI Features

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/home/HomeScreen.kt)
- Add `onBookingButtonClick` lambda parameter.
- Replace the second placeholder button with a "Book a Court" button.
- Adjust the placeholder loop to start from 3.

#### [MODIFY] [AppNavigation.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/navigation/AppNavigation.kt)
- Import `BookingScreen`.
- Update `HomeScreen` call to include `onBookingButtonClick`.
- Add `"booking"` destination to the `NavHost`.

## Verification Plan

### Manual Verification
- Deploy the app.
- Log in (if not already logged in).
- Tap "2. Book a Court" on the Home Screen.
- Verify that `BookingScreen` loads and shows "Tennis" (Courts 1, 2) and "Padel" (Court A).
- Verify that tapping a court returns to the Home Screen (as configured in the callback).
