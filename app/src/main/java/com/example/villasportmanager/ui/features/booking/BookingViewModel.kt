package com.example.villasportmanager.ui.features.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.model.BookingRequest
import com.example.villasportmanager.data.model.Court
import com.example.villasportmanager.data.model.Sport
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

data class CourtState(
    val court: Court,
    val slots: List<TimeSlot> = emptyList(),
    val isChecking: Boolean = false
)

class BookingViewModel(private val repository: BookingRepository) : ViewModel() {
    
    // --- Reactive State Sources ---
    private val _selectedDateFlow = MutableStateFlow(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    private val _currentSportNameFlow = MutableStateFlow<String?>(null)

    // --- UI Exposed State ---
    var sportsList by mutableStateOf<List<Sport>>(emptyList())
    var isLoading by mutableStateOf(false)

    val courtStates = mutableStateMapOf<String, CourtState>()
    
    val availableDates: List<LocalDate> = (0..6).map { LocalDate.now(AppConstants.CLUB_ZONE_ID).plusDays(it.toLong()) }
    var selectedDate by mutableStateOf(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    
    var selectedSlot by mutableStateOf<Pair<String, TimeSlot>?>(null)

    var isBooking by mutableStateOf(false)
    var bookingError by mutableStateOf<String?>(null)
    var bookingSuccess by mutableStateOf(false)
    
    var lastCreatedBookingId by mutableStateOf<String?>(null)

    var isCancelling by mutableStateOf(false)
    var cancellationError by mutableStateOf<String?>(null)
    var cancellationSuccess by mutableStateOf(false)

    init {
        // Core Reactive Logic: Combine all background data with current UI selections
        combine(
            BookingRepository.sports,
            BookingRepository.bookings,
            BookingRepository.isSyncing,
            _selectedDateFlow,
            _currentSportNameFlow
        ) { sports, bookings, isSyncing, date, sportName ->
            
            // Only show loading if we have absolutely NO data and are currently syncing
            isLoading = isSyncing && sports.isEmpty()
            selectedDate = date // Sync back to UI state
            
            if (sportName == null) return@combine
            
            // 1. Filter for the selected sport
            val filtered = sports.filter { it.name.equals(sportName, ignoreCase = true) }
            sportsList = filtered
            
            if (filtered.isNotEmpty()) {
                val sport = filtered.first()
                
                // 2. Map all courts and their slots instantly from the repository cache
                sport.courts.forEach { court ->
                    val cacheKey = "${court.id}_$date"
                    val courtBookings = bookings[cacheKey] ?: emptyList()
                    
                    val generatedSlots = generateSlots(sport, date)
                    val bookedTimes = courtBookings.mapNotNull {
                        try {
                            val zdt = ZonedDateTime.parse(it.startTime).withZoneSameInstant(AppConstants.CLUB_ZONE_ID)
                            if (zdt.toLocalDate() == date) zdt.toLocalTime() else null
                        } catch (e: Exception) { null }
                    }

                    val updatedSlots = generatedSlots.map { slot ->
                        slot.copy(isBooked = bookedTimes.contains(slot.time))
                    }
                    
                    courtStates[court.id] = CourtState(court = court, slots = updatedSlots, isChecking = false)
                }
            }
        }.launchIn(viewModelScope)
    }

    fun filterBySport(sportName: String, date: LocalDate = LocalDate.now(AppConstants.CLUB_ZONE_ID)) {
        _currentSportNameFlow.value = sportName
        _selectedDateFlow.value = date
    }

    private fun generateSlots(sport: Sport, date: LocalDate): List<TimeSlot> {
        return try {
            val openTime = LocalTime.parse(sport.openTime)
            val closeTime = LocalTime.parse(sport.closeTime)
            val duration = sport.slotDurationMinutes

            val openDateTime = date.atTime(openTime)
            val closeDateTime = date.atTime(closeTime)

            val generatedSlots = mutableListOf<TimeSlot>()
            var currentDateTime = openDateTime

            var count = 0
            while (count < 100 && !currentDateTime.plusMinutes(duration.toLong()).isAfter(closeDateTime)) {
                generatedSlots.add(TimeSlot(time = currentDateTime.toLocalTime(), isBooked = false))
                currentDateTime = currentDateTime.plusMinutes(duration.toLong())
                count++
            }
            generatedSlots
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun onDateSelected(date: LocalDate) {
        if (date != _selectedDateFlow.value) {
            selectedSlot = null
            _selectedDateFlow.value = date
        }
    }

    fun onSlotSelected(courtId: String, slot: TimeSlot) {
        selectedSlot = courtId to slot
    }

    fun confirmBooking() {
        val (courtId, slot) = selectedSlot ?: return
        val sport = sportsList.firstOrNull() ?: return
        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id ?: run {
            bookingError = "User not logged in"
            return
        }

        viewModelScope.launch {
            isBooking = true
            bookingError = null
            bookingSuccess = false

            val activeBookingsResult = repository.countActiveBookingsForSport(userId, sport.id)
            activeBookingsResult.onSuccess { count ->
                if (count >= 2) {
                    bookingError = "can't have more than 2 active bookings"
                    isBooking = false
                    return@launch
                }
            }.onFailure {
                bookingError = "Limit verification failed: ${it.message}"
                isBooking = false
                return@launch
            }

            val zdt = selectedDate.atTime(slot.time).atZone(AppConstants.CLUB_ZONE_ID)
            val startTimeIso = zdt.toOffsetDateTime().toString()
            val endTimeIso = zdt.plusMinutes(sport.slotDurationMinutes.toLong()).toOffsetDateTime().toString()

            val request = BookingRequest(courtId = courtId, userId = userId, startTime = startTimeIso, endTime = endTimeIso)
            val result = repository.createBooking(request)

            result.onSuccess { bookingId ->
                lastCreatedBookingId = bookingId
                bookingSuccess = true
            }.onFailure {
                bookingError = it.message ?: "Unknown error"
            }
            isBooking = false
        }
    }

    fun cancelLastBooking() {
        val bookingId = lastCreatedBookingId ?: return
        viewModelScope.launch {
            isCancelling = true
            cancellationError = null
            cancellationSuccess = false

            val result = repository.cancelBooking(bookingId)

            result.onSuccess {
                cancellationSuccess = true
                lastCreatedBookingId = null
            }.onFailure {
                cancellationError = it.message ?: "Failed to cancel booking"
            }

            isCancelling = false
        }
    }

    fun resetLastBookingStatus() {
        bookingSuccess = false
        cancellationSuccess = false
    }

    fun getEndTimeForSlot(startTime: LocalTime): LocalTime {
        val duration = sportsList.firstOrNull()?.slotDurationMinutes ?: 0
        return startTime.plusMinutes(duration.toLong())
    }

    fun clearBookingStatus() {
        bookingError = null
        bookingSuccess = false
        selectedSlot = null
    }
}

class BookingViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BookingViewModel(BookingRepository(SupabaseModule.client)) as T
    }
}
