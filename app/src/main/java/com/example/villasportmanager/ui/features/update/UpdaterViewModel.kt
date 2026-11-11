package com.example.villasportmanager.ui.features.update

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.BuildConfig
import com.example.villasportmanager.data.model.PatchInfo
import com.example.villasportmanager.data.model.VersionInfo
import com.example.villasportmanager.data.repository.BookingRepository
import com.example.villasportmanager.data.repository.UpdateRepository
import com.example.villasportmanager.util.UpdateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Remembers "Later" across launches so a kiosk is not re-prompted on every Activity creation. */
interface UpdateSnooze {
    fun isSnoozed(): Boolean
    fun snooze(hours: Int)

    object None : UpdateSnooze {
        override fun isSnoozed() = false
        override fun snooze(hours: Int) {}
    }

    class SharedPrefs(context: Context) : UpdateSnooze {
        private val prefs = context.applicationContext.getSharedPreferences("kayan_updates", Context.MODE_PRIVATE)
        override fun isSnoozed() = prefs.getLong(KEY, 0L) > System.currentTimeMillis()
        override fun snooze(hours: Int) {
            prefs.edit().putLong(KEY, System.currentTimeMillis() + hours * 3_600_000L).apply()
        }
        private companion object { const val KEY = "snooze_until" }
    }
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val version: VersionInfo, val patch: PatchInfo?) : UpdateState()
    object Downloading : UpdateState()
    object Patching : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdaterViewModel(
    private val repository: UpdateRepository = UpdateRepository(),
    private val snooze: UpdateSnooze = UpdateSnooze.None
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** True while the villa is somewhere other than the home/sign-in screen (set by AppNavigation). */
    private val _userBusy = MutableStateFlow(false)

    /**
     * Whether the updater may put a prompt in front of the user right now: not mid-flow, and no
     * booking write waiting for the server. Downloads continue in the background regardless.
     */
    val canPrompt: StateFlow<Boolean> = combine(_userBusy, BookingRepository.inFlightWrites) { busy, writes ->
        !busy && writes == 0
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var latestApkFile: File? = null
    private var downloadJob: Job? = null

    fun setUserBusy(busy: Boolean) {
        _userBusy.value = busy
    }

    /** "Later": hide the prompt for a few hours; the APK stays cached if already downloaded. */
    fun later() {
        snooze.snooze(SNOOZE_HOURS)
        if (_updateState.value is UpdateState.UpdateAvailable) _updateState.value = UpdateState.Idle
    }

    fun checkForUpdates(isSilent: Boolean = false) {
        val current = _updateState.value
        // MainActivity re-runs the silent check on every Activity creation. Never let a check reset a
        // download or patch that is in progress, or forget an APK that is ready.
        if (current is UpdateState.Downloading || current is UpdateState.Patching || current is UpdateState.ReadyToInstall) {
            Log.d(TAG, "Update already in progress ($current); ignoring check")
            return
        }
        if (isSilent && snooze.isSnoozed()) {
            Log.d(TAG, "Update prompt snoozed; skipping silent check")
            return
        }
        val currentVersion = BuildConfig.VERSION_CODE
        Log.d(TAG, "Checking for updates. Current App Version Code: $currentVersion")
        if (!isSilent) {
            _updateState.value = UpdateState.Checking
        }
        viewModelScope.launch {
            try {
                val latestVersion = repository.getLatestVersion()
                Log.d(TAG, "Latest version in DB: ${latestVersion?.versionCode}")

                if (latestVersion != null && latestVersion.versionCode > currentVersion) {
                    Log.d(TAG, "Update available! Searching for patch from $currentVersion to ${latestVersion.versionCode}")
                    val patch = repository.getPatch(currentVersion, latestVersion.versionCode)
                    _updateState.value = UpdateState.UpdateAvailable(latestVersion, patch)
                } else {
                    Log.d(TAG, "No update available or latestVersion is null")
                    if (!isSilent) {
                        if (latestVersion == null) {
                            _updateState.value = UpdateState.Error("No version information found in database. Check 'app_versions' table.")
                        } else {
                            // Already up to date
                            _updateState.value = UpdateState.Error("Your app is already up to date (Version ${BuildConfig.VERSION_CODE})")
                        }
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                if (!isSilent) {
                    val detail = e.message ?: e.toString()
                    _updateState.value = UpdateState.Error("Update check failed:\n$detail")
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * Patch first (small), full APK second. Every downloaded or generated file is size/hash-verified
     * when the server published those values and written atomically (see UpdateRepository /
     * UpdateUtils). If the patch path fails for ANY reason (bad download, patch built against other
     * bytes, hash mismatch) the full APK is downloaded instead of leaving the kiosk retrying the same
     * broken patch forever.
     */
    fun downloadAndApply(context: Context) {
        val currentState = _updateState.value
        if (currentState !is UpdateState.UpdateAvailable) return
        if (downloadJob?.isActive == true) return // second tap while the first download runs

        _updateState.value = UpdateState.Downloading
        downloadJob = viewModelScope.launch {
            val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val patchFile = File(cacheDir, "update.patch")
            val finalApkFile = File(cacheDir, "update.apk")
            latestApkFile = null
            finalApkFile.delete()
            val version = currentState.version
            val patch = currentState.patch
            try {
                var produced = false
                if (patch != null) {
                    try {
                        repository.downloadFile(patch.patchPath, patchFile, expectedSha256 = patch.sha256)
                        _updateState.value = UpdateState.Patching
                        UpdateUtils.applyPatch(
                            context, patchFile, finalApkFile,
                            baseSha256 = patch.baseSha256,
                            targetSha256 = patch.targetSha256 ?: version.sha256
                        )
                        produced = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Patch path failed (${e.message}); falling back to the full APK", e)
                        finalApkFile.delete()
                    } finally {
                        patchFile.delete()
                    }
                }
                if (!produced) {
                    _updateState.value = UpdateState.Downloading
                    repository.downloadFile(version.apkPath, finalApkFile, expectedSha256 = version.sha256, expectedSize = version.sizeBytes)
                }

                if (finalApkFile.exists() && finalApkFile.length() > 1024) {
                    latestApkFile = finalApkFile
                    _updateState.value = UpdateState.ReadyToInstall
                } else {
                    finalApkFile.delete()
                    _updateState.value = UpdateState.Error("Downloaded file is empty or too small.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/applying update", e)
                finalApkFile.delete()
                _updateState.value = UpdateState.Error("Update failed: ${e.message}")
            }
        }
    }

    fun install(context: Context) {
        // The package installer kills this process. A booking INSERT/UPDATE that is still waiting
        // for its response would be committed server-side but never acknowledged on the kiosk.
        if (BookingRepository.inFlightWrites.value > 0) {
            Log.w(TAG, "Install requested while a booking write is in flight; ignoring")
            return
        }
        latestApkFile?.takeIf { it.exists() }?.let {
            UpdateUtils.installApk(context, it)
        } ?: run {
            latestApkFile = null
            _updateState.value = UpdateState.Error("APK file not found. Please download the update again.")
        }
    }

    fun dismiss() {
        // Keep the state while a download/patch is running; the dialog hides itself for those states.
        if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.Patching) return
        _updateState.value = UpdateState.Idle
    }

    companion object {
        private const val TAG = "UpdaterViewModel"
        private const val SNOOZE_HOURS = 4

        /** Factory for MainActivity: persists "Later" in SharedPreferences. */
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UpdaterViewModel(snooze = UpdateSnooze.SharedPrefs(context)) as T
        }
    }
}
