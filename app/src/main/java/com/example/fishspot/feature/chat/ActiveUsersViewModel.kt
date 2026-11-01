package com.example.fishspot.feature.chat

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val timestamp: Long = System.currentTimeMillis(),
    val primalacId: String = "" // kome je poruka; po ovome primalac dobija poruke i kad pošiljalac nije na mapi
)

// Jedan razgovor na ekranu Poruke (Razgovori/{moj uid}/sa/{uid})
data class Razgovor(
    val uid: String,
    val ime: String,
    val slika: String,
    val poslednja: String,
    val vreme: Long,
    val neprocitano: Long,
    val odMene: Boolean
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

    // Korisnik koji ti je upravo poslao poruku: mapa za njega otvara balončić
    private val _noviSagovornik = kotlinx.coroutines.flow.MutableSharedFlow<ActiveUser>(extraBufferCapacity = 8)
    val noviSagovornik = _noviSagovornik.asSharedFlow()

    // Razgovor koji treba odmah otvoriti (dodir na obaveštenje o poruci)
    private val _otvoriChat = kotlinx.coroutines.flow.MutableSharedFlow<ActiveUser>(extraBufferCapacity = 2)
    val otvoriChat = _otvoriChat.asSharedFlow()

    private var dolazneRegistracija: ListenerRegistration? = null
    private val slikeKorisnika = mutableMapOf<String, String>()

    private var mySlikaUrl: String? = null
    private var myUsername: String = "Pecaroš"

    // Spisak razgovora (ekran Poruke); upisuje ga server pri svakoj poruci
    private val _razgovori = MutableStateFlow<List<Razgovor>>(emptyList())
    val razgovori = _razgovori.asStateFlow()
    private var razgovoriRegistracija: ListenerRegistration? = null

    init {
        listenToActiveUsers()
        fetchMyProfileData()
        slusajDolaznePoruke()
        slusajRazgovore()
    }

    private fun slusajRazgovore() {
        val mojUid = auth.currentUser?.uid ?: return
        razgovoriRegistracija = firestore.collection("Razgovori").document(mojUid).collection("sa")
            .orderBy("vreme", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Poruke", "Slušanje razgovora", error)
                    return@addSnapshotListener
                }
                _razgovori.value = snapshot?.documents.orEmpty().mapNotNull { d ->
                    Razgovor(
                        uid = d.getString("uid") ?: d.id,
                        ime = d.getString("ime") ?: "Pecaroš",
                        slika = d.getString("slika").orEmpty(),
                        poslednja = d.getString("poslednja").orEmpty(),
                        vreme = d.getLong("vreme") ?: 0L,
                        neprocitano = d.getLong("neprocitano") ?: 0L,
                        odMene = d.getBoolean("odMene") ?: false
                    )
                }
            }
    }

    // Razgovor je otvoren: brojač nepročitanih se vraća na 0
    fun oznaciProcitano(uid: String) {
        val mojUid = auth.currentUser?.uid ?: return
        if ((_razgovori.value.firstOrNull { it.uid == uid }?.neprocitano ?: 0L) == 0L) return
        firestore.collection("Razgovori").document(mojUid).collection("sa").document(uid)
            .update("neprocitano", 0)
    }

    // Sve nove poruke upućene meni, bez obzira da li je pošiljalac na mapi
    private fun slusajDolaznePoruke() {
        val mojUid = auth.currentUser?.uid ?: return
        val pocetak = System.currentTimeMillis()
        dolazneRegistracija = firestore.collectionGroup("Messages")
            .whereEqualTo("primalacId", mojUid)
            .whereGreaterThan("timestamp", pocetak)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Poruke", "Slušanje dolaznih poruka", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { promena ->
                        val posiljalac = promena.document.getString("senderId") ?: return@forEach
                        val ime = promena.document.getString("senderUsername") ?: "Pecaroš"
                        loadPrivateMessages(posiljalac)
                        saSlikom(posiljalac, ime) { _noviSagovornik.tryEmit(it) }
                    }
            }
    }

    // Otvaranje razgovora sa obaveštenja (pošiljalac možda nije na mapi)
    fun otvoriChatSa(uid: String, ime: String) {
        loadPrivateMessages(uid)
        saSlikom(uid, ime) { _otvoriChat.tryEmit(it) }
    }

    private fun saSlikom(uid: String, ime: String, gotovo: (ActiveUser) -> Unit) {
        slikeKorisnika[uid]?.let { return gotovo(ActiveUser(userId = uid, username = ime, slikaUrl = it)) }
        firestore.collection("Korisnici").document(uid).get()
            .addOnSuccessListener { doc ->
                val slika = doc.getString("slikaURL").orEmpty()
                slikeKorisnika[uid] = slika
                gotovo(ActiveUser(userId = uid, username = doc.getString("userName")?.takeIf { it.isNotBlank() } ?: ime, slikaUrl = slika))
            }
            .addOnFailureListener { gotovo(ActiveUser(userId = uid, username = ime)) }
    }

    override fun onCleared() {
        dolazneRegistracija?.remove()
        razgovoriRegistracija?.remove()
        listeners.values.forEach { it.remove() }
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

    // Poslednji upis lokacije, da se ne upisuje pri svakom malom pomeranju
    private var poslednjiUpis: Triple<Double, Double, Long>? = null

    fun updateMyLocationIfSharing(lat: Double, lng: Double, deli: Boolean) {
        val currentUser = auth.currentUser ?: return
        if (!deli) {
            if (poslednjiUpis != null) removeMyLocation()
            return
        }
        val sada = System.currentTimeMillis()
        poslednjiUpis?.let { (staraLat, staraLng, vreme) ->
            val pomeraj = FloatArray(1)
            android.location.Location.distanceBetween(staraLat, staraLng, lat, lng, pomeraj)
            // Upis samo kad se pomeri 30 m ili prođe minut (da ostane "aktivan")
            if (pomeraj[0] < 30f && sada - vreme < 60_000L) return
        }
        poslednjiUpis = Triple(lat, lng, sada)
        val activeUser = ActiveUser(
            userId = currentUser.uid,
            username = myUsername,
            slikaUrl = mySlikaUrl ?: "",
            lat = lat,
            lng = lng,
            lastActive = sada
        )
        firestore.collection("ActiveUsers").document(currentUser.uid).set(activeUser)
    }

    fun removeMyLocation() {
        poslednjiUpis = null
        val currentUser = auth.currentUser ?: return
        firestore.collection("ActiveUsers").document(currentUser.uid).delete()
    }

    private fun listenToActiveUsers() {
        // Samo korisnici aktivni u poslednjih 5 minuta (stari zapisi se ne čitaju)
        firestore.collection("ActiveUsers")
            .whereGreaterThan("lastActive", System.currentTimeMillis() - 5 * 60 * 1000)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val currentTime = System.currentTimeMillis()
                    // Svoj nalog se ne prikazuje kao pecaroš u blizini (inače bi se otvorio chat sa samim sobom)
                    val mojUid = auth.currentUser?.uid
                    val users = snapshot.documents.mapNotNull { it.toObject(ActiveUser::class.java) }
                        .filter { currentTime - it.lastActive < 5 * 60 * 1000 && it.userId != mojUid }

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
        if (text.isBlank() || targetUserId == myUid) return

        val roomId = getChatRoomId(myUid, targetUserId)
        val newMessage = UserMessage(
            senderId = myUid,
            senderUsername = senderUsername,
            text = text,
            timestamp = System.currentTimeMillis(),
            primalacId = targetUserId
        )

        firestore.collection("PrivateChats").document(roomId).collection("Messages").add(newMessage)
    }
}