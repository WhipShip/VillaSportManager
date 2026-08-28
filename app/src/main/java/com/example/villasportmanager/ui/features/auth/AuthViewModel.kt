package com.example.villasportmanager.ui.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.villasportmanager.data.repository.AuthRepository
import com.example.villasportmanager.di.SupabaseModule
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun signIn(onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both fields."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val result = repository.signIn(username, password)
            isLoading = false

            if (result.isSuccess) {
                onSuccess()
            } else {
                errorMessage = "Invalid username or password."
            }
        }
    }
}

// A Factory is required to pass the Repository into the ViewModel
class AuthViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = AuthRepository(SupabaseModule.client)
        return AuthViewModel(repository) as T
    }
}