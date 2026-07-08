package com.personaliai.chatty

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

enum class ChattyRole { USER, ASSISTANT, AGENT }

data class ChattyMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChattyRole,
    val text: String,
    val createdAt: String = Instant.now().toString(),
    val fileUrl: String? = null,
)

data class ChattyUiState(
    val theme: ChattyTheme? = null,
    val ready: Boolean = false,
    val messages: List<ChattyMessage> = emptyList(),
    val sending: Boolean = false,
    val aiPaused: Boolean = false,
    val error: String? = null,
)

/**
 * Drives a full Chatty conversation: loads bot theme/config, manages the
 * persistent session id, sends messages, and polls for human-agent replies
 * every 4s — the native-SDK equivalent of widget.js's embed iframe lifecycle.
 */
class ChattyViewModel(
    application: Application,
    private val botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    private val hostKey: String = "app",
    private val pollIntervalMs: Long = 4000L,
    private val visitorTimezone: String = "UTC",
    private val onMessage: ((ChattyMessage) -> Unit)? = null,
) : AndroidViewModel(application) {

    private val client = ChattyClient(botId, baseUrl, host)
    private var sessionId: String? = null
    private var lastPollAt: String = Instant.now().toString()

    private val _state = MutableStateFlow(ChattyUiState())
    val state: StateFlow<ChattyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val theme = client.getTheme()
                sessionId = ChattySession.getOrCreateSessionId(application, botId, hostKey)
                val welcome = theme.welcomeMessage?.let {
                    listOf(ChattyMessage(id = "welcome", role = ChattyRole.ASSISTANT, text = it))
                } ?: emptyList()
                _state.update { it.copy(theme = theme, ready = true, messages = welcome) }
                startPolling()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to load Chatty bot") }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(pollIntervalMs)
                val sid = sessionId ?: continue
                try {
                    val res = client.poll(sid, lastPollAt)
                    if (res.messages.isNotEmpty()) {
                        lastPollAt = res.messages.last().createdAt
                        val newMsgs = res.messages.map {
                            ChattyMessage(
                                role = if (it.sender == "agent") ChattyRole.AGENT else ChattyRole.ASSISTANT,
                                text = it.content,
                                createdAt = it.createdAt,
                            )
                        }
                        _state.update { it.copy(messages = it.messages + newMsgs, aiPaused = res.aiPaused) }
                        newMsgs.forEach { onMessage?.invoke(it) }
                    } else {
                        _state.update { it.copy(aiPaused = res.aiPaused) }
                    }
                } catch (_: Exception) {
                    // silent — polling failures shouldn't surface as user-facing errors
                }
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        val sid = sessionId ?: return
        if (trimmed.isEmpty()) return

        _state.update { it.copy(messages = it.messages + ChattyMessage(role = ChattyRole.USER, text = trimmed), sending = true, error = null) }
        viewModelScope.launch {
            try {
                val res = client.sendMessage(sid, trimmed, visitorTimezone)
                _state.update { it.copy(aiPaused = res.aiPaused) }
                if (!res.aiPaused && res.reply.isNotEmpty()) {
                    val reply = ChattyMessage(role = ChattyRole.ASSISTANT, text = res.reply)
                    _state.update { it.copy(messages = it.messages + reply) }
                    onMessage?.invoke(reply)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to send message") }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun sendImage(file: File, mimeType: String, caption: String = "") {
        val sid = sessionId ?: return
        _state.update {
            it.copy(
                messages = it.messages + ChattyMessage(role = ChattyRole.USER, text = caption, fileUrl = file.absolutePath),
                sending = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val res = client.sendMedia(sid, file, mimeType, caption, visitorTimezone)
                _state.update { it.copy(aiPaused = res.aiPaused) }
                if (!res.aiPaused && res.reply.isNotEmpty()) {
                    val reply = ChattyMessage(role = ChattyRole.ASSISTANT, text = res.reply)
                    _state.update { it.copy(messages = it.messages + reply) }
                    onMessage?.invoke(reply)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to send image") }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }
}
