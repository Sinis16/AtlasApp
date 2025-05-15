package com.example.atlas.data

import android.util.Log
import com.example.atlas.models.Tracker
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    suspend fun getTrackers(
        userId: String?,
        offset: Int = 0,
        limit: Int = 100
    ): List<Tracker> = withContext(Dispatchers.IO) {
        runCatching {
            val query = supabaseClient.from("trackers").select {
                filter {
                    if (userId != null) {
                        or {
                            eq("user1", userId)
                            eq("user2", userId)
                            eq("user3", userId)
                        }
                    }
                }
                order("last_connection", order = Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }
            val trackers = query.decodeList<Tracker>().filter { it.id != null }
            Log.d("TrackerRepository", "Fetched trackers: $trackers")
            trackers
        }.getOrElse {
            Log.e("TrackerRepository", "Error fetching trackers: ${it.localizedMessage}", it)
            emptyList()
        }
    }

    suspend fun getTrackersByUserId(userId: String): List<Tracker> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()
        supabaseClient.from("trackers")
            .select {
                filter {
                    or {
                        eq("user1", userId)
                        eq("user2", userId)
                        eq("user3", userId)
                    }
                }
            }
            .decodeList<Tracker>()
    }

    suspend fun getTrackerById(trackerId: String): Tracker? = withContext(Dispatchers.IO) {
        runCatching {
            val tracker = supabaseClient.from("trackers").select(
                columns = Columns.list(
                    "id",
                    "ble_id",
                    "name",
                    "user1",
                    "user2",
                    "user3",
                    "last_connection",
                    "last_latitude",
                    "last_longitude",
                    "type"
                )
            ) {
                filter { eq("id", trackerId) }
            }.decodeSingle<Tracker>()
            if (tracker.id == null) {
                Log.w("TrackerRepository", "Tracker with id $trackerId has null id")
                null
            } else {
                Log.d("TrackerRepository", "Fetched tracker: $tracker")
                tracker
            }
        }.getOrElse {
            Log.e("TrackerRepository", "Error fetching tracker: ${it.localizedMessage}", it)
            null
        }
    }

    suspend fun getTrackerByBleId(bleId: String): Tracker? = withContext(Dispatchers.IO) {
        runCatching {
            val tracker = supabaseClient.from("trackers").select(
                columns = Columns.list(
                    "id",
                    "ble_id",
                    "name",
                    "user1",
                    "user2",
                    "user3",
                    "last_connection",
                    "last_latitude",
                    "last_longitude",
                    "type"
                )
            ) {
                filter { eq("ble_id", bleId) }
            }.decodeSingleOrNull<Tracker>()
            if (tracker?.id == null) {
                Log.w("TrackerRepository", "Tracker with ble_id $bleId has null id")
                null
            } else {
                Log.d("TrackerRepository", "Fetched tracker by ble_id: $tracker")
                tracker
            }
        }.getOrElse {
            Log.e("TrackerRepository", "Error fetching tracker by ble_id: ${it.localizedMessage}", it)
            null
        }
    }

    fun getTrackersByBleIds(bleIds: List<String>): Flow<List<Tracker>> = flow {
        if (bleIds.isNotEmpty()) {
            try {
                val trackers = supabaseClient.from("trackers")
                    .select {
                        filter {
                            isIn("ble_id", bleIds)
                        }
                    }
                    .decodeList<Tracker>()
                emit(trackers)
            } catch (e: Exception) {
                emit(emptyList())
            }
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getAllTrackers(): List<Tracker> = withContext(Dispatchers.IO) {
        runCatching {
            val trackers = supabaseClient.from("trackers").select()
                .decodeList<Tracker>().filter { it.id != null }
            Log.d("TrackerRepository", "Fetched all trackers: $trackers")
            trackers
        }.getOrElse {
            Log.e("TrackerRepository", "Error fetching all trackers: ${it.localizedMessage}", it)
            emptyList()
        }
    }

    suspend fun addTracker(tracker: Tracker) = withContext(Dispatchers.IO) {
        runCatching {
            // Ensure id is null to let Supabase auto-generate it
            val validTracker = tracker.copy(id = null)
            supabaseClient.from("trackers").insert(validTracker)
            Log.d("TrackerRepository", "Added tracker: $validTracker")
        }.onFailure {
            Log.e("TrackerRepository", "Error adding tracker: ${it.localizedMessage}", it)
            throw it
        }
    }

    suspend fun updateTracker(tracker: Tracker) = withContext(Dispatchers.IO) {
        runCatching {
            if (tracker.id == null) {
                Log.w("TrackerRepository", "Cannot update tracker with null id")
                return@withContext
            }
            supabaseClient.from("trackers").update(
                {
                    set("ble_id", tracker.ble_id)
                    set("name", tracker.name)
                    set("user1", tracker.user1)
                    set("user2", tracker.user2)
                    set("user3", tracker.user3)
                    set("last_connection", tracker.last_connection?.toString())
                    set("last_latitude", tracker.last_latitude)
                    set("last_longitude", tracker.last_longitude)
                    set("type", tracker.type)
                }
            ) {
                filter { eq("id", tracker.id!!) }
            }
            Log.d("TrackerRepository", "Updated tracker: $tracker")
        }.onFailure {
            Log.e("TrackerRepository", "Error updating tracker: ${it.localizedMessage}", it)
            throw it
        }
    }

    suspend fun deleteTracker(trackerId: String) = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.from("trackers").delete {
                filter { eq("id", trackerId) }
            }
            Log.d("TrackerRepository", "Deleted tracker: $trackerId")
        }.onFailure {
            Log.e("TrackerRepository", "Error deleting tracker: ${it.localizedMessage}", it)
            throw it
        }
    }
}