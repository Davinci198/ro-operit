package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 模型健康扫描 — 对端点上的每个模型探测一次，测量真实延迟。
 *
 * Health scan — probes every model on an endpoint once and measures the real
 * latency of the round trip. Discovery uses the existing `getModelsList`
 * implementation; this class only adds the probe.
 */

/** 延迟标签，例如 "1.2s"。唯一的格式化实现，避免各处重复。
 * Latency label, e.g. "1.2s". The single formatting implementation, so the
 * number is never formatted two different ways in two places. */
private fun formatLatency(latencyMs: Long): String = "%.1fs".format(latencyMs / 1000.0)

/** 单个模型的探测结果 / Result of probing one model. */
data class ModelHealth(
    val modelId: String,
    val status: ModelHealthStatus,
    val latencyMs: Long,
    val detail: String? = null
) {
    /** 延迟标签，例如 "1.2s" / Latency label, e.g. "1.2s". */
    val latencyLabel: String
        get() = formatLatency(latencyMs)
}

/** 探测状态 / Probe outcome. */
enum class ModelHealthStatus {
    /** 模型正常应答 / The model answered. */
    OK,

    /** 模型返回错误 / The model returned an error. */
    FAIL,

    /** 探测超时 / The probe did not finish in time. */
    TIMEOUT
}

/** 扫描摘要，用于 UI 汇总条 / Scan summary for the UI header. */
data class ModelScanSummary(
    val total: Int,
    val working: Int,
    val broken: Int,
    val averageLatencyMs: Long
) {
    /** 平均延迟标签，例如 "1.9s" / Average-latency label, e.g. "1.9s". */
    val averageLatencyLabel: String
        get() = formatLatency(averageLatencyMs)

    /** 汇总行，例如 "5 din 8 verificate · 1.9s medie". */
    fun summaryLine(template: String): String =
        template.format(working, total, averageLatencyLabel)

    companion object {
        /** 从探测结果计算摘要 / Derive the summary from probe results. */
        fun of(results: List<ModelHealth>): ModelScanSummary {
            val working = results.count { it.status == ModelHealthStatus.OK }
            val measured = results.filter { it.status == ModelHealthStatus.OK && it.latencyMs > 0 }
            val average = if (measured.isEmpty()) 0L else measured.sumOf { it.latencyMs } / measured.size
            return ModelScanSummary(
                total = results.size,
                working = working,
                broken = results.size - working,
                averageLatencyMs = average
            )
        }
    }
}

/** 探测一次调用产出的结果 / What one probe call produced. */
sealed interface ModelProbeResult {
    /** 模型应答了 / The model answered. */
    data class Answered(val latencyMs: Long) : ModelProbeResult

    /** 调用抛错 / The call threw. */
    data class Failed(val message: String) : ModelProbeResult

    /** 超过超时 / The call exceeded the timeout. */
    data class TimedOut(val timeoutMs: Long) : ModelProbeResult
}

/**
 * 模型健康扫描器 / Model health scanner.
 *
 * [discover] 只做发现，[probe] 只做单模型探测，[scan] 组合两者。这样每个部分
 * 都能被单独测试，UI 也能只调用需要的那一步。
 *
 * [discover] does discovery only, [probe] probes a single model, and [scan]
 * composes the two. Each part is therefore testable alone, and the UI can call
 * just the step it needs.
 *
 * 注意 / Note: 本类不改变调用方选中的模型。扫描只报告哪些模型可用，从不静默
 * 替换选择 —— 替换是调用方的决定。
 * This class never changes the caller's selected model. A scan reports which
 * models work; silently swapping the selection is the caller's decision, not
 * this scanner's.
 */
