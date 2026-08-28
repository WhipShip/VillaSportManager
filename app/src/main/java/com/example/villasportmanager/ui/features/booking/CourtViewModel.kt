package com.example.villasportmanager.ui.features.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.model.Booking
import com.example.villasportmanager.data.model.BookingRequest
import com.example.villasportmanager.data.model.TimeSlot
import com.example.villasportmanager.data.repository.BookingRepository
import com.example.villasportmanager.di.SupabaseModule
import com.example.villasportmanager.util.AppConstants
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class CourtViewModel(private val repository: BookingRepository) : ViewModel() {

    // --- Core Data & Selection State ---
    var slots by mutableStateOf<List<TimeSlot>>(emptyList())

    // The rolling 7-day window starting today in the club's timezone
    val availableDates: List<LocalDate> = (0..6).map { LocalDate.now(AppConstants.CLUB_ZONE_ID).plusDays(it.toLong()) }

    private val _selectedDateFlow = MutableStateFlow(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    var selectedDate by mutableStateOf(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    
    var selectedSlot by mutableStateOf<TimeSlot?>(null)

    // --- Loading & Booking States ---
    var isCheckingDatabase by mutableStateOf(false)
    var isBooking by mutableStateOf(false)
    var bookingError by mutableStateOf<String?>(null)
    var bookingSuccess by mutableStateOf(false)

    // Cache court rules so we can re-generate slots when dates change
    private val _courtIdFlow = MutableStateFlow<String?>(null)
    private var currentOpenTime: String? = null
    private var currentCloseTime: String? = null
    private var currentDuration: Int? = null

    init {
        // Observe global bookings and reactively update slots
        combine(
            BookingRepository.bookings,
            BookingRepository.isSyncing,
            BookingRepository.isInitialized,
            _selectedDateFlow,
            _courtIdFlow
        ) { args ->
            val bookings = args[0] as Map<String, List<Booking>>
            val isSyncing = args[1] as Boolean
            val isInitialized = args[2] as Boolean
            val date = args[3] as LocalDate
            val courtId = args[4] as String?

            isCheckingDatabase = isSyncing || !isInitialized
            selectedDate = date

            if (courtId == null || currentOpenTime == null || currentCloseTime == null || currentDuration == null) return@combine

            val cacheKey = "${courtId}_$date"
            val courtBookings = bookings[cacheKey] ?: emptyList<Booking>()

            val generatedSlots = generateSlotsInternal(date, currentOpenTime!!, currentCloseTime!!, currentDuration!!)
            val bookedTimes = courtBookings.mapNotNull { booking ->
                try {
                    val zdt = ZonedDateTime.parse(booking.startTime).withZoneSameInstant(AppConstants.CLUB_ZONE_ID)
                    if (zdt.toLocalDate() == date) zdt.toLocalTime() else null
                } catch (e: Exception) { null }
            }

            slots = generatedSlots.map { slot ->
                slot.copy(isBooked = bookedTimes.contains(slot.time))
            }
        }.launchIn(viewModelScope)
    }

    private fun generateSlotsInternal(date: LocalDate, openTimeStr: String, closeTimeStr: String, durationMinutes: Int): List<TimeSlot> {
        return try {
            val openTime = LocalTime.parse(openTimeStr)
            val closeTime = LocalTime.parse(closeTimeStr)

            val openDateTime = date.atTime(openTime)
            val closeDateTime = date.atTime(closeTime)

            val generatedSlots = mutableListOf<TimeSlot>()
            var currentDateTime = openDateTime

            var count = 0
            while (count < 100 && !currentDateTime.plusMinutes(durationMinutes.toLong()).isAfter(closeDateTime)) {
                generatedSlots.add(TimeSlot(time = currentDateTime.toLocalTime(), isBooked = false))
                currentDateTime = currentDateTime.plusMinutes(durationMinutes.toLong())
                count++
            }
            generatedSlots
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadSchedule(
        courtId: String,
        openTimeStr: String,
        closeTimeStr: String,
        durationMinutes: Int,
        date: LocalDate = LocalDate.now(AppConstants.CLUB_ZONE_ID)
    ) {
        currentOpenTime = openTimeStr
        currentCloseTime = closeTimeStr
        currentDuration = durationMinutes
        
        _selectedDateFlow.value = date
        _courtIdFlow.value = courtId
        
        selectedSlot = null
    }

    fun onDateSelected(date: LocalDate) {
        if (date != _selectedDateFlow.value) {
            selectedSlot = null
            _selectedDateFlow.value = date
        }
    }

    fun confirmBooking() {
        val slot = selectedSlot ?: return
        val courtId = _courtIdFlow.value ?: return
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