package com.bus.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "session_store")

data class SessionState(
    val token: String? = null,
    val role: String? = null,
    val userId: Int? = null,
    val login: String? = null
)

class SessionDataStore(private val context: Context) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val ROLE = stringPreferencesKey("role")
        val USER_ID = intPreferencesKey("user_id")
        val LOGIN = stringPreferencesKey("login")
    }

    val sessionFlow: Flow<SessionState> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map(::mapPreferencesToState)

    suspend fun saveSession(token: String, role: String, userId: Int?, login: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.ROLE] = role
            login.let { prefs[Keys.LOGIN] = it }
            if (userId != null) prefs[Keys.USER_ID] = userId else prefs.remove(Keys.USER_ID)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    private fun mapPreferencesToState(prefs: Preferences): SessionState = SessionState(
        token = prefs[Keys.TOKEN],
        role = prefs[Keys.ROLE],
        userId = prefs[Keys.USER_ID],
        login = prefs[Keys.LOGIN]
    )
}
