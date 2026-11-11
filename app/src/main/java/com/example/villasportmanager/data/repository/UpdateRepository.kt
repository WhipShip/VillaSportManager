package com.example.villasportmanager.data.repository

import android.net.Uri
import android.util.Log
import com.example.villasportmanager.BuildConfig
import com.example.villasportmanager.data.model.PatchInfo
import com.example.villasportmanager.data.model.VersionInfo
import com.example.villasportmanager.di.SupabaseModule
import com.example.villasportmanager.util.IntegrityException
import com.example.villasportmanager.util.UpdateIntegrity
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdateRepository {

    private val client = SupabaseModule.client

    suspend fun getLatestVersion(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching latest version from 'app_versions'...")
            val result = client.from("app_versions")
                .select {
                    limit(1)
                    order("version_code", Order.DESCENDING)
                }
                .decodeList<VersionInfo>()

            val latest = result.firstOrNull()
            Log.d(TAG, "Latest version info: $latest")
            latest
        } catch (e: Exception) {
            Log.e(TAG, "getLatestVersion failed", e)
            throw e
        }
    }

    suspend fun getPatch(fromVersion: Int, toVersion: Int): PatchInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for patch in Supabase: from_version_code=$fromVersion, to_version_code=$toVersion")
            val result = client.from("app_patches")
                .select {
                    filter {
                        // Using raw strings to ensure exact column matching
                        eq("from_version_code", fromVersion)
                        eq("to_version_code", toVersion)
                    }
                }
                .decodeList<PatchInfo>()

            Log.d(TAG, "Supabase returned ${result.size} patches.")
            val patch = result.firstOrNull()
            Log.d(TAG, "Selected patch: $patch")
            patch
        } catch (e: Exception) {
            Log.e(TAG, "getPatch error", e)
            null
        }
    }

    /**
     * Downloads [path] into [destFile] **atomically**.
     *
     * [path] is either an absolute http(s) URL or an object path inside the public `updates`
     * bucket. Bytes stream into `<dest>.part` (never buffered whole in memory); the byte count is
     * checked against `Content-Length`, then against [expectedSize] / [expectedSha256] when the
     * server published them; only then is the file moved over [destFile]. On any failure the
     * `.part` file is deleted and [destFile] is left untouched, so a half-written APK can never be
     * handed to the installer.
     */
    suspend fun downloadFile(
        path: String,
        destFile: File,
        expectedSha256: String? = null,
        expectedSize: Long? = null
    ): Unit = withContext(Dispatchers.IO) {
        val part = File(destFile.parentFile, destFile.name + ".part")
        try {
            val url = if (path.startsWith("http://") || path.startsWith("https://")) URL(path) else URL(publicObjectUrl(path))
            Log.d(TAG, "Downloading $url")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            try {
                connection.connect()
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status while downloading $url")
                val declared = connection.contentLengthLong
                connection.inputStream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                if (declared > 0 && part.length() != declared) {
                    throw IntegrityException("Download truncated: got ${part.length()} of $declared bytes")
                }
            } finally {
                connection.disconnect()
            }
            UpdateIntegrity.verifyFile(part, expectedSha256, expectedSize, "Downloaded file")
            UpdateIntegrity.seal(part, destFile)
            Log.d(TAG, "Download complete and verified. Size: ${destFile.length()} bytes")
        } catch (e: Exception) {
            part.delete()
            Log.e(TAG, "Download failed", e)
            throw e
        }
    }

    /** Public-bucket object URL; the bucket must stay public-read (write is dashboard/service-role only). */
    private fun publicObjectUrl(objectPath: String): String {
        val encoded = objectPath.trimStart('/').split('/').joinToString("/") { Uri.encode(it) }
        return "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET/$encoded"
    }

    private companion object {
        const val TAG = "UpdateRepository"
        const val BUCKET = "updates"
    }
}
