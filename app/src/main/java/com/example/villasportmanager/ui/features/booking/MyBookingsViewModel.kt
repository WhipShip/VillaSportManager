package com.example.villasportmanager.ui.features.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.model.UserBooking
import com.example.villasportmanager.data.repository.BookingRepository
import com.example.villasportmanager.di.SupabaseModule
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MyBookingsViewModel(private val repository: BookingRepository) : ViewModel() {

    var bookings by mutableStateOf<List<UserBooking>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Cancellation states
    var isCancelling by mutableStateOf(false)
    var cancellationError by mutableStateOf<String?>(null)
    var cancellationSuccess by mutableStateOf(false)

    init {
        // Automatically reload when global data is refreshed or synced
        BookingRepository.refreshTrigger.onEach {
            loadBookings()
        }.launchIn(viewModelScope)
    }

    fun loadBookings() {
        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id ?: return
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val result = repository.getUserBookings(userId)
            
            result.onSuccess {
                bookings = it
            }.onFailure {
                error = it.message ?: "Failed to load bookings"
            }
            
            isLoading = false
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            isCancelling = true
            cancellationError = null
            cancellationSuccess = false

            val result = repository.cancelBooking(bookingId)

            result.onSuccess {
                cancellationSuccess = true
                loadBookings() // Refresh the list
            }.onFailure {
                cancellationError = it.message ?: "Failed to cancel booking"
            }

            isCancelling = false
        }
    }

    fun clearCancellationStatus() {
        cancellationError = null
        cancellationSuccess = false
    }
}

class MyBookingsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MyBookingsViewModel(BookingRepository(SupabaseModule.client)) as T
    }
}
