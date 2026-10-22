package com.example.fishspot.feature.chat

import androidx.lifecycle.ViewModel

import com.google.firebase.auth.FirebaseAuth
import com.example.fishspot.core.theme.FishPoruka
import com.google.firebase.firestore.FieldValue
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
                        val responseText = bezbedanOdgovor(doc)
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
                        val resp = bezbedanOdgovor(lastDoc)
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

    // Pitanje i brojač pitanja (BotLimit/{uid}) se upisuju zajedno; pravila baze dozvoljavaju
    // najviše PITANJA_DNEVNO pitanja u 24 sata, jer se svaki Gemini odgovor plaća
    fun sendQuestionToBot(text: String) {
        val userId = auth.currentUser?.uid ?: return
        val pitanje = text.trim().take(MAKS_DUZINA_PITANJA)
        if (pitanje.isBlank()) return
        _isThinking.value = true

        val porukaRef = db.collection("bot_chats").document(userId).collection("messages").document()
        val limitRef = db.collection("BotLimit").document(userId)

        db.runTransaction { tx ->
            val limit = tx.get(limitRef)
            val pocetak = limit.getTimestamp("pocetak")?.toDate()?.time
            val broj = limit.getLong("broj") ?: 0L
            val novoRazdoblje = pocetak == null || System.currentTimeMillis() - pocetak > DAN_MS

            if (!novoRazdoblje && broj >= PITANJA_DNEVNO) {
                throw IllegalStateException("limit")
            }
            tx.set(porukaRef, mapOf("prompt" to pitanje, "timestamp" to System.currentTimeMillis()))
            tx.set(
                limitRef,
                if (novoRazdoblje) {
                    mapOf("broj" to 1L, "pocetak" to FieldValue.serverTimestamp(), "poslednja" to porukaRef.id)
                } else {
                    mapOf("broj" to broj + 1, "pocetak" to limit.getTimestamp("pocetak"), "poslednja" to porukaRef.id)
                }
            )
        }.addOnFailureListener { e ->
            _isThinking.value = false
            FishPoruka.prikazi(
                if (e.message == "limit" || e.cause?.message == "limit") "Deda Pera se umorio: danas si postavio $PITANJA_DNEVNO pitanja. Vrati se sutra!"
                else "Pitanje nije poslato. Proveri internet i pokušaj ponovo.",
                dugo = true
            )
        }
    }

    // Odgovor se prikazuje tek kad ga server proveri (funkcija proveriOdgovorDedePere).
    // Stari odgovori (pre uvođenja provere) se prikazuju, osim onih koji otkrivaju uputstva.
    private fun bezbedanOdgovor(doc: com.google.firebase.firestore.DocumentSnapshot): String? {
        val odgovor = doc.getString("response") ?: return null
        if (doc.getBoolean("provereno") == true) return odgovor
        val vreme = doc.getLong("timestamp") ?: 0L
        if (vreme >= POCETAK_PROVERE) return null
        val t = odgovor.lowercase()
        return if ("pravila tvog karaktera" in t || "ti si deda pera, stari" in t) ODBIJANJE else odgovor
    }

    companion object {
        private const val POCETAK_PROVERE = 1792684800000L // 22.10.2026. 18:00
        private const val ODBIJANJE = "Bistro, sinko! Deda Pera priča samo o pecanju: pribor, mamci, mesta, riba i lovostaj. " +
            "Te druge stvari ostavi nekom drugom, a mene pitaj nešto o ribolovu pa da ti pomognem!"
        const val PITANJA_DNEVNO = 30
        const val MAKS_DUZINA_PITANJA = 500
        private const val DAN_MS = 24 * 60 * 60 * 1000L
    }
}