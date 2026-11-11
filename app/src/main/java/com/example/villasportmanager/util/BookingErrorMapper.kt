package com.example.villasportmanager.util

/**
 * Turns a PostgREST / Postgres failure into one sentence a villa can act on.
 *
 * Pure Kotlin so it is unit-tested on the JVM (see BookingErrorMapperTest). The patterns match the
 * SQLSTATEs and the trigger HINTs defined in supabase/02_booking_integrity.sql and
 * supabase/03_booking_rls_hardening.sql:
 *
 *  23P01 / "exclusion"      another confirmed row overlaps (bookings_no_overlap_confirmed)
 *  23505 / "duplicate key"  unique violation (e.g. the same client_request_id sent twice)
 *  P0001 + hint             kayan_check_slot_grid / kayan_check_booking_limit
 *  23514                    CHECK constraint (status vocabulary, end > start)
 *  42501                    row-level security refused the write
 */
object BookingErrorMapper {

    const val SLOT_TAKEN = "This slot was just booked by someone else. Please pick another one."
    const val LIMIT_REACHED = "You have reached the maximum of 2 active bookings for this sport."
    const val SLOT_ENDED = "That slot has already ended. Please pick a later one."
    const val NOT_A_SLOT = "That time can't be booked. Please pick a slot from the list."
    const val NOT_ALLOWED = "This account isn't allowed to make that booking."
    const val OFFLINE = "Couldn't reach the club server. Check the Wi-Fi and try again."
    const val GENERIC = "An unexpected error occurred. Please try again."

    /**
     * @param message exception message (PostgREST puts the Postgres message here)
     * @param code    SQLSTATE if the client library exposes it, else null
     * @param hint    Postgres HINT if exposed, else null (our triggers put a stable token here)
     * @param details Postgres DETAIL if exposed, else null
     */
    fun friendlyMessage(message: String?, code: String? = null, hint: String? = null, details: String? = null): String {
        val all = listOfNotNull(message, code, hint, details).joinToString("\n")
        val lower = all.lowercase()
        return when {
            has(lower, "23p01") || has(lower, "exclusion constraint") || has(lower, "conflicts with existing key") -> SLOT_TAKEN
            has(lower, "23505") || has(lower, "duplicate key") -> SLOT_TAKEN
            has(lower, "booking_limit") -> LIMIT_REACHED
            has(lower, "past_slot") || has(lower, "already ended") -> SLOT_ENDED
            has(lower, "slot_grid") || has(lower, "misaligned") || has(lower, "slot_length") || has(lower, "outside_hours") -> NOT_A_SLOT
            has(lower, "23514") || has(lower, "check constraint") -> NOT_A_SLOT
            has(lower, "42501") || has(lower, "row-level security") -> NOT_ALLOWED
            has(lower, "unable to resolve host") || has(lower, "timeout") || has(lower, "failed to connect") || has(lower, "connectexception") -> OFFLINE
            message.isNullOrBlank() -> GENERIC
            else -> message
        }
    }

    private fun has(haystack: String, needle: String) = haystack.contains(needle)
}
