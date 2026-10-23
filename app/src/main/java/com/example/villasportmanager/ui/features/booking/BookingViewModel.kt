package com.example.villasportmanager.ui.features.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.model.Booking
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

/** Snapshot of a booking that was just created, captured before any cache refresh clears the selection. */
data class ConfirmedBooking(
    val bookingId: String,
    val sportName: String,
    val courtName: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
)

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
    /** Every sport the club offers, used by the in-screen sport switcher. */
    var allSports by mutableStateOf<List<Sport>>(emptyList())
    /** Name of the sport currently shown (null until [filterBySport] is called). */
    var currentSportName by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    val courtStates = mutableStateMapOf<String, CourtState>()
    
    val availableDates: List<LocalDate> = (0..6).map { LocalDate.now(AppConstants.CLUB_ZONE_ID).plusDays(it.toLong()) }
    var selectedDate by mutableStateOf(LocalDate.now(AppConstants.CLUB_ZONE_ID))
    
    var selectedSlot by mutableStateOf<Pair<String, TimeSlot>?>(null)

    var isBooking by mutableStateOf(false)
    var bookingError by mutableStateOf<String?>(null)
    var bookingSuccess by mutableStateOf(false)
    
    var lastCreatedBookingId by mutableStateOf<String?>(null)
    /** Set once a booking succeeds; the screen navigates on it and then calls [consumeConfirmedBooking]. */
    var confirmedBooking by mutableStateOf<ConfirmedBooking?>(null)

    var isCancelling by mutableStateOf(false)
    var cancellationError by mutableStateOf<String?>(null)
    var cancellationSuccess by mutableStateOf(false)

    init {
        // Core Reactive Logic: Combine all background data with current UI selections
        combine(
            BookingRepository.sports,
            BookingRepository.bookings,
            BookingRepository.isSyncing,
            BookingRepository.isInitialized,
            _selectedDateFlow,
            _currentSportNameFlow
        ) { args ->
            val sports = args[0] as List<Sport>
            @Suppress("UNCHECKED_CAST")
            val bookings = args[1] as Map<String, List<Booking>>
            val isSyncing = args[2] as Boolean
            val isInitialized = args[3] as Boolean
            val date = args[4] as LocalDate
            val sportName = args[5] as String?

            // Show loading if we are currently syncing OR if the cache hasn't been initialized yet
            isLoading = isSyncing || !isInitialized
            selectedDate = date // Sync back to UI state
            allSports = sports
            currentSportName = sportName

            if (sportName == null) return@combine

            // 1. Filter for the selected sport
            val filtered = sports.filter { it.name.equals(sportName, ignoreCase = true) }
            sportsList = filtered

            // 2. Rebuild every court of that sport from the repository cache
            val myUserId = SupabaseModule.client.auth.currentUserOrNull()?.id
            val newStates = linkedMapOf<String, CourtState>()
            filtered.firstOrNull()?.let { sport ->
                sport.courts.sortedBy { it.name }.forEach { court ->
                    val courtBookings = bookings["${court.id}_$date"] ?: emptyList<Booking>()
                    val bookedByTime = courtBookings.mapNotNull { booking ->
                        try {
                            val zdt = ZonedDateTime.parse(booking.startTime).withZoneSameInstant(AppConstants.CLUB_ZONE_ID)
                            if (zdt.toLocalDate() == date) zdt.toLocalTime() to booking else null
                        } catch (e: Exception) { null }
                    }.toMap()

                    val updatedSlots = generateSlots(sport, date).map { slot ->
                        val booking = bookedByTime[slot.time]
                        slot.copy(
                            isBooked = booking != null,
                            isMine = booking != null && myUserId != null && booking.userId == myUserId
                        )
                    }
                    newStates[court.id] = CourtState(court = court, slots = updatedSlots, isChecking = false)
                }
            }
            // Replace (not merge) so courts of a previously shown sport disappear
            (courtStates.keys - newStates.keys).forEach { courtStates.remove(it) }
            courtStates.putAll(newStates)

            // Drop a selection that is no longer valid (slot got booked, or court vanished)
            selectedSlot?.let { (courtId, slot) ->
                val fresh = newStates[courtId]?.slots?.firstOrNull { it.time == slot.time }
                if (fresh == null || fresh.isBooked) selectedSlot = null
            }
        }.launchIn(viewModelScope)
    }

    fun filterBySport(sportName: String, date: LocalDate = LocalDate.now(AppConstants.CLUB_ZONE_ID)) {
        _currentSportNameFlow.value = sportName
        _selectedDateFlow.value = date
    }

    /** Switches sport from inside the booking screen, keeping the chosen day. */
    fun switchSport(sportName: String) {
        if (sportName.equals(_currentSportNameFlow.value, ignoreCase = true)) return
        selectedSlot = null
        _currentSportNameFlow.value = sportName
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
                    bookingError = "You have reached the maximum of 2 active bookings for this sport."
                    isBooking = false
                    return@launch
                }
            }.onFailure {
                bookingError = "Unable to verify booking limits. Please try again."
                isBooking = false
                return@launch
            }

            val bookingDate = selectedDate
            val courtName = courtStates[courtId]?.court?.name ?: ""
            val zdt = bookingDate.atTime(slot.time).atZone(AppConstants.CLUB_ZONE_ID)
            val startTimeIso = zdt.toOffsetDateTime().toString()
            val endTimeIso = zdt.plusMinutes(sport.slotDurationMinutes.toLong()).toOffsetDateTime().toString()

            val request = BookingRequest(courtId = courtId, userId = userId, startTime = startTimeIso, endTime = endTimeIso)
            val result = repository.createBooking(request)

            result.onSuccess { bookingId ->
                lastCreatedBookingId = bookingId
                confirmedBooking = ConfirmedBooking(
                    bookingId = bookingId,
                    sportName = sport.name,
                    courtName = courtName,
                    date = bookingDate,
                    startTime = slot.time,
                    endTime = slot.time.plusMinutes(sport.slotDurationMinutes.toLong())
                )
                bookingSuccess = true
            }.onFailure {
                bookingError = when {
                    it.message?.contains("duplicate key", ignoreCase = true) == true || 
                    it.message?.contains("23505") == true ||
                    it.message?.contains("23P01") == true ||
                    it.message?.contains("exclusion constraint", ignoreCase = true) == true -> 
                        "This slot was just booked by someone else. Please pick another one."
                    else -> it.message ?: "An unexpected error occurred. Please try again."
                }
                // Force refresh to show current availability even on failure
                BookingRepository.refreshAllData(SupabaseModule.client)
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

    /** Cancels a specific booking by id (used by the success screen, which owns its own ViewModel). */
    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            isCancelling = true
            cancellationError = null
            cancellationSuccess = false

            repository.cancelBooking(bookingId)
                .onSuccess {
                    cancellationSuccess = true
                    if (lastCreatedBookingId == bookingId) lastCreatedBookingId = null
                }
                .onFailure { cancellationError = it.message ?: "Failed to cancel booking" }

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

    fun consumeConfirmedBooking() {
        confirmedBooking = null
        bookingSuccess = false
        selectedSlot = null
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
