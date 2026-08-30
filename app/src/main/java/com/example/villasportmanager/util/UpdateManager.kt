package com.example.villasportmanager.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.villasportmanager.BuildConfig
import com.example.villasportmanager.data.model.AppUpdate
import com.example.villasportmanager.di.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    data class UpdateAvailable(val update: AppUpdate) : UpdateState()
    object Downloading : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * Manages the in-app update flow using Supabase as a backend.
 */
class UpdateManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId && id != -1L) {
                checkDownloadStatus(id)
            }
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Queries Supabase for the latest version and compares it with the current app version.
     */
    suspend fun checkForUpdates() {
        // If we are already downloading or ready, don't check again
        if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.ReadyToInstall) {
            return
        }

        try {
            val update = withContext(Dispatchers.IO) {
                SupabaseModule.client.from("app_updates")
                    .select {
                        limit(1)
                        order("version_code", Order.DESCENDING)
                    }
                    .decodeSingleOrNull<AppUpdate>()
            }

            update?.let {
                Log.d("UpdateManager", "Latest version from server: ${it.versionCode}, current version: ${BuildConfig.VERSION_CODE}")
                if (it.versionCode > BuildConfig.VERSION_CODE) {
                    
                    // Check if a download is already in progress for this APK
                    val existingId = findExistingDownloadId(it.apkUrl)
                    if (existingId != -1L) {
                        downloadId = existingId
                        checkDownloadStatus(existingId)
                        return
                    }

                    // Check if file is already downloaded and complete
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                    if (file.exists()) {
                        _updateState.value = UpdateState.ReadyToInstall
                    } else {
                        _updateState.value = UpdateState.UpdateAvailable(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking for updates from Supabase", e)
        }
    }

    private fun findExistingDownloadId(apkUrl: String): Long {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            do {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                if (uriIndex != -1) {
                    val uri = cursor.getString(uriIndex)
                    if (uri == apkUrl) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_PENDING || 
                            status == DownloadManager.STATUS_RUNNING || 
                            status == DownloadManager.STATUS_PAUSED ||
                            status == DownloadManager.STATUS_SUCCESSFUL) {
                            val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                            val id = cursor.getLong(idIndex)
                            cursor.close()
                            return id
                        }
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return -1L
    }

    private fun checkDownloadStatus(id: Long) {
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex != -1) {
                val status = cursor.getInt(statusIndex)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> _updateState.value = UpdateState.ReadyToInstall
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> _updateState.value = UpdateState.Downloading
                    DownloadManager.STATUS_FAILED -> {
                        _updateState.value = UpdateState.Idle
                        downloadId = -1L
                    }
                }
            }
        }
        cursor.close()
    }

    /**
     * Downloads the APK from the given URL using Android's DownloadManager.
     */
    fun startDownload(update: AppUpdate) {
        if (_updateState.value is UpdateState.Downloading) return

        _updateState.value = UpdateState.Downloading
        
        // Remove any existing "update.apk" record from DownloadManager to avoid naming conflicts (update-1.apk, etc)
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            do {
                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                if (titleIndex != -1) {
                    val title = cursor.getString(titleIndex)
                    if (title.contains("Kayan Club Update") || title.contains("update.apk")) {
                        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                        downloadManager.remove(cursor.getLong(idIndex))
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Kayan Club Update")
            .setDescription("Downloading new version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // Clear physical file if exists
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (file.exists()) file.delete()

        downloadId = downloadManager.enqueue(request)
    }

    /**
     * Triggers the APK installation intent.
     */
    fun installApk() {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (!file.exists()) {
            _updateState.value = UpdateState.Error("Update file not found")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Installation failed", e)
            _updateState.value = UpdateState.Error("Installation failed")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
    
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
