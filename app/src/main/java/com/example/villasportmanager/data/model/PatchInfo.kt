package com.example.villasportmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `app_versions`. `sha256` / `size_bytes` are optional (added by
 * supabase/04_update_integrity.sql); when present the download is verified against them.
 */
@Serializable
data class VersionInfo(
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("full_apk_path")
    val apkPath: String,
    @SerialName("version_name")
    val versionName: String? = null,
    /** Lower-case hex SHA-256 of the full APK. */
    @SerialName("sha256")
    val sha256: String? = null,
    @SerialName("size_bytes")
    val sizeBytes: Long? = null
)

/**
 * One row of `app_patches`. A bsdiff patch is only valid for the exact bytes it was built from, so
 * `base_sha256` (installed APK) and `target_sha256` (APK the patch produces) let the kiosk refuse a
 * patch that does not match what it has installed instead of producing a corrupt APK.
 */
@Serializable
data class PatchInfo(
    val id: Long? = null,
    @SerialName("from_version_code")
    val fromVersionCode: Int,
    @SerialName("to_version_code")
    val toVersionCode: Int,
    @SerialName("patch_path")
    val patchPath: String,
    /** SHA-256 of the patch file itself. */
    @SerialName("sha256")
    val sha256: String? = null,
    /** SHA-256 of the APK the patch was generated against. */
    @SerialName("base_sha256")
    val baseSha256: String? = null,
    /** SHA-256 of the APK the patch produces (falls back to the version's sha256). */
    @SerialName("target_sha256")
    val targetSha256: String? = null
)
