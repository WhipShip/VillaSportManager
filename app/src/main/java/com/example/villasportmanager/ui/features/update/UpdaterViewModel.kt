package com.example.villasportmanager.ui.features.update

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.BuildConfig
import com.example.villasportmanager.data.model.PatchInfo
import com.example.villasportmanager.data.model.VersionInfo
import com.example.villasportmanager.data.repository.UpdateRepository
import com.example.villasportmanager.util.UpdateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
    private val repository: UpdateRepository = UpdateRepository()
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var latestApkFile: File? = null

    fun checkForUpdates(isSilent: Boolean = false) {
        val currentVersion = BuildConfig.VERSION_CODE
        Log.d("UpdaterViewModel", "Checking for updates. Current App Version Code: $currentVersion")
        if (!isSilent) {
            _updateState.value = UpdateState.Checking
        }
        viewModelScope.launch {
            try {
                val latestVersion = repository.getLatestVersion()
                Log.d("UpdaterViewModel", "Latest version in DB: ${latestVersion?.versionCode}")
                
                if (latestVersion != null && latestVersion.versionCode > currentVersion) {
                    Log.d("UpdaterViewModel", "Update available! Searching for patch from $currentVersion to ${latestVersion.versionCode}")
                    val patch = repository.getPatch(currentVersion, latestVersion.versionCode)
                    _updateState.value = UpdateState.UpdateAvailable(latestVersion, patch)
                } else {
                    Log.d("UpdaterViewModel", "No update available or latestVersion is null")
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
                Log.e("UpdaterViewModel", "Error checking for updates", e)
                if (!isSilent) {
                    val detail = e.message ?: e.toString()
                    _updateState.value = UpdateState.Error("Update check failed:\n$detail")
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    fun downloadAndApply(context: Context) {
        val currentState = _updateState.value
        if (currentState !is UpdateState.UpdateAvailable) return

        _updateState.value = UpdateState.Downloading
        viewModelScope.launch {
            try {
                val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val downloadedFile = File(cacheDir, "temp_update")
                val finalApkFile = File(cacheDir, "update.apk")
                latestApkFile = finalApkFile

                if (currentState.patch != null) {
                    // Download patch
                    repository.downloadFile(currentState.patch.patchPath, downloadedFile)
                    _updateState.value = UpdateState.Patching
                    // Apply patch
                    UpdateUtils.applyPatch(context, downloadedFile, finalApkFile)
                    downloadedFile.delete()
                } else {
                    // Download full APK
                    repository.downloadFile(currentState.version.apkPath, finalApkFile)
                }

                if (finalApkFile.exists() && finalApkFile.length() > 1024) {
                    _updateState.value = UpdateState.ReadyToInstall
                } else {
                    _updateState.value = UpdateState.Error("Downloaded file is empty or too small.")
                }
            } catch (e: Exception) {
                Log.e("UpdaterViewModel", "Error downloading/applying update", e)
                _updateState.value = UpdateState.Error("Update failed: ${e.message}")
            }
        }
    }

    fun install(context: Context) {
        latestApkFile?.let {
            UpdateUtils.installApk(context, it)
        } ?: run {
            _updateState.value = UpdateState.Error("APK file not found")
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}