class ModelHealthScanner(
    private val context: Context,
    private val modelConfigManager: ModelConfigManager
) {
    /**
     * 探测单个模型 / Probe a single model.
     *
     * 用 [config] 的端点与凭据，但把模型名换成 [modelId]，然后发一条很短的
     * 非流式消息并计时。
     *
     * Uses [config] for endpoint and credentials, overriding only the model name
     * with [modelId], then sends one very short non-streaming message and times
     * it.
     */
    suspend fun probe(
        config: ModelConfigData,
        modelId: String,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS
    ): ModelProbeResult {
        val result = withTimeoutOrNull(timeoutMs) {
            val startedAt = System.currentTimeMillis()
            try {
                val service = AIServiceFactory.createService(
                    config = config.copy(modelName = modelId),
                    modelConfigManager = modelConfigManager,
                    context = context
                )
                val parameters = modelConfigManager.getModelParametersForConfig(config.id)
                service.sendMessage(
                    context,
                    listOf(PromptTurn(kind = PromptTurnKind.USER, content = PROBE_PROMPT)),
                    parameters,
                    stream = false,
                    // A probe must report the model's real answer, not a retry
                    // sequence's eventual success: retries would hide a broken
                    // model behind a working one and inflate the latency.
                    enableRetry = false
                ).collect { }
                ModelProbeResult.Answered(System.currentTimeMillis() - startedAt)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ModelProbeResult.Failed(e.message ?: e::class.java.simpleName)
            }
        }
        return result ?: ModelProbeResult.TimedOut(timeoutMs)
    }

    /**
     * 扫描一组模型 / Scan a list of models.
     *
     * 并发上限 [concurrency]（夹在 1..10）。[onProgress] 每完成一个模型调用一次，
     * 便于 UI 流式显示。
     *
     * Probes at most [concurrency] models at a time (clamped to 1..10).
     * [onProgress] fires once per finished model so the UI can stream results.
     */
    suspend fun scan(
        config: ModelConfigData,
        models: List<ModelOption>,
        concurrency: Int = DEFAULT_CONCURRENCY,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
        onProgress: suspend (ModelHealth) -> Unit = {}
    ): List<ModelHealth> = withContext(Dispatchers.IO) {
        if (models.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(concurrency.coerceIn(1, 10))
        val collected = Collections.synchronizedList(mutableListOf<ModelHealth>())
        coroutineScope {
            models.map { model ->
                async {
                    semaphore.withPermit {
                        val health = probe(config, model.id, timeoutMs).toHealth(model.id)
                        collected.add(health)
                        onProgress(health)
                    }
                }
            }.awaitAll()
        }
        collected.toList()
    }

    /**
     * 发现并扫描 / Discover, then scan.
     *
     * 发现由调用方已有的 `getModelsList` 完成，本方法只探测。返回 null 表示
     * 发现失败（此时不报健康，而不是把所有模型标成故障）。
     *
     * Discovery is delegated to the caller's existing `getModelsList`; this only
     * probes. A null result means discovery itself failed, in which case no
     * health is reported rather than marking every model broken.
     */
    suspend fun discoverAndScan(
        config: ModelConfigData,
        concurrency: Int = DEFAULT_CONCURRENCY,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
        onProgress: suspend (ModelHealth) -> Unit = {}
    ): List<ModelHealth>? {
        val service = AIServiceFactory.createService(
            config = config,
            modelConfigManager = modelConfigManager,
            context = context
        )
        val discovered = service.getModelsList(context).getOrElse { error ->
            AppLogger.w(TAG, "Model discovery failed for ${config.name}", error)
            return null
        }
        return scan(config, discovered, concurrency, timeoutMs, onProgress)
    }

    private fun ModelProbeResult.toHealth(modelId: String): ModelHealth = when (this) {
        is ModelProbeResult.Answered -> ModelHealth(modelId, ModelHealthStatus.OK, latencyMs)
        is ModelProbeResult.Failed -> ModelHealth(modelId, ModelHealthStatus.FAIL, 0L, message)
        is ModelProbeResult.TimedOut ->
            ModelHealth(modelId, ModelHealthStatus.TIMEOUT, 0L, "timeout ${timeoutMs}ms")
    }

    companion object {
        private const val TAG = "ModelHealthScanner"

        /** 短探测提示词，只验证链路是否应答 / Short probe prompt: proves the round trip answers. */
        const val PROBE_PROMPT = "ping"

        /** 单模型探测超时 / Per-model probe timeout. */
        const val DEFAULT_PROBE_TIMEOUT_MS = 30_000L

        /** 默认并发 / Default concurrency. */
        const val DEFAULT_CONCURRENCY = 5
    }
}
