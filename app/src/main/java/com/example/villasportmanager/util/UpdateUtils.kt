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

    /**
     * Applies a bsdiff [patchFile] to the installed APK and writes the result to [newApkFile].
     *
     * A bsdiff patch is only meaningful for the exact bytes it was generated against. When the
     * server published [baseSha256] the installed APK is checked first (a mismatch means "this
     * patch is not for this build": wrong signing, rebuilt with the same versionCode, split APK).
     * When [targetSha256] is published the output is checked too. The result is written to a
     * `.part` file and sealed atomically, so a failed patch never leaves a bogus `update.apk`.
     */
    suspend fun applyPatch(
        context: Context,
        patchFile: File,
        newApkFile: File,
        baseSha256: String? = null,
        targetSha256: String? = null
    ) = withContext(Dispatchers.IO) {
        // 1. Get the path of the currently installed APK
        val currentApkPath = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        val oldApkFile = File(currentApkPath)
        UpdateIntegrity.verifyFile(oldApkFile, baseSha256, null, "Installed APK (patch base)")

        // 2. Apply bspatch into a temporary file
        val part = File(newApkFile.parentFile, newApkFile.name + ".part")
        try {
            val oldBytes = oldApkFile.readBytes()
            val patchBytes = patchFile.readBytes()
            FileOutputStream(part).use { fos ->
                Patch.patch(oldBytes, patchBytes, fos)
            }
            UpdateIntegrity.verifyFile(part, targetSha256, null, "Patched APK")
            UpdateIntegrity.seal(part, newApkFile)
        } catch (e: Exception) {
            part.delete()
            throw e
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
