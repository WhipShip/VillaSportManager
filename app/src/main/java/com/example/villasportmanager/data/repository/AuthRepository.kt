package com.example.villasportmanager.data.repository

import com.example.villasportmanager.di.getDisplayName
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository(private val supabase: SupabaseClient) {

    suspend fun signIn(username: String, password: String): Result<Unit> {
        return try {
            // The workaround: map the username to your dummy domain
            val dummyEmail = "${username.trim()}@myapp.local"

            supabase.auth.signInWith(Email) {
                email = dummyEmail
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDisplayName(): String? = supabase.getDisplayName()
}