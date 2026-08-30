package com.example.villasportmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.sigpipe.jbsdiff.Patch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object UpdateUtils {

    suspend fun applyPatch(context: Context, patchFile: File, newApkFile: File) = withContext(Dispatchers.IO) {
        // 1. Get the path of the currently installed APK
        val currentApkPath = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        val oldApkFile = File(currentApkPath)

        // 2. Apply bspatch
        val oldBytes = oldApkFile.readBytes()
        val patchBytes = patchFile.readBytes()
        
        FileOutputStream(newApkFile).use { fos ->
            Patch.patch(oldBytes, patchBytes, fos)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            android.util.Log.e("UpdateUtils", "APK file does not exist: ${apkFile.absolutePath}")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

        context.startActivity(intent)
    }
}
