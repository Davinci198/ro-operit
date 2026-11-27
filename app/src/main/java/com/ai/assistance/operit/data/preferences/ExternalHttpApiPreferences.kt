package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.externalHttpApiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "external_http_api_preferences"
)

data class ExternalHttpApiConfig(
    val enabled: Boolean,
    val port: Int,
    val bearerToken: String
)

class ExternalHttpApiPreferences private constructor(private val context: Context) {

    /**
     * In-memory cache of the preferences snapshot.
     *
     * The synchronous getters are called by the external HTTP servers on **every request**
     * (`WebChatHttpBridge.requireBearerToken`, `ExternalChatHttpServer.requireBearerToken`). Each of
     * those calls used to be a `runBlocking { data.first() }`, i.e. every request blocked a NanoHTTPD
     * worker thread on a DataStore read. Only the first read blocks now; afterwards the snapshot
     * stays in memory and every write refreshes it immediately.
     */
    @Volatile
    private var cachedConfig: ExternalHttpApiConfig? = null

    val enabledFlow: Flow<Boolean> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_ENABLED] ?: false
        }

    val portFlow: Flow<Int> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_PORT] ?: DEFAULT_PORT
        }

    val bearerTokenFlow: Flow<String> =
        context.externalHttpApiDataStore.data.map { preferences ->
            preferences[KEY_BEARER_TOKEN].orEmpty()
        }

    suspend fun setEnabled(enabled: Boolean) {
        cachedConfig = context.externalHttpApiDataStore
            .edit { preferences -> preferences[KEY_ENABLED] = enabled }
            .toConfig()
    }

    suspend fun setPort(port: Int) {
        require(isValidPort(port)) { "Invalid port: $port" }
        cachedConfig = context.externalHttpApiDataStore
            .edit { preferences -> preferences[KEY_PORT] = port }
            .toConfig()
    }

    suspend fun ensureBearerToken(): String {
        val existing = getConfig().bearerToken
        if (existing.isNotBlank()) {
            return existing
        }
        val generated = generateBearerToken()
        setBearerToken(generated)
        return generated
    }

    suspend fun resetBearerToken(): String {
        val generated = generateBearerToken()
        setBearerToken(generated)
        return generated
    }

    suspend fun setBearerToken(token: String) {
        cachedConfig = context.externalHttpApiDataStore
            .edit { preferences -> preferences[KEY_BEARER_TOKEN] = token }
            .toConfig()
    }

    suspend fun getConfig(): ExternalHttpApiConfig {
        val preferences = context.externalHttpApiDataStore.data.first()
        return preferences.toConfig().also { cachedConfig = it }
    }

    /** Read the snapshot synchronously; only the first call in a process, when no snapshot exists yet, blocks on DataStore. */
    fun getConfigSync(): ExternalHttpApiConfig {
        return cachedConfig ?: runBlocking { getConfig() }
    }

    fun getEnabled(): Boolean = getConfigSync().enabled

    fun getPort(): Int = getConfigSync().port

    fun getBearerToken(): String = getConfigSync().bearerToken

    private fun Preferences.toConfig(): ExternalHttpApiConfig {
        return ExternalHttpApiConfig(
            enabled = this[KEY_ENABLED] ?: false,
            port = this[KEY_PORT] ?: DEFAULT_PORT,
            bearerToken = this[KEY_BEARER_TOKEN].orEmpty()
        )
    }

    companion object {
        const val DEFAULT_PORT = 8094

        private val KEY_ENABLED = booleanPreferencesKey("external_http_api_enabled")
        private val KEY_PORT = intPreferencesKey("external_http_api_port")
        private val KEY_BEARER_TOKEN = stringPreferencesKey("external_http_api_bearer_token")

        @Volatile
        private var INSTANCE: ExternalHttpApiPreferences? = null

        fun getInstance(context: Context): ExternalHttpApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ExternalHttpApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun isValidPort(port: Int): Boolean {
            return port in 1..65535
        }

        private fun generateBearerToken(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }
    }
}
