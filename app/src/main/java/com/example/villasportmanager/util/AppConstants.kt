package com.example.villasportmanager.util

import java.time.ZoneId

object AppConstants {
    /**
     * The timezone where the sports club is physically located.
     * Using a specific region ID (like "Europe/Athens") allows Java to 
     * automatically handle Daylight Savings (UTC+2 vs UTC+3).
     */
    val CLUB_ZONE_ID: ZoneId = ZoneId.of("Africa/Cairo")
}
