package com.example.atlas.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atlas.data.TrackerRepository
import com.example.atlas.models.Tracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val trackerRepository: TrackerRepository
) : ViewModel() {
    var trackers = mutableStateOf<List<Tracker>>(emptyList())
        private set
    var selectedTracker = mutableStateOf<Tracker?>(null)
        private set
    var isLoading = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    fun getTrackers(userId: String?, offset: Int = 0, limit: Int = 100) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                trackers.value = trackerRepository.getTrackers(userId, offset, limit)
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to fetch trackers: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun getTrackerById(trackerId: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                selectedTracker.value = trackerRepository.getTrackerById(trackerId)
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to fetch tracker: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun getTrackerByBleId(bleId: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                selectedTracker.value = trackerRepository.getTrackerByBleId(bleId)
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to fetch tracker by BLE ID: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addTracker(tracker: Tracker) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                trackerRepository.addTracker(tracker)
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to add tracker: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updateTracker(tracker: Tracker) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                trackerRepository.updateTracker(tracker)
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to update tracker: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deleteTracker(trackerId: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                trackerRepository.deleteTracker(trackerId)
                trackers.value = trackers.value.filter { it.id != trackerId }
                errorMessage.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to delete tracker: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}