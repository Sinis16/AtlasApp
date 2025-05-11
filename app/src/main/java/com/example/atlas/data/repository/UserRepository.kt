package com.example.atlas.data.repository

import android.util.Log
import com.example.atlas.models.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {

    suspend fun hasActiveSession(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check for existing session
                val session = supabaseClient.auth.currentSessionOrNull()
                if (session != null) {
                    Log.d("UserRepository", "Active session found: $session")
                    return@withContext true
                }

                // Try to restore session
                val restoredSession = supabaseClient.auth.currentSessionOrNull()
                if (restoredSession != null) {
                    Log.d("UserRepository", "Session restored: $restoredSession")
                    return@withContext true
                }

                Log.d("UserRepository", "No active session found")
                false
            } catch (e: Exception) {
                Log.e("UserRepository", "Error checking session: ${e.localizedMessage}", e)
                false
            }
        }
    }

    suspend fun logout() {
        supabaseClient.auth.signOut()
    }

    suspend fun signInWithEmail(emailS: String, passwordS: String) {
        withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.signInWith(Email) {
                    email = emailS
                    password = passwordS
                }
                Log.d("UserRepository", "Sign-in successful for email: $emailS")
            } catch (e: Exception) {
                Log.e("UserRepository", "Sign-in error for email $passwordS: ${e.message}")
                throw e // Rethrow to handle in ViewModel
            }
        }
    }

    suspend fun signUpWithEmail(emails: String, passwords: String) {
        withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.signUpWith(Email) {
                    email = emails
                    password = passwords
                }
                Log.d("UserRepository", "Sign-up successful for email: $emails")
            } catch (e: Exception) {
                Log.e("UserRepository", "Sign-up error for email $emails: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun ensureUserExists(userId: String, email: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("UserCheck", "Checking if user exists: $userId")

                val existingUser = supabaseClient
                    .from("clients")
                    .select(columns = Columns.list("id", "email")) {
                        filter { eq("id", userId) }
                    }
                    .decodeSingleOrNull<User>()

                if (existingUser == null) {
                    Log.d("UserCheck", "User does not exist, inserting: $userId")

                    supabaseClient.from("clients").insert(
                        User(id = userId, email = email)
                    )

                    Log.d("UserCheck", "New user added: $userId")
                } else {
                    Log.d("UserCheck", "User already exists: $userId")
                }
            } catch (e: Exception) {
                Log.e("UserCheck", "Error checking user: ${e.localizedMessage}", e)
            }
        }
    }

    suspend fun getCurrentUser(): User? {
        try {
            // Get user from Supabase DB
            val authUser = supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)
            Log.d("UserFetch", "Auth user: id=${authUser?.id ?: "none"}, email=${authUser?.email ?: "none"}, metadata=${authUser?.userMetadata ?: "none"}")

            if (authUser == null || authUser.email == null) {
                Log.d("UserFetch", "No auth user or email found")
                return null
            }

            // Fetch from clients table
            val dbUser = supabaseClient
                .from("clients")
                .select(columns = Columns.list("id", "name", "email")) {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingleOrNull<User>()

            Log.d("UserFetch", "DB user: ${dbUser?.toString() ?: "none"}")

            // Create User object
            val user = User(
                id = authUser.id,
                name = dbUser?.name ?: authUser.email!!.substringBefore("@"), // Fallback to email prefix
                email = authUser.email!!
            )
            Log.d("UserFetch", "Extracted User: id=${user.id}, name=${user.name}, email=${user.email}")
            return user
        } catch (e: Exception) {
            Log.e("UserFetch", "Error retrieving user: ${e.localizedMessage}", e)
            return null
        }
    }

    suspend fun getCurrentClient(): User? {
        try {
            // Get current session's user
            val authUser = supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)
            Log.d("ClientFetch", "Session user: id=${authUser?.id ?: "none"}, email=${authUser?.email ?: "none"}")

            if (authUser == null) {
                Log.d("ClientFetch", "No auth user found")
                return null
            }

            // Query clients table
            val client = supabaseClient
                .from("clients")
                .select(columns = Columns.list("id", "name", "email")) {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingleOrNull<User>()

            Log.d("ClientFetch", "Client from DB: ${client?.toString() ?: "none"}")
            return client
        } catch (e: Exception) {
            Log.e("ClientFetch", "Error fetching client: ${e.localizedMessage}", e)
            return null
        }
    }

    suspend fun insertUserInClients() {
        try {
            val authUser = supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)
            Log.d("ClientInsert", "Session user: id=${authUser?.id ?: "none"}, email=${authUser?.email ?: "none"}")

            if (authUser == null || authUser.email == null) {
                Log.d("ClientInsert", "No auth user or email found")
                return
            }

            Log.d("ClientInsert", "Found")

            // Check if user already exists in clients table
            val existingClient = supabaseClient.from("clients")
                .select {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingleOrNull<User>()

            if (existingClient != null) {
                Log.d("ClientInsert", "Client already exists: id=${authUser.id}, email=${authUser.email}")
                return
            }

            val client = User(
                id = authUser.id,
                name = null,
                email = authUser.email!!
            )

            supabaseClient.from("clients").insert(client)
            Log.d("ClientInsert", "Inserted client: id=${authUser.id}, email=${authUser.email}")
        } catch (e: Exception) {
            Log.e("ClientInsert", "Error inserting client: ${e.localizedMessage}", e)
            throw e
        }
    }

    @Deprecated("Use hasActiveSession instead")
    suspend fun checkUserSession(): Boolean {
        val session = supabaseClient.auth.currentSessionOrNull()
        Log.d("UserRepository", "Session after redirect: $session")
        val user = supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)
        Log.d("UserRepository", "User after redirect: $user")
        return session != null
    }

    suspend fun updateAuthUser(name: String) {
        try {
            Log.d("UserRepository", "Updating user metadata: name=$name")
            val metadata = buildJsonObject {
                put("name", name)
            }
            Log.d("UserRepository", "Metadata JsonObject: $metadata")
            val user = supabaseClient.auth.updateUser {
                data { metadata }
            }
            Log.d("UserRepository", "User metadata updated successfully")
            Log.d("UserRepository", "$user")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating user metadata: ${e.localizedMessage}", e)
            throw e // Let ViewModel handle
        }
    }

    suspend fun updateClientsUser(name: String) {
        try {
            Log.d("UserRepository", "Updating clients table: name=$name")
            val authUser = supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)
            Log.d("UserRepository", "Session user: id=${authUser?.id ?: "none"}")

            if (authUser == null) {
                Log.d("UserRepository", "No auth user found")
                throw Exception("No authenticated user")
            }

            supabaseClient.from("clients").update(
                {
                    set("name", name)
                }
            ) {
                filter {
                    eq("id", authUser.id)
                }
            }

            Log.d("UserRepository", "Clients table updated successfully: id=${authUser.id}, name=$name")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating clients table: ${e.localizedMessage}", e)
            throw e // Let ViewModel handle
        }
    }

    suspend fun doesEmailExistInClients(email: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dbUser = supabaseClient
                    .from("clients")
                    .select(columns = Columns.list("id", "name", "email")) {
                        filter {
                            eq("email", email)
                        }
                    }
                    .decodeSingleOrNull<User>()
                val exists = dbUser != null
                Log.d("UserRepository", "Email $email exists in clients: $exists")
                exists
            } catch (e: Exception) {
                Log.e("UserRepository", "Error checking email in clients: ${e.localizedMessage}", e)
                false
            }
        }
    }

    suspend fun resetPassword(email: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if email exists in clients table
                val userExists = doesEmailExistInClients(email)
                if (!userExists) {
                    Log.d("UserRepository", "Email not found in clients: $email")
                    return@withContext false
                }

                // Send reset email
                supabaseClient.auth.resetPasswordForEmail(email)
                Log.d("UserRepository", "Password reset email sent for: $email")
                true
            } catch (e: Exception) {
                Log.e("UserRepository", "Error in resetPassword: ${e.localizedMessage}", e)
                false
            }
        }
    }

    suspend fun verifyRecoveryOtp(email: String, token: String) {
        return withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email,
                    token = token
                )
                Log.d("UserRepository", "Recovery OTP verified for email: $email")
            } catch (e: Exception) {
                Log.e("UserRepository", "Error verifying recovery OTP: ${e.localizedMessage}", e)
                throw e
            }
        }
    }

    suspend fun updatePassword(newPassword: String) {
        return withContext(Dispatchers.IO) {
            try {
                supabaseClient.auth.updateUser {
                    password = newPassword
                }
                Log.d("UserRepository", "Password updated")
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating password: ${e.localizedMessage}", e)
                throw e
            }
        }
    }

    suspend fun resetPasswordFinal(newPassword: String) {
        supabaseClient.auth.updateUser {
            password = newPassword
        }
    }

    suspend fun verifyEmailOtp(email: String, token: String) {
        supabaseClient.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email,
            token = token
        )
    }

    suspend fun sendPasswordResetEmail(email2: String) {
        supabaseClient.auth.signInWith(OTP) {
            email = email2
        }
    }

    suspend fun exchangeCodeForSession(code: String) {
        withContext(Dispatchers.IO) {
            supabaseClient.auth.exchangeCodeForSession(code)
            Log.d("UserRepository", "Session exchanged for code=$code")
        }
    }

    suspend fun signInHELP(): Boolean {
        return try {
            supabaseClient.auth.signInWith(Github)
            true
        } catch (e: Exception) {
            false
        }
    }
}