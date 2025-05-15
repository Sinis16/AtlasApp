package com.example.atlas.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atlas.data.TrackerRepository
import com.example.atlas.data.repository.UserRepository
import com.example.atlas.models.Tracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val trackerRepository: TrackerRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _trackers = MutableStateFlow<List<Tracker>>(emptyList())
    val trackers: StateFlow<List<Tracker>> = _trackers.asStateFlow()

    private val _userTrackers = MutableStateFlow<List<Tracker>>(emptyList())
    private val _selectedTracker = MutableStateFlow<Tracker?>(null)
    val selectedTracker: StateFlow<Tracker?> = _selectedTracker.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun getTrackers(userId: String? = null, offset: Int = 0, limit: Int = 100) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val effectiveUserId = userId ?: userRepository.getCurrentUser()?.id
                if (effectiveUserId == null) {
                    _errorMessage.value = "No user logged in"
                    _trackers.value = emptyList()
                    return@launch
                }
                val fetchedTrackers = trackerRepository.getTrackers(effectiveUserId, offset, limit)
                _trackers.value = fetchedTrackers
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch trackers: ${e.localizedMessage}"
                _trackers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getTrackersByUserId(userId: String): StateFlow<List<Tracker>> {
        viewModelScope.launch {
            if (userId.isBlank()) {
                _userTrackers.value = emptyList()
                _errorMessage.value = "Invalid user ID"
                return@launch
            }
            _isLoading.value = true
            try {
                val trackers = trackerRepository.getTrackersByUserId(userId)
                _userTrackers.value = trackers
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch user trackers: ${e.localizedMessage}"
                _userTrackers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
        return _userTrackers.asStateFlow()
    }

    fun getTrackerById(trackerId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _selectedTracker.value = trackerRepository.getTrackerById(trackerId)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch tracker: ${e.localizedMessage}"
                _selectedTracker.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getTrackerByBleId(bleId: String): Tracker? {
        _isLoading.value = true
        return try {
            val tracker = trackerRepository.getTrackerByBleId(bleId)
            _selectedTracker.value = tracker
            _errorMessage.value = null
            tracker
        } catch (e: Exception) {
            _errorMessage.value = "Failed to fetch tracker by BLE ID: ${e.localizedMessage}"
            _selectedTracker.value = null
            null
        } finally {
            _isLoading.value = false
        }
    }

    fun addTracker(tracker: Tracker) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                trackerRepository.addTracker(tracker)
                _errorMessage.value = null
                // Refresh trackers list after adding
                val userId = userRepository.getCurrentUser()?.id
                if (userId != null) {
                    _trackers.value = trackerRepository.getTrackers(userId)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add tracker: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTracker(tracker: Tracker) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                trackerRepository.updateTracker(tracker)
                _errorMessage.value = null
                // Refresh trackers list after updating
                val userId = userRepository.getCurrentUser()?.id
                if (userId != null) {
                    _trackers.value = trackerRepository.getTrackers(userId)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update tracker: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTracker(trackerId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                trackerRepository.deleteTracker(trackerId)
                _trackers.value = _trackers.value.filter { it.id != trackerId }
                _selectedTracker.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete tracker: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}