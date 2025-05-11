package com.example.senefavores.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.status.SessionSource.Storage
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseManagement @Inject constructor() {

    private val SUPABASE_URL = "https://zvcbntvosigvfchmoqql.supabase.co"
    private val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp2Y2JudHZvc2lndmZjaG1vcXFsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDY5NDk4OTcsImV4cCI6MjA2MjUyNTg5N30._8VsLubQ9_3053tDqsZj4vOwDJJ8l75Thn6p_AGFhBA"

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {

            install(Auth) {
                flowType = FlowType.PKCE
                host = "com.example.atlas"
                scheme = "atlas"
                //defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)

        }
    }
}