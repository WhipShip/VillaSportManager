package com.example.villasportmanager.ui.features.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.model.BookingRequest
import com.example.villasportmanager.data.model.TimeSlot
import com.example.villasportmanager.data.repository.BookingRepository
import com.example.villasportmanager.di.SupabaseModule
import com.example.villasportmanager.util.AppConstants
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class CourtViewModel(private val repository: BookingRepository) : ViewModel() {

    // --- Core Data & Selection State ---
    var slots by mutableStateOf<List<TimeSlot>>(emptyList())

    // The rolling 7-day window starting today in the club's timezone
    val availableDates: List<LocalDate> = (0..6).map { LocalDate.now(AppConstants.CLUB_ZONE_ID).plusDays(it.toLong()) }

    var selectedDate by mutableStateOf(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    var selectedSlot by mutableStateOf<TimeSlot?>(null)

    // --- Loading & Booking States ---
    var isCheckingDatabase by mutableStateOf(false)
    var isBooking by mutableStateOf(false)
    var bookingError by mutableStateOf<String?>(null)
    var bookingSuccess by mutableStateOf(false)

    // Cache court rules so we can re-generate slots when dates change
    private var currentCourtId: String? = null
    private var currentOpenTime: String? = null
    private var currentCloseTime: String? = null
    private var currentDuration: Int? = null

    fun loadSchedule(
        courtId: String,
        openTimeStr: String,
        closeTimeStr: String,
        durationMinutes: Int,
        date: LocalDate = LocalDate.now(AppConstants.CLUB_ZONE_ID)
    ) {
        currentCourtId = courtId
        currentOpenTime = openTimeStr
        currentCloseTime = closeTimeStr
        currentDuration = durationMinutes
        selectedDate = date
        selectedSlot = null // Clear selection when the schedule reloads

        // 1. SAFETY: Prevent infinite loop if duration is invalid
        if (durationMinutes <= 0) {
            slots = emptyList()
            return
        }

        // 2. INSTANT UI: Generate the blank slots
        try {
            val openTime = LocalTime.parse(openTimeStr)
            val closeTime = LocalTime.parse(closeTimeStr)

            val openDateTime = date.atTime(openTime)
            val closeDateTime = date.atTime(closeTime)

            val generatedSlots = mutableListOf<TimeSlot>()
            var currentDateTime = openDateTime

            // Safety limit: max 100 slots to prevent runaway loops
            var count = 0
            while (count < 100 && !currentDateTime.plusMinutes(durationMinutes.toLong()).isAfter(closeDateTime)) {
                generatedSlots.add(TimeSlot(time = currentDateTime.toLocalTime(), isBooked = false))
                currentDateTime = currentDateTime.plusMinutes(durationMinutes.toLong())
                count++
            }

            slots = generatedSlots
        } catch (e: Exception) {
            slots = emptyList()
            return
        }

        // 3. NETWORK CALL: Fetch bookings and gray out taken slots
        viewModelScope.launch {
            isCheckingDatabase = true

            val result = repository.getBookingsForCourt(courtId, date)

            result.onSuccess { bookings ->
                // Parse UTC times from DB to the Club's Local Timezone
                val bookedTimes = bookings.mapNotNull {
                    try {
                        val zonedDateTime = ZonedDateTime.parse(it.startTime)
                            .withZoneSameInstant(AppConstants.CLUB_ZONE_ID)

                        // FIX: Ensure the booking actually belongs to the selected day before mapping it
                        if (zonedDateTime.toLocalDate() == date) {
                            zonedDateTime.toLocalTime()
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                // Update UI state
                slots = slots.map { slot ->
                    if (bookedTimes.contains(slot.time)) {
                        slot.copy(isBooked = true)
                    } else {
                        slot
                    }
                }
            }
            isCheckingDatabase = false
        }
    }

    fun onDateSelected(date: LocalDate) {
        if (date != selectedDate) {
            currentCourtId?.let { courtId ->
                // Reload the schedule for the new date using cached rules
                loadSchedule(courtId, currentOpenTime!!, currentCloseTime!!, currentDuration!!, date)
            }
        }
    }

    fun confirmBooking() {
        val slot = selectedSlot ?: return
        val courtId = currentCourtId ?: return
        val duration = currentDuration ?: return
        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id ?: run {
            bookingError = "User not logged in"
            return
        }

        viewModelScope.launch {
            isBooking = true
            bookingError = null
            bookingSuccess = false

            // Convert LocalTime + LocalDate to ISO8601 UTC for Supabase
            val zonedDateTime = selectedDate.atTime(slot.time).atZone(AppConstants.CLUB_ZONE_ID)
            val startTimeIso = zonedDateTime.toOffsetDateTime().toString()
            val endTimeIso = zonedDateTime.plusMinutes(duration.toLong()).toOffsetDateTime().toString()

            val request = BookingRequest(
                courtId = courtId,
                userId = userId,
                startTime = startTimeIso,
                endTime = endTimeIso
            )

            val result = repository.createBooking(request)

            result.onSuccess {
                bookingSuccess = true
                selectedSlot = null // Clear the selection panel on success
                loadSchedule(courtId, currentOpenTime!!, currentCloseTime!!, duration, selectedDate)
            }.onFailure {
                bookingError = it.message ?: "Unknown error occurred"
            }

            isBooking = false
        }
    }

    fun getEndTimeForSlot(startTime: LocalTime): LocalTime {
        return currentDuration?.let { startTime.plusMinutes(it.toLong()) } ?: startTime
    }

    fun clearBookingStatus() {
        bookingError = null
        bookingSuccess = false
    }
}

class CourtViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CourtViewModel(BookingRepository(SupabaseModule.client)) as T
    }
}