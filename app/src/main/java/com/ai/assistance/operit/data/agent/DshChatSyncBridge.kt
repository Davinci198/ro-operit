package com.ai.assistance.operit.data.agent

import android.content.Context
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

/**
 * Bridges the DshBrain sync channel with the Operit chat:
 * - Outbound: [onOutgoingChatMessage] forwards every user message sent from the
 *   Dev Chat into the DSH session (visible in the DSH Web UI).
 * - Inbound: registers [DshBrain.onDshMessageReceived] so messages written by the
 *   DSH Web UI into the shared session file are injected into the main conversation.
 *
 * All operations are best-effort: DSH may not be installed/running, in which case
 * calls are cheap no-ops.
 */
object DshChatSyncBridge {
    private const val TAG = "DshChatSyncBridge"
    private const val SOURCE_OPERIT_DEV_CHAT = "operit_dev_chat"
    private const val SENDER_DSH = "dsh"

    @Volatile
    private var started = false

    /**
     * Install inbound + outbound listeners. Safe to call multiple times.
     */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val brain = DshBrain.getInstance(context)

        // Inbound: DSH Web UI -> Operit conversation
        brain.onDshMessageReceived = { syncMessage ->
            injectIntoConversation(context, syncMessage)
        }

        AppLogger.i(TAG, "DSH chat sync bridge started")
    }

    /**
     * Outbound hook: forward a user message to the DSH session.
     * Called from MessageProcessingDelegate after the user message is persisted.
     */
    fun onOutgoingChatMessage(context: Context, chatId: String, content: String) {
        if (content.isBlank()) return
        val brain = DshBrain.getInstance(context)
        if (!brain.isActive()) return

        AppLogger.d(TAG, "Forwarding user message to DSH (chatId=$chatId, len=${content.length})")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val ok = brain.syncMessage(SOURCE_OPERIT_DEV_CHAT, content, "user")
                if (!ok) {
                    AppLogger.w(TAG, "Failed to forward user message to DSH")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "DSH outbound sync error", e)
            }
        }
    }

    private fun injectIntoConversation(context: Context, syncMessage: DshBrain.SyncMessage) {
        try {
            val mainCore = ChatRuntimeHolder.getInstance(context).getCore(ChatRuntimeSlot.MAIN)
            val historyDelegate = mainCore.getChatHistoryDelegate()
            val chatId = historyDelegate.currentChatId.value ?: run {
                AppLogger.d(TAG, "No active chat; dropping incoming DSH message")
                return
            }
            val message = ChatMessage(
                sender = SENDER_DSH,
                content = syncMessage.content,
                roleName = "DSH",
                displayMode = ChatMessageDisplayMode.NORMAL
            )
            historyDelegate.addMessageToChatAsync(message, chatId)
            AppLogger.d(TAG, "Injected DSH message into chat $chatId (${syncMessage.content.take(40)}...)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to inject DSH message into conversation", e)
        }
    }
}
