package com.example.villasportmanager.data.repository

import android.util.Log
import com.example.villasportmanager.data.model.PatchInfo
import com.example.villasportmanager.data.model.VersionInfo
import com.example.villasportmanager.di.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class UpdateRepository {

    private val client = SupabaseModule.client

    suspend fun getLatestVersion(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d("UpdateRepository", "Fetching latest version from 'app_versions'...")
            val result = client.from("app_versions")
                .select {
                    limit(1)
                    order("version_code", Order.DESCENDING)
                }
                .decodeList<VersionInfo>()
            
            val latest = result.firstOrNull()
            Log.d("UpdateRepository", "Latest version info: $latest")
            latest
        } catch (e: Exception) {
            Log.e("UpdateRepository", "getLatestVersion failed", e)
            throw e 
        }
    }

    suspend fun getPatch(fromVersion: Int, toVersion: Int): PatchInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d("UpdateRepository", "Searching for patch in Supabase: from_version_code=$fromVersion, to_version_code=$toVersion")
            val result = client.from("app_patches")
                .select {
                    filter {
                        // Using raw strings to ensure exact column matching
                        eq("from_version_code", fromVersion)
                        eq("to_version_code", toVersion)
                    }
                }
                .decodeList<PatchInfo>()
            
            Log.d("UpdateRepository", "Supabase returned ${result.size} patches.")
            val patch = result.firstOrNull()
            Log.d("UpdateRepository", "Selected patch: $patch")
            patch
        } catch (e: Exception) {
            Log.e("UpdateRepository", "getPatch error", e)
            null
        }
    }

    suspend fun downloadFile(path: String, destFile: File): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d("UpdateRepository", "Downloading file from: $path")
            if (path.startsWith("http")) {
                val url = URL(path)
                val connection = url.openConnection()
                connection.connect()
                
                val inputStream = connection.getInputStream()
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } else {
                val bytes = client.storage.from("updates").downloadPublic(path)
                destFile.writeBytes(bytes)
            }
            Log.d("UpdateRepository", "Download complete. Size: ${destFile.length()} bytes")
        } catch (e: Exception) {
            Log.e("UpdateRepository", "Download failed", e)
            throw e
        }
    }
}
