# Fix App Update Check Logic

The user reports that the app at version 7 does not prompt for an update even though version 8 and a patch are available in Supabase. This plan aims to diagnose and fix the update check mechanism.

## User Review Required

> [!IMPORTANT]
> Please confirm the exact names of your Supabase tables. The current code uses `app_versions` and `app_patches`. If your tables are named `app_verison` (with a typo) or `app_version` (singular), we need to update the code accordingly.

## Proposed Changes

### Data Layer

#### [MODIFY] [UpdateRepository.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/repository/UpdateRepository.kt)
- Add comprehensive logging to catch and report Supabase errors.
- Use `maybeSingle()` in the `select` queries to correctly handle single-object responses from Postgrest.
- Ensure the table names match what is in Supabase. (Currently assuming `app_versions` and `app_patches`).

### UI Layer

#### [MODIFY] [UpdaterViewModel.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/features/update/UpdaterViewModel.kt)
- Add logging to track the update check flow (current version vs. latest version found).
- Ensure errors are properly reported even when `isSilent` is true, at least in the logs.

## Verification Plan

### Automated Tests
- None possible without a live Supabase connection, but we will verify the code compiles.

### Manual Verification
1.  Deploy the app with logging.
2.  Check Logcat for "UpdaterViewModel" and "UpdateRepository" tags.
3.  Verify if the latest version is being fetched correctly.
4.  Verify if there are any Supabase "relation does not exist" errors (indicating wrong table names).
