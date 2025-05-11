package com.example.atlas.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atlas.models.User
import com.example.atlas.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context // Inject Context for SharedPreferences
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    private val _isAuthenticated = MutableStateFlow(false)
    private val _hasCompletedInfo = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val user: StateFlow<User?> = _user
    val hasCompletedInfo: StateFlow<Boolean> = _hasCompletedInfo
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    val error: StateFlow<String?> = _error

    // Initialize SharedPreferences
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
    }

    suspend fun loadUserAuthInfo(): User? {
        Log.d("UserViewModel", "Fetching user auth data...")
        try {
            val userData = userRepository.getCurrentUser()
            Log.d("UserViewModel", "User auth loaded: $userData")
            _user.value = userData
            _hasCompletedInfo.value = !userData?.name.isNullOrEmpty()
            Log.d("UserViewModel", "hasCompletedInfo: ${_hasCompletedInfo.value}")
            _error.value = null
            return userData
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error loading user auth: ${e.localizedMessage}", e)
            _error.value = "Error al cargar datos de autenticación: ${e.localizedMessage}"
            return null
        }
    }

    suspend fun loadUserClientInfo(): User? {
        Log.d("UserViewModel", "Fetching user client data...")
        try {
            val userData = userRepository.getCurrentClient()
            Log.d("UserViewModel", "User client loaded: $userData")
            _user.value = userData
            _hasCompletedInfo.value = !userData?.name.isNullOrEmpty()
            Log.d("UserViewModel", "hasCompletedInfo: ${_hasCompletedInfo.value}")
            _error.value = null

            // Save user ID and name to SharedPreferences if userData is not null
            if (userData != null) {
                sharedPreferences.edit().putString(KEY_USER_ID, userData.id).apply()
                sharedPreferences.edit().putString("${userData.id}_name", userData.name).apply()
                Log.d("UserViewModel", "Saved user ID to SharedPreferences: ${userData.id}")
            }

            return userData
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error loading user client: ${e.localizedMessage}", e)
            _error.value = "Error al cargar usuario: ${e.localizedMessage}"
            _user.value = null
            return null
        }
    }

    suspend fun insertUserInClients() {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Inserting user into clients table")
                userRepository.insertUserInClients()
                Log.d("UserViewModel", "User inserted successfully")
                loadUserClientInfo() // Refresh user data
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error inserting user: ${e.localizedMessage}", e)
                _error.value = "Error al insertar usuario: ${e.localizedMessage}"
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        Log.d("UserViewModel", "Starting email sign-in: $email")
        viewModelScope.launch {
            try {
                userRepository.signInWithEmail(email, password)
                delay(1000)
                _isAuthenticated.value = true
                loadUserClientInfo()
                Log.d("UserViewModel", "Email sign-in successful")
                _error.value = null
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Email sign-in error: ${e.localizedMessage}", e)
                _isAuthenticated.value = false
                val errorMessage = if (e.message?.contains("Invalid login credentials", ignoreCase = true) == true) {
                    "Credenciales inválidas"
                } else {
                    "Error al iniciar sesión: ${e.localizedMessage}"
                }
                _error.value = errorMessage
                onResult(false, errorMessage)
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        Log.d("UserViewModel", "Starting email sign-up: $email")
        viewModelScope.launch {
            try {
                userRepository.signUpWithEmail(email, password)
                Log.d("UserViewModel", "Email sign-up successful")
                _isAuthenticated.value = true
                loadUserClientInfo()
                _error.value = null
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Email sign-up error: ${e.localizedMessage}", e)
                _isAuthenticated.value = false
                val errorMessage = when {
                    e.message?.contains("already registered", ignoreCase = true) == true -> {
                        "El correo ya está registrado"
                    }
                    e.message?.contains("invalid", ignoreCase = true) == true -> {
                        "Correo o contraseña inválidos"
                    }
                    else -> {
                        "Error al registrarse: ${e.localizedMessage}"
                    }
                }
                _error.value = errorMessage
                onResult(false, errorMessage)
            }
        }
    }

    fun updateAuthUser(name: String) {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Updating auth user: name=$name")
                userRepository.updateAuthUser(name)
                loadUserClientInfo() // Refresh user data
                Log.d("UserViewModel", "Auth user update successful")
                _error.value = null
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error updating auth user: ${e.localizedMessage}", e)
                _error.value = "Error al actualizar usuario: ${e.localizedMessage}"
            }
        }
    }

    fun updateClientsUser(name: String) {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Updating clients user: name=$name")
                userRepository.updateClientsUser(name)
                loadUserClientInfo() // Refresh user data
                Log.d("UserViewModel", "Clients user update successful")
                _error.value = null
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error updating clients user: ${e.localizedMessage}", e)
                _error.value = "Error al actualizar usuario: ${e.localizedMessage}"
            }
        }
    }

    fun checkUserSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val hasSession = userRepository.checkUserSession()
                if (hasSession) {
                    _isAuthenticated.value = true
                    loadUserClientInfo()
                    Log.d("UserViewModel", "Session check successful")
                    _error.value = null
                } else {
                    Log.e("UserViewModel", "No active session found")
                    _error.value = "No se encontró una sesión activa"
                }
                onResult(hasSession)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Session check error: ${e.localizedMessage}", e)
                _error.value = "Error al verificar sesión: ${e.localizedMessage}"
                onResult(false)
            }
        }
    }

    fun logout(context: Context) {
        viewModelScope.launch {
            try {
                userRepository.logout()
                _isAuthenticated.value = false
                _user.value = null
                _error.value = null
                // Clear user ID from SharedPreferences on logout
                sharedPreferences.edit().remove(KEY_USER_ID).apply()
                Log.d("UserViewModel", "Cleared user ID from SharedPreferences on logout")
                Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                Log.d("UserViewModel", "User signed out")
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error signing out: ${e.localizedMessage}", e)
                _error.value = "Error al cerrar sesión: ${e.localizedMessage}"
                Toast.makeText(context, "Logout failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun resetPassword(email: String): Boolean {
        return try {
            Log.d("UserViewModel", "Attempting password reset for email: $email")
            val success = userRepository.resetPassword(email)
            Log.d("UserViewModel", "Password reset result: $success")
            _error.value = null
            success
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error resetting password: ${e.localizedMessage}", e)
            _error.value = "Error al restablecer contraseña: ${e.localizedMessage}"
            false
        }
    }

    suspend fun verifyRecoveryOtp(email: String, token: String) {
        try {
            Log.d("UserViewModel", "Verifying recovery OTP for email: $email, token: $token")
            userRepository.verifyRecoveryOtp(email, token)
            _isAuthenticated.value = true
            loadUserClientInfo()
            Log.d("UserViewModel", "Recovery OTP verified")
            _error.value = null
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error verifying recovery OTP: ${e.localizedMessage}", e)
            _error.value = "Error al verificar OTP: ${e.localizedMessage}"
            throw e
        }
    }

    suspend fun updatePassword(newPassword: String) {
        try {
            Log.d("UserViewModel", "Updating password")
            userRepository.updatePassword(newPassword)
            Log.d("UserViewModel", "Password updated successfully")
            _error.value = null
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error updating password: ${e.localizedMessage}", e)
            _error.value = "Error al actualizar contraseña: ${e.localizedMessage}"
            throw e
        }
    }

    fun resetPasswordFinal(newPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                userRepository.resetPasswordFinal(newPassword)
                Log.d("UserViewModel", "Password reset successful")
                _error.value = null
                onResult(true)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Password reset error: ${e.localizedMessage}", e)
                _error.value = "Error al restablecer contraseña: ${e.localizedMessage}"
                onResult(false)
            }
        }
    }

    fun verifyEmailOtp(email: String, token: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                userRepository.verifyEmailOtp(email, token)
                _isAuthenticated.value = true
                loadUserClientInfo()
                Log.d("UserViewModel", "Email verification successful for $email")
                _error.value = null
                onResult(true)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Email verification error: ${e.localizedMessage}", e)
                _error.value = "Error al verificar correo: ${e.localizedMessage}"
                onResult(false)
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                userRepository.sendPasswordResetEmail(email)
                Log.d("UserViewModel", "Password reset email sent to $email")
                _error.value = null
                onResult(true)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error sending password reset email: ${e.localizedMessage}", e)
                _error.value = "Error al enviar correo de restablecimiento: ${e.localizedMessage}"
                onResult(false)
            }
        }
    }

    fun exchangeCodeForSession(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                userRepository.exchangeCodeForSession(code)
                _isAuthenticated.value = true
                loadUserClientInfo()
                Log.d("UserViewModel", "Session exchanged successfully for code=$code")
                _error.value = null
                onResult(true)
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error exchanging code for session: ${e.localizedMessage}", e)
                _error.value = "Error al intercambiar código por sesión: ${e.localizedMessage}"
                _isAuthenticated.value = false
                onResult(false)
            }
        }
    }

    fun getCurrentUserId(): String? {
        return _user.value?.id
    }

    // Function to get the user ID from SharedPreferences when offline
    fun getSavedUserId(): String? {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        Log.d("UserViewModel", "Retrieved user ID from SharedPreferences: $userId")
        return userId
    }
}