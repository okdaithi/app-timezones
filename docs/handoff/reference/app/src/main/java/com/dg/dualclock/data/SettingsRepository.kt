package com.dg.dualclock.data

// REFERENCE, NOT COMPILED IN THIS PACKAGE.

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dg.dualclock.core.CallHours
import com.dg.dualclock.core.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class Settings(val openHour: Int = 8, val closeHour: Int = 21, val primary: City = City.PERTH) {
    val hours: CallHours get() = CallHours(openHour, closeHour)
}

class SettingsRepository(private val context: Context) {
    private val OPEN = intPreferencesKey("open_hour")
    private val CLOSE = intPreferencesKey("close_hour")
    private val PRIMARY = stringPreferencesKey("primary")

    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(p[OPEN] ?: 8, p[CLOSE] ?: 21, City.valueOf(p[PRIMARY] ?: City.PERTH.name))
    }

    suspend fun current(): Settings = settings.first()

    /** Caller must validate open < close (CallHours init enforces it) and then call refreshAll(context). */
    suspend fun save(s: Settings) {
        s.hours // validates
        context.settingsStore.edit {
            it[OPEN] = s.openHour
            it[CLOSE] = s.closeHour
            it[PRIMARY] = s.primary.name
        }
    }
}
