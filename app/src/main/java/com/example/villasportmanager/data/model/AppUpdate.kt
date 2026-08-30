package com.example.villasportmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdate(
    val id: Long,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("apk_url")
    val apkUrl: String
)
