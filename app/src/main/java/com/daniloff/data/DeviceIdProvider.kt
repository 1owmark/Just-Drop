package com.daniloff.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID


private val Context.dataStore by preferencesDataStore(
    name = "device_preferences"
)

class DeviceIdProvider(
    private val context: Context
) {
    private val deviceId = stringPreferencesKey("device_id")

    suspend fun getDeviceId(): String {
        val preferences = context.dataStore.data.first()
        val savedId = preferences[deviceId]

        if (savedId != null) {
            return savedId
        } else {
            val newId = UUID.randomUUID().toString()
            context.dataStore.edit { preferences ->
                preferences[deviceId] = newId
            }

            return newId
        }
    }
}