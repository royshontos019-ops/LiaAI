package com.Lia.assistant.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Lia.assistant.Forge
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.ConversationStore
import com.Lia.assistant.data.PersonalityRepository
import com.Lia.assistant.data.StoredChat
import com.Lia.assistant.data.StoredMessage
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

/** Typed chat state. Every message is also saved on the phone, unless the user switched that off in Privacy. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private var nextId = 1L
    private var job: Job? = null

    private var conversationId: String? = null
    private var opened = false
    private var autoSent = false

    /** Opens a saved conversation, or starts a fresh one when [id] is null. Only the first call counts. */
    fun open(id: String?) {
        if (opened) return
        opened = true
        if (id == null) return
        viewModelScope.launch {
            val chat = withContext(Dispatchers.IO) { ConversationStore.load(appContext, id) } ?: return@launch
            conversationId = chat.id
            _messages.value = chat.messages.mapIndexed { index, m ->
                ChatMessage(index + 1L, m.isUser, m.text, kindFromName(m.kind))
            }
            nextId = chat.messages.size + 1L
        }
    }

    /** Sends [raw] once (used by the Quick actions tile). */
    fun sendOnce(raw: String) {
        if (autoSent) return
        autoSent = true
        send(raw)
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _isTyping.value) return
        _messages.update { it + ChatMessage(nextId++, isUser = true, text = text) }
        persist()
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
        persist()
    }

    private fun kindFromName(name: String): MessageKind =
        MessageKind.entries.firstOrNull { it.name == name } ?: MessageKind.NORMAL

    /** Saves the whole conversation in the background (nothing happens when saving is off). */
    private fun persist() {
        if (!ConversationStore.isSavingEnabled(appContext)) return
        val snapshot = _messages.value
        if (snapshot.isEmpty()) return
        val id = conversationId ?: ConversationStore.newId().also { conversationId = it }
        val title = snapshot.firstOrNull { it.isUser }?.text
            ?.replace('\n', ' ')?.trim()?.take(48).orEmpty()
            .ifBlank { "Conversation" }
        val stored = snapshot.map { StoredMessage(it.isUser, it.text, it.kind.name) }
        ConversationStore.saveAsync(appContext, StoredChat(id, title, System.currentTimeMillis(), stored))
    }
}
