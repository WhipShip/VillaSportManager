package com.example.villasportmanager.data.repository

import com.example.villasportmanager.data.model.Booking
import com.example.villasportmanager.data.model.UserBooking
import com.example.villasportmanager.data.model.BookingRequest
import com.example.villasportmanager.data.model.Sport
import com.example.villasportmanager.util.AppConstants
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

class BookingRepository(private val supabase: SupabaseClient) {

    companion object {
        private val _sports = MutableStateFlow<List<Sport>>(emptyList())
        val sports = _sports.asStateFlow()

        // Key: courtId_date (e.g., "court123_2026-08-28")
        private val _bookings = MutableStateFlow<Map<String, List<Booking>>>(emptyMap())
        val bookings = _bookings.asStateFlow()

        private val _isSyncing = MutableStateFlow(false)
        val isSyncing = _isSyncing.asStateFlow()

        private var isRealtimeInitialized = false
        private var lastPreloadDate: LocalDate? = null

        /**
         * Preloads all sports, courts, and bookings for the next 7 days into the background cache.
         */
        fun preloadData(supabase: SupabaseClient, scope: CoroutineScope) {
            val today = LocalDate.now(AppConstants.CLUB_ZONE_ID)
            // Skip if already preloaded today (unless forced)
            if (lastPreloadDate == today && _sports.value.isNotEmpty()) return
            
            scope.launch(Dispatchers.IO) {
                refreshAllData(supabase)
                lastPreloadDate = today
            }
        }

        suspend fun refreshAllData(supabase: SupabaseClient) {
            _isSyncing.value = true
            try {
                // 1. Fetch Sports and Courts
                val fetchedSports = supabase.postgrest["sports"]
                    .select(columns = Columns.raw("id, name, slot_duration_minutes, open_time, close_time, courts(id, name, sport_id)"))
                    .decodeList<Sport>()
                _sports.value = fetchedSports

                // 2. Fetch all confirmed bookings for the next 7 days
                val startDate = LocalDate.now(AppConstants.CLUB_ZONE_ID)
                val endDate = startDate.plusDays(7)
                
                val startIso = startDate.atStartOfDay().atZone(AppConstants.CLUB_ZONE_ID).toOffsetDateTime().toString()
                val endIso = endDate.atStartOfDay().atZone(AppConstants.CLUB_ZONE_ID).toOffsetDateTime().toString()

                val allBookings = supabase.postgrest["bookings"]
                    .select {
                        filter {
                            eq("status", "confirmed")
                            gte("start_time", startIso)
                            lt("start_time", endIso)
                        }
                    }.decodeList<Booking>()

                // 3. Group bookings by courtId_date and update the flow
                val newBookingsMap = allBookings.groupBy { booking ->
                    try {
                        val zdt = ZonedDateTime.parse(booking.startTime).withZoneSameInstant(AppConstants.CLUB_ZONE_ID)
                        "${booking.courtId}_${zdt.toLocalDate()}"
                    } catch (e: Exception) {
                        "unknown"
                    }
                }.filterKeys { it != "unknown" }

                _bookings.value = newBookingsMap
            } catch (e: Exception) {
                println("Preload/Sync Error: ${e.message}")
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }

        fun initializeGlobalRealtime(supabase: SupabaseClient, scope: CoroutineScope) {
            if (isRealtimeInitialized) return
            isRealtimeInitialized = true

            scope.launch {
                try {
                    supabase.realtime.connect()
                    val channel = supabase.realtime.channel("global_booking_updates")
                    val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "bookings"
                    }
                    channel.subscribe()
                    
                    flow.collect {
                        // On any change, trigger a background refresh to keep the cache consistent
                        refreshAllData(supabase)
                    }
                } catch (e: Exception) {
                    isRealtimeInitialized = false
                    e.printStackTrace()
                }
            }
        }
    }

    // --- Suspabase Operations (Wrappers around the reactive cache or direct calls) ---

    fun getBookingsForCourtFromCache(courtId: String, date: LocalDate): List<Booking> {
        return _bookings.value["${courtId}_$date"] ?: emptyList()
    }

    suspend fun getBookingsForCourt(courtId: String, date: LocalDate): Result<List<Booking>> {
        return try {
            val startIso = date.atStartOfDay().atZone(AppConstants.CLUB_ZONE_ID).toOffsetDateTime().toString()
            val endIso = date.plusDays(1).atStartOfDay().atZone(AppConstants.CLUB_ZONE_ID).toOffsetDateTime().toString()

            val bookings = supabase.postgrest["bookings"]
                .select {
                    filter {
                        eq("court_id", courtId)
                        eq("status", "confirmed")
                        gte("start_time", startIso)
                        lt("start_time", endIso)
                    }
                }.decodeList<Booking>()
            Result.success(bookings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBooking(bookingRequest: BookingRequest): Result<String> {
        return try {
            val response = supabase.postgrest["bookings"].insert(bookingRequest) {
                select()
            }.decodeSingle<Booking>()
            
            // The realtime listener will trigger a refresh, but we can also do it manually for speed
            refreshAllData(supabase)
            Result.success(response.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserBookings(userId: String): Result<List<UserBooking>> {
        return try {
            val bookings = supabase.postgrest["bookings"]
                .select(columns = Columns.raw("id, start_time, end_time, status, courts(id, name, sport_id)")) {
                    filter {
                        eq("user_id", userId)
                    }
                    order("start_time", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }.decodeList<UserBooking>()
            Result.success(bookings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun countActiveBookingsForSport(userId: String, sportId: String): Result<Int> {
        return try {
            val now = java.time.ZonedDateTime.now(AppConstants.CLUB_ZONE_ID).toOffsetDateTime().toString()
            val bookings = supabase.postgrest["bookings"]
                .select(columns = Columns.raw("id, start_time, end_time, status, courts!inner(id, name, sport_id)")) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "confirmed")
                        eq("courts.sport_id", sportId)
                        gt("end_time", now)
                    }
                }.decodeList<UserBooking>()
            Result.success(bookings.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(bookingId: String): Result<Unit> {
        return try {
            supabase.postgrest["bookings"].update(
                update = {
                    set("status", "cancelled")
                }
            ) {
                filter {
                    eq("id", bookingId)
                }
            }
            refreshAllData(supabase)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subscribeToBookingChanges(): Flow<PostgresAction> {
        val channel = supabase.realtime.channel("booking_updates_local")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "bookings"
        }
        channel.subscribe()
        return flow
    }
}
