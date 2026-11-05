package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.api.chat.llmprovider.ModelHealth
import com.ai.assistance.operit.api.chat.llmprovider.ModelHealthStatus
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.modelScanDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "model_scan_results")

/**
 * 模型扫描结果持久化 / Persists model-scan results.
 *
 * 按配置 id 存储最近一次扫描的健康数据，这样重新打开设置界面时不必重扫。
 * Stores the most recent scan's health data per config id, so reopening the
 * settings screen does not force a rescan.
 *
 * 只存结果，不存用户选择。扫描从不改写 [com.ai.assistance.operit.data.model.ModelConfigData]
 * ——本类也不碰它。
 * Results only; never the user's selection. A scan never rewrites the stored
 * model config, and neither does this class.
 */
class ModelScanPreferences private constructor(private val context: Context) {

    /**
     * 磁盘格式 / On-disk shape.
     *
     * 状态存为字符串而非序号：新增状态值不会让旧数据解码错位。
     * Status is stored as a name rather than an ordinal, so adding a new status
     * value cannot silently re-map old records.
     */
    @Serializable
    private data class StoredModelHealth(
        val id: String,
        val status: String,
        val latencyMs: Long,
        val detail: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 读取某配置的扫描结果 / Read the scan results for one config.
     *
     * 没有记录或数据损坏时返回空列表——调用方据此显示「尚未扫描」，而不是
     * 假装所有模型都正常或都故障。
     * Returns an empty list when there is no record or the record is corrupt;
     * the caller then shows "not scanned yet" rather than pretending every
     * model is fine or every model is broken.
     */
    suspend fun loadResults(configId: String): List<ModelHealth> {
        val raw = context.modelScanDataStore.data.map { it[keyFor(configId)] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredModelHealth>>(raw) }
            .map { stored -> stored.map { it.toDomain() } }
            .getOrElse { error ->
                AppLogger.w(TAG, "Discarding unreadable scan record for ${configId.take(8)}", error)
                emptyList()
            }
    }

    /** 保存扫描结果 / Save scan results for one config. */
    suspend fun saveResults(configId: String, results: List<ModelHealth>) {
        val raw = json.encodeToString(results.map { it.toStored() })
        context.modelScanDataStore.edit { it[keyFor(configId)] = raw }
    }

    /** 清除某配置的扫描结果 / Clear the scan results for one config. */
    suspend fun clearResults(configId: String) {
        context.modelScanDataStore.edit { it.remove(keyFor(configId)) }
    }

    private fun keyFor(configId: String): Preferences.Key<String> =
        stringPreferencesKey("scan_$configId")

    private fun ModelHealth.toStored(): StoredModelHealth =
        StoredModelHealth(id = modelId, status = status.name, latencyMs = latencyMs, detail = detail)

    private fun StoredModelHealth.toDomain(): ModelHealth =
        ModelHealth(
            modelId = id,
            status = ModelHealthStatus.entries.firstOrNull { it.name == status }
                ?: ModelHealthStatus.FAIL,
            latencyMs = latencyMs,
            detail = detail
        )

    companion object {
        private const val TAG = "ModelScanPreferences"

        @Volatile
        private var INSTANCE: ModelScanPreferences? = null

        fun getInstance(context: Context): ModelScanPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelScanPreferences(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
