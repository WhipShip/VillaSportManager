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
    @SerialName("user_id") val userId: String? = null,
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
    val status: String = "confirmed",
    /**
     * Idempotency key (UUID) generated once per tap of "Book it". The DB has a unique index on it
     * (supabase/02_booking_integrity.sql), so if the kiosk dies after the INSERT was sent but before
     * the response arrived (Wi-Fi drop, app update killing the process) the booking can be found
     * again instead of being silently duplicated or lost.
     */
    @SerialName("client_request_id") val clientRequestId: String? = null
)

// This is just for our UI state - not for Supabase
data class TimeSlot(
    val time: LocalTime,
    val isBooked: Boolean = false,
    /** True when the booking occupying this slot belongs to the signed-in villa. */
    val isMine: Boolean = false,
    /** True when the slot has already ended (club time). Villas cannot book it; the DB rejects it too. */
    val isPast: Boolean = false
)