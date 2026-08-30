package com.example.villasportmanager.di

import com.example.villasportmanager.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.jsonPrimitive

object SupabaseModule {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        httpEngine = OkHttp.create()
        install(Postgrest)
        install(Auth)
        install(Realtime)
        install(Storage)
    }
}

fun SupabaseClient.getDisplayName(): String? {
    return auth.currentUserOrNull()
        ?.userMetadata
        ?.get("display_name")
        ?.jsonPrimitive
        ?.content
}