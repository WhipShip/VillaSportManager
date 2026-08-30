# Walkthrough - Delta Update System

The update system has been upgraded to support delta (patch) updates. This significantly reduces the download size for users when moving between versions.

## Key Changes

### 1. Supabase Integration
- Added `Storage` plugin to `SupabaseModule`.
- Updated data models to include `VersionInfo` and `PatchInfo`.
- Created `UpdateRepository` to query latest versions and patches from Supabase tables `app_versions` and `app_patches`.

### 2. Delta Patching Logic
- Integrated `jbsdiff` library to apply binary patches.
- Implemented `UpdateUtils.applyPatch` which:
    - Reads the currently installed APK from the system.
    - Downloads the patch from Supabase.
    - Applies the patch to generate a new APK in the app's cache directory.

### 3. Modernized Update UI
- Replaced the old `UpdateManager` with a more robust `UpdaterViewModel` and `UpdaterDialog`.
- The new UI provides clear feedback during checking, downloading, patching, and installation phases.
- Automatically handles fallback to full APK download if no patch is available for the current version.

## Verification Results

### Build Verification
- [x] Project compiles successfully with new dependencies and logic.
- [x] `UpdateRepository` correctly queries Supabase using property-based filtering.
- [x] `MainActivity` integrated with the new `UpdaterDialog`.

### Manual Testing Steps (Recommended)
1.  **Full Update**: Upload a new version to `app_versions` but do not create a patch in `app_patches`. Verify the app downloads the full APK.
2.  **Delta Update**: Create a patch in `app_patches` from the current version to the new version. Verify the app downloads the patch and applies it successfully.
3.  **Installation**: Verify that after downloading/patching, the installation prompt appears and correctly points to the new APK.
