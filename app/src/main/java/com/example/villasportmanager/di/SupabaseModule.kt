package com.example.villasportmanager.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.jsonPrimitive

object SupabaseModule {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://nwolxadgjtsjnyyvnwos.supabase.co",
        supabaseKey = "sb_publishable_RAqpRGcMX_3s8Od56w8dvA_1dOFjQmk"
    )
    {
        httpEngine = OkHttp.create()
        install(Postgrest)
        install(Auth)
        install(Realtime)
    }
}

fun SupabaseClient.getDisplayName(): String? {
    return auth.currentUserOrNull()
        ?.userMetadata
        ?.get("display_name")
        ?.jsonPrimitive
        ?.content
}