# Add Preview for Sport Booking Page

This plan describes the refactoring of `BookingScreen.kt` to support Jetpack Compose Previews by extracting its UI logic into a stateless `BookingScreenContent` composable.

## Proposed Changes

### [Component Name] Booking UI

#### [MODIFY] [BookingScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/booking/BookingScreen.kt)

- Extract the UI logic of `BookingScreen` into a new `BookingScreenContent` composable.
- `BookingScreenContent` will take state parameters (like `courtStates`, `selectedDate`, etc.) and event callbacks.
- Update `BookingScreen` to use `BookingScreenContent`.
- Add a `@Preview` function `BookingScreenPreview` that provides mock data to `BookingScreenContent`.
- Update `DatePickerSection` and `BookingDetailsContent` to be stateless as well.

## Verification Plan

### Automated Tests
- None planned for this UI-only change.

### Manual Verification
- Verify that the Preview for `BookingScreen` renders correctly in Android Studio.
- Verify that the app still functions correctly by navigating to the booking screen and checking if courts/slots are displayed and clickable.
