package com.example.fishspot.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveUser(
    val userId: String = "",
    val username: String = "",
    val slikaUrl: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val lastActive: Long = System.currentTimeMillis()
)

data class UserMessage(
    val senderId: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class ActiveUsersViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())
    val activeUsers = _activeUsers.asStateFlow()

    private val _messagesMap = MutableStateFlow<Map<String, List<UserMessage>>>(emptyMap())
    val messagesMap = _messagesMap.asStateFlow()

    private val listeners = mutableMapOf<String, ListenerRegistration>()

    private var mySlikaUrl: String? = null
    private var myUsername: String = "Pecaroš"

    init {
        listenToActiveUsers()
        fetchMyProfileData()
    }

    private fun fetchMyProfileData() {
        val user = auth.currentUser ?: return

        val slikaRef = storage.reference.child("profilne_slike/${user.uid}.jpg")
        slikaRef.downloadUrl.addOnSuccessListener { uri ->
            mySlikaUrl = uri.toString()
        }.addOnFailureListener {
            mySlikaUrl = ""
        }

        myUsername = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
                    ?: "Pecaroš"
    }

    fun updateMyLocationIfSharing(lat: Double, lng: Double) {
        val currentUser = auth.currentUser ?: return
        val activeUser = ActiveUser(
            userId = currentUser.uid,
            username = myUsername,
            slikaUrl = mySlikaUrl ?: "",
            lat = lat,
            lng = lng,
            lastActive = System.currentTimeMillis()
        )
        firestore.collection("ActiveUsers").document(currentUser.uid).set(activeUser)
    }

    fun removeMyLocation() {
        val currentUser = auth.currentUser ?: return
        firestore.collection("ActiveUsers").document(currentUser.uid).delete()
    }

    private fun listenToActiveUsers() {
        firestore.collection("ActiveUsers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val currentTime = System.currentTimeMillis()
                    val users = snapshot.documents.mapNotNull { it.toObject(ActiveUser::class.java) }
                        .filter { currentTime - it.lastActive < 5 * 60 * 1000 }

                    _activeUsers.value = users

                    val myUid = auth.currentUser?.uid ?: return@addSnapshotListener
                    users.forEach { u ->
                        if (u.userId != myUid) {
                            loadPrivateMessages(u.userId)
                        }
                    }
                }
            }
    }

    private fun getChatRoomId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    fun loadPrivateMessages(targetUserId: String) {
        if (listeners.containsKey(targetUserId)) return

        val myUid = auth.currentUser?.uid ?: return
        val roomId = getChatRoomId(myUid, targetUserId)

        val reg = firestore.collection("PrivateChats").document(roomId).collection("Messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val msgs = snapshot.documents.mapNotNull { it.toObject(UserMessage::class.java) }
                    val currentMap = _messagesMap.value.toMutableMap()
                    currentMap[targetUserId] = msgs
                    _messagesMap.value = currentMap
                }
            }
        listeners[targetUserId] = reg
    }

    // MAGIJA: OVO JE BILO ZABORAVLJENO! OVO GASI RAZGOVOR KADA GA ZATVORIŠ PREVLAČENJEM DOLE.
    fun closeChat(targetUserId: String) {
        listeners[targetUserId]?.remove()
        listeners.remove(targetUserId)
        val currentMap = _messagesMap.value.toMutableMap()
        currentMap.remove(targetUserId)
        _messagesMap.value = currentMap
    }

    fun sendPrivateMessage(targetUserId: String, text: String, senderUsername: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (text.isBlank()) return

        val roomId = getChatRoomId(myUid, targetUserId)
        val newMessage = UserMessage(
            senderId = myUid,
            senderUsername = senderUsername,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        firestore.collection("PrivateChats").document(roomId).collection("Messages").add(newMessage)
    }
}