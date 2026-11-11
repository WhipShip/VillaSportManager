package com.example.villasportmanager

import com.example.villasportmanager.util.BookingErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingErrorMapperTest {

    @Test
    fun exclusionViolationIsSlotTaken() {
        val msg = "conflicting key value violates exclusion constraint \"bookings_no_overlap_confirmed\""
        assertEquals(BookingErrorMapper.SLOT_TAKEN, BookingErrorMapper.friendlyMessage(msg, code = "23P01"))
        assertEquals(BookingErrorMapper.SLOT_TAKEN, BookingErrorMapper.friendlyMessage(msg))
    }

    @Test
    fun uniqueViolationIsSlotTaken() {
        assertEquals(BookingErrorMapper.SLOT_TAKEN, BookingErrorMapper.friendlyMessage("duplicate key value violates unique constraint", "23505"))
    }

    @Test
    fun limitTriggerHintIsLimitMessage() {
        assertEquals(
            BookingErrorMapper.LIMIT_REACHED,
            BookingErrorMapper.friendlyMessage("booking_limit: already 2 active bookings for this sport (max 2)", "P0001", "booking_limit"),
        )
        // hint may be missing from the client exception; the message still carries the token
        assertEquals(BookingErrorMapper.LIMIT_REACHED, BookingErrorMapper.friendlyMessage("booking_limit: already 2 active bookings"))
    }

    @Test
    fun gridTriggerIsNotASlot() {
        assertEquals(BookingErrorMapper.NOT_A_SLOT, BookingErrorMapper.friendlyMessage("slot_grid: start 10:30 is not on the 60 minute grid", "P0001", "misaligned"))
        assertEquals(BookingErrorMapper.NOT_A_SLOT, BookingErrorMapper.friendlyMessage("new row violates check constraint \"bookings_time_order_check\"", "23514"))
    }

    @Test
    fun pastSlotIsEnded() {
        assertEquals(BookingErrorMapper.SLOT_ENDED, BookingErrorMapper.friendlyMessage("slot_grid: that slot has already ended", "P0001", "past_slot"))
    }

    @Test
    fun rlsRefusalIsNotAllowed() {
        assertEquals(BookingErrorMapper.NOT_ALLOWED, BookingErrorMapper.friendlyMessage("new row violates row-level security policy for table \"bookings\"", "42501"))
    }

    @Test
    fun networkFailureIsOffline() {
        assertEquals(BookingErrorMapper.OFFLINE, BookingErrorMapper.friendlyMessage("Unable to resolve host \"x.supabase.co\""))
    }

    @Test
    fun unknownMessageIsPassedThroughAndBlankIsGeneric() {
        assertEquals("something odd", BookingErrorMapper.friendlyMessage("something odd"))
        assertEquals(BookingErrorMapper.GENERIC, BookingErrorMapper.friendlyMessage(null))
        assertEquals(BookingErrorMapper.GENERIC, BookingErrorMapper.friendlyMessage(""))
    }
}
