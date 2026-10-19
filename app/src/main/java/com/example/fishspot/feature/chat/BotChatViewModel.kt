package com.example.fishspot.feature.chat

import androidx.lifecycle.ViewModel

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BotChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _messages = MutableStateFlow<List<BotChatMessage>>(emptyList())
    private val _isThinking = MutableStateFlow(false)
    val isThinking = _isThinking.asStateFlow()
    val messages = _messages.asStateFlow()

    init {
        listenToBotChat()
    }

    private fun listenToBotChat() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("bot_chats")
            .document(userId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    val chatList = mutableListOf<BotChatMessage>()

                    for (doc in snapshot.documents) {
                        val promptText = doc.getString("prompt") ?: ""
                        val responseText = doc.getString("response")
                        val timestamp = doc.getLong("timestamp") ?: 0L

                        if (promptText.isNotBlank()) {
                            chatList.add(
                                BotChatMessage(
                                    id = "${doc.id}_user",
                                    text = promptText,
                                    sender = BotSender.USER,
                                    timestamp = timestamp
                                )
                            )
                        }

                        if (!responseText.isNullOrBlank()) {
                            chatList.add(
                                BotChatMessage(
                                    id = "${doc.id}_bot",
                                    text = responseText,
                                    sender = BotSender.BOT,
                                    timestamp = timestamp + 1
                                )
                            )
                        }
                    }

                    // AKO JE ČET PRAZAN: Deda Pera se prvi javlja domaćinskim pozdravom
                    if (chatList.isEmpty()) {
                        chatList.add(
                            BotChatMessage(
                                id = "welcome_deda_pera",
                                text = "Bistro! Ja sam deda Pera – stari alas koji je pola života proveo kraj vode, a drugu polovinu pričajući o ribama. Godinama jurim šarana, smuđa, štuku i sve što ima peraje, pa ako ti treba savet oko pribora, mamaca, mesta ili tehnike – na pravom si mestu. Pitaj slobodno, voda me je svačemu naučila.",
                                sender = BotSender.BOT,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }

                    _messages.value = chatList
                   // PROVERAVAMO DA LI DEDA JOŠ RAZMIŠLJA:
                    if (!snapshot.isEmpty) {
                        val lastDoc = snapshot.documents.last()
                        val resp = lastDoc.getString("response")
                        val status = lastDoc.get("status") as? Map<*, *>
                        val stateStr = status?.get("state") as? String

                        // Ako ima odgovor ILI je upisana greška (ERROR), deda je završio misao
                        if (!resp.isNullOrBlank() || stateStr == "ERROR") {
                            _isThinking.value = false
                        } else if (lastDoc.getString("prompt")?.isNotBlank() == true) {
                            // Ako ima prompt, a nema odgovora ni greške -> još uvek se čeka server
                            _isThinking.value = true
                        }
                    } else {
                        _isThinking.value = false
                    }
                }

            }
    }

    fun sendQuestionToBot(text: String) {
        val userId = auth.currentUser?.uid ?: return
        if (text.isBlank()) return
        _isThinking.value = true
        val newMessage = mapOf(
            "prompt" to text,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("bot_chats")
            .document(userId)
            .collection("messages")
            .add(newMessage)
    }
}