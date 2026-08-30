package com.example.villasportmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("full_apk_path")
    val apkPath: String,
    @SerialName("version_name")
    val versionName: String? = null
)

@Serializable
data class PatchInfo(
    val id: Long? = null,
    @SerialName("from_version_code")
    val fromVersionCode: Int,
    @SerialName("to_version_code")
    val toVersionCode: Int,
    @SerialName("patch_path")
    val patchPath: String
)
