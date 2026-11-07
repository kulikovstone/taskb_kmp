package com.mkulikov.taskb.feature.settings.api.repository

import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import kotlinx.coroutines.flow.Flow

interface IAppLocationRepository {
    fun observeLocation(): Flow<AppLocation?>

    suspend fun searchLocations(query: String): List<AppLocation>

    suspend fun setLocation(location: AppLocation?)
}
