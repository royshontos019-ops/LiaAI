package com.Lia.assistant.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Lia.assistant.Forge
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.PersonalityRepository
import com.Lia.assistant.voice.ChatPromptBuilder
import com.Lia.assistant.voice.ChatReplyResult
import com.Lia.assistant.voice.ChatTurn
import com.Lia.assistant.voice.ForgeIntent
import com.Lia.assistant.voice.ForgeRequest
import com.Lia.assistant.voice.GeminiTextClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Typed chat state. History lives in memory only and is gone when the chat screen is left. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private var nextId = 1L
    private var job: Job? = null

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _isTyping.value) return
        _messages.update { it + ChatMessage(nextId++, isUser = true, text = text) }
        respondTo(text)
    }

    /** Drops this assistant message (and anything after it) and asks again for the last user message. */
    fun retry(messageId: Long) {
        if (_isTyping.value) return
        val list = _messages.value
        val index = list.indexOfFirst { it.id == messageId }
        if (index < 0 || list[index].isUser) return
        val kept = list.subList(0, index).toList()
        val lastUser = kept.lastOrNull { it.isUser } ?: return
        _messages.value = kept
        respondTo(lastUser.text)
    }

    fun finishReveal(id: Long) {
        _messages.update { list -> list.map { if (it.id == id && it.animate) it.copy(animate = false) else it } }
    }

    private fun respondTo(userText: String) {
        job = viewModelScope.launch {
            _isTyping.value = true
            try {
                val forge = ForgeIntent.extract(userText)
                if (forge != null && withContext(Dispatchers.IO) { startForge(forge) }) {
                    addAssistant("Opening the Forge …", MessageKind.FORGE, animate = false)
                    return@launch
                }
                // Forge not available (or not a build request): answer normally.
                val history = _messages.value
                    .filter { it.kind == MessageKind.NORMAL }
                    .map { ChatTurn(it.isUser, it.text) }
                val result = withContext(Dispatchers.IO) {
                    val personality = PersonalityRepository.getPersonality(appContext)
                    val language = PersonalityRepository.getLanguage(appContext)
                    val name = PersonalityRepository.getAssistantName(appContext)
                    GeminiTextClient.reply(
                        apiKey = ApiKeyStore.getKey(appContext),
                        systemInstruction = ChatPromptBuilder.build(personality, language, name),
                        history = history,
                    )
                }
                when (result) {
                    is ChatReplyResult.Success -> addAssistant(result.text, MessageKind.NORMAL, animate = true)
                    is ChatReplyResult.Error -> addAssistant(result.message, MessageKind.ERROR, animate = false)
                }
            } finally {
                _isTyping.value = false
            }
        }
    }

    private fun startForge(request: ForgeRequest): Boolean {
        val args = JSONObject().put("request", request.prompt)
        request.topic?.let { args.put("topic", it) }
        return Forge.start(appContext, args)
    }

    private fun addAssistant(text: String, kind: MessageKind, animate: Boolean) {
        _messages.update { it + ChatMessage(nextId++, isUser = false, text = text, kind = kind, animate = animate) }
    }
}
