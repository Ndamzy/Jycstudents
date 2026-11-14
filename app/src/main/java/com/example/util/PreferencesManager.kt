package com.example.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

private val Context.dataStore by preferencesDataStore(name = "jy_preferences")

class PreferencesManager(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_mode")

  val themeMode: Flow<AppThemeMode> =
    context.dataStore.data.map { prefs ->
      when (prefs[themeKey]) {
        "LIGHT" -> AppThemeMode.LIGHT
        "DARK" -> AppThemeMode.DARK
        else -> AppThemeMode.SYSTEM
      }
    }

  suspend fun setThemeMode(mode: AppThemeMode) {
    context.dataStore.edit { prefs -> prefs[themeKey] = mode.name }
  }
}
