# Delta Update System Implementation Plan

This plan outlines the steps to implement a delta (patch) update system for the Kayan Club app using Supabase for storage and version tracking, and `jbsdiff` for applying patches to the installed APK.

## User Review Required

> [!IMPORTANT]
> The implementation assumes two tables in Supabase: `app_versions` and `app_patches`. If these tables do not exist yet, they will need to be created with the following structures:
> - `app_versions`: `id`, `version_code` (Int), `apk_path` (String)
> - `app_patches`: `id`, `from_version_code` (Int), `to_version_code` (Int), `patch_path` (String)

## Proposed Changes

### Configuration & Infrastructure

#### [MODIFY] [SupabaseModule.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/di/SupabaseModule.kt)
- Install `Storage` plugin in the Supabase client.

### Data Layer

#### [NEW] [PatchInfo.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/model/PatchInfo.kt)
- Define `PatchInfo` and `VersionInfo` data classes for update tracking.

#### [NEW] [UpdateRepository.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/data/repository/UpdateRepository.kt)
- Implement `checkForUpdates(currentVersion: Int)`:
    - Fetches the latest version from `app_versions`.
    - Checks for a patch in `app_patches` from `currentVersion` to `latestVersion`.
- Implement `downloadFile(path: String, destFile: File)`:
    - Downloads the file from Supabase Storage bucket `updates`.

### Business Logic

#### [NEW] [UpdateUtils.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/util/UpdateUtils.kt)
- Implement `applyPatch(context, patchFile, newApkFile)`:
    - Locates the installed APK.
    - Applies the `jbsdiff` patch to generate a new APK.
- Implement `installApk(context, apkFile)`:
    - Uses `FileProvider` and `Intent.ACTION_VIEW` to trigger installation.

### UI Layer

#### [NEW] [UpdaterViewModel.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/viewmodel/UpdaterViewModel.kt)
- Manage the update state machine: `Idle`, `Checking`, `UpdateAvailable`, `Downloading`, `Patching`, `ReadyToInstall`, `Error`.
- Coordinate the update flow: Check -> Download -> Patch -> Install.

#### [NEW] [UpdaterScreen.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/ui/screens/UpdaterScreen.kt)
- Create a Compose UI to display the update progress and actions to the user.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/whips/Desktop/Work/sport_club_managerV2/VillaSportManager/app/src/main/java/com/example/villasportmanager/MainActivity.kt)
- Integrate the new `UpdaterScreen` or trigger the update flow.
- Remove the old `UpdateManager` usage.

## Verification Plan

### Automated Tests
- Unit tests for `UpdateRepository` (mocking Supabase).
- Unit tests for `UpdaterViewModel` state transitions.

### Manual Verification
1.  Deploy a version of the app.
2.  Upload a patch file and a full APK to Supabase.
3.  Trigger the update check.
4.  Verify that the patch is downloaded and applied correctly.
5.  Verify that the installation prompt appears with the correct APK.
