package com.example.atlas.di

import android.content.Context
import com.example.atlas.data.repository.UserRepository
import com.example.atlas.util.LocationCache
import com.example.atlas.util.LocationHelper
import com.example.atlas.util.NetworkChecker
import com.example.senefavores.data.remote.SupabaseManagement
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return SupabaseManagement().supabase
    }

    @Provides
    @Singleton
    fun provideLocationCache(): LocationCache {
        return LocationCache()
    }

    @Provides
    @Singleton
    fun provideLocationHelper(
        @ApplicationContext context: Context,
        locationCache: LocationCache
    ): LocationHelper {
        return LocationHelper(context, locationCache)
    }


    @Provides
    @Singleton
    fun provideUserRepository(supabaseClient: SupabaseClient): UserRepository {
        return UserRepository(supabaseClient)
    }

    @Provides
    @Singleton
    fun provideNetworkChecker(@ApplicationContext context: Context): NetworkChecker {
        return NetworkChecker(context)
    }
}
