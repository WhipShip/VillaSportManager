package com.example.villasportmanager.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.LocalTime

@Serializable
data class Court(
    val id: String,
    val name: String,
    @SerialName("sport_id") val sportId: String
)

@Serializable
data class Sport(
    val id: String,
    val name: String,
    @SerialName("slot_duration_minutes") val slotDurationMinutes: Int,
    @SerialName("open_time") val openTime: String, // Comes in as "HH:mm:ss"
    @SerialName("close_time") val closeTime: String, // Comes in as "HH:mm:ss"
    val courts: List<Court> = emptyList()
)
@Serializable
data class Booking(
    val id: String,
    @SerialName("start_time") val startTime: String, // Comes in as ISO8601 e.g. "2026-08-27T10:00:00+00:00"
    @SerialName("end_time") val endTime: String,
    @SerialName("court_id") val courtId: String? = null,
    val status: String? = null
)

@Serializable
data class UserBooking(
    val id: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val status: String,
    val courts: Court? = null
)

@Serializable
data class BookingRequest(
    @SerialName("court_id") val courtId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val status: String = "confirmed"
)

// This is just for our UI state - not for Supabase
data class TimeSlot(
    val time: LocalTime,
    val isBooked: Boolean = false
)