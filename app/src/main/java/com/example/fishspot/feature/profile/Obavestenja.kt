package com.example.fishspot.feature.profile

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Jedno obaveštenje = jedan ulov: "Marko i još 3 korisnika su lajkovali tvoj ulov: Šaran 4,2 kg"
data class Obavestenje(
    val id: String,
    val ulovId: String,
    val ulov: String,
    val broj: Int,
    val poslednjeIme: String,
    val poslednjaSlika: String,
    val vreme: Long,
    val procitano: Boolean
)

/**
 * Obaveštenja u aplikaciji, bez slanja na telefon. Upisuje ih server (functions/obavestenja.js) u
 * Korisnici/{moj uid}/obavestenja, a aplikacija ih prikazuje (zvonce u profilu, tačkica na mapi)
 * i označava pročitanim kad se spisak otvori.
 */
object Obavestenja {
    private val db = FirebaseFirestore.getInstance()
    private val _lista = MutableStateFlow<List<Obavestenje>>(emptyList())
    val lista: StateFlow<List<Obavestenje>> = _lista.asStateFlow()
    private val _neprocitano = MutableStateFlow(0)
    val neprocitano: StateFlow<Int> = _neprocitano.asStateFlow()
    private var slusalac: ListenerRegistration? = null

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            slusalac?.remove()
            slusalac = null
            _lista.value = emptyList()
            _neprocitano.value = 0
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            slusalac = db.collection("Korisnici").document(uid).collection("obavestenja")
                .orderBy("vreme", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener { s, e ->
                    if (e != null) { Log.e("Obavestenja", "Učitavanje obaveštenja", e); return@addSnapshotListener }
                    val l = s?.documents.orEmpty().map { d ->
                        Obavestenje(
                            id = d.id,
                            ulovId = d.getString("ulovId") ?: d.id,
                            ulov = d.getString("ulov").orEmpty(),
                            broj = (d.getLong("broj") ?: 1L).toInt(),
                            poslednjeIme = d.getString("poslednjeIme").orEmpty().ifBlank { "Pecaroš" },
                            poslednjaSlika = d.getString("poslednjaSlika").orEmpty(),
                            vreme = d.getLong("vreme") ?: 0L,
                            procitano = d.getBoolean("procitano") ?: true
                        )
                    }
                    _lista.value = l
                    _neprocitano.value = l.count { !it.procitano }
                }
        }
    }

    /** Poziva se pri pokretanju, da broj bude spreman pre prvog prikaza mape */
    fun pokreni() = Unit

    /** Otvaranjem spiska sva obaveštenja postaju pročitana (nestaju broj i tačkica) */
    fun oznaciProcitano() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val neprocitana = _lista.value.filter { !it.procitano }
        if (neprocitana.isEmpty()) return
        val paket = db.batch()
        neprocitana.forEach { paket.update(db.collection("Korisnici").document(uid).collection("obavestenja").document(it.id), "procitano", true) }
        paket.commit().addOnFailureListener { Log.e("Obavestenja", "Označavanje pročitanih", it) }
    }

    // "Marko je lajkovao" / "Marko i još 1 korisnik su lajkovali" / "Marko i još 3 korisnika su lajkovali"
    fun ostali(broj: Int): String {
        val n = broj - 1
        return if (n % 10 == 1 && n % 100 != 11) "$n korisnik" else "$n korisnika"
    }
}

// Zvonce sa brojem nepročitanih (u profilu, levo od slike)
@Composable
fun ZvonceObavestenja(onOtvoriUlov: (String) -> Unit, modifier: Modifier = Modifier) {
    val neprocitano by Obavestenja.neprocitano.collectAsState()
    var otvoren by remember { mutableStateOf(false) }
    if (otvoren) ObavestenjaDialog(onZatvori = { otvoren = false }, onOtvoriUlov = { otvoren = false; onOtvoriUlov(it) })
    Box(modifier, contentAlignment = Alignment.Center) {
        BadgedBox(badge = {
            if (neprocitano > 0) Badge(containerColor = FishBoje.Greska) { Text(if (neprocitano > 9) "9+" else "$neprocitano") }
        }) {
            IconButton(
                onClick = { otvoren = true; Obavestenja.oznaciProcitano() },
                modifier = Modifier.size(48.dp).background(FishBoje.Kartica, CircleShape).border(1.dp, FishBoje.IvicaKartice, CircleShape)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "Obaveštenja", tint = FishBoje.Akcenat)
            }
        }
    }
}

@Composable
private fun ObavestenjaDialog(onZatvori: () -> Unit, onOtvoriUlov: (String) -> Unit) {
    val lista by Obavestenja.lista.collectAsState()
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FISHSPOT • OBAVEŠTENJA", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Obaveštenja", color = FishBoje.TekstPrimarni, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                if (lista.isEmpty()) {
                    Text(
                        "Još nema obaveštenja. Ovde ćeš videti ko je lajkovao tvoje ulove.",
                        color = FishBoje.TekstSekundarni, fontSize = 15.sp, modifier = Modifier.padding(24.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lista, key = { it.id }) { o -> RedObavestenja(o) { onOtvoriUlov(o.ulovId) } }
                }
            }
        }
    }
}

@Composable
private fun RedObavestenja(o: Obavestenje, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (o.procitano) FishBoje.Kartica else FishBoje.Krug)
            .border(1.dp, if (o.procitano) FishBoje.IvicaKartice else FishBoje.AkcenatTamni, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(FishBoje.Polje), contentAlignment = Alignment.Center) {
            if (o.poslednjaSlika.isNotBlank()) AsyncImage(o.poslednjaSlika, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(o.poslednjeIme.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FishBoje.TekstPrimarni)) { append(o.poslednjeIme) }
                    if (o.broj > 1) {
                        append(" i još ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FishBoje.TekstPrimarni)) { append(Obavestenja.ostali(o.broj)) }
                        append(" su lajkovali tvoj ulov")
                    } else append(" je lajkovao tvoj ulov")
                    if (o.ulov.isNotBlank()) {
                        append(": ")
                        withStyle(SpanStyle(color = FishBoje.Akcenat, fontWeight = FontWeight.SemiBold)) { append(o.ulov) }
                    }
                },
                color = FishBoje.TekstSekundarni, fontSize = 14.sp, lineHeight = 19.sp
            )
            Text(pre(o.vreme), color = FishBoje.TekstPriguseni, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.Favorite, null, tint = FishBoje.Greska, modifier = Modifier.size(18.dp))
    }
}

private fun pre(vreme: Long): String {
    val min = (System.currentTimeMillis() - vreme) / 60_000
    return when {
        min < 1 -> "upravo"
        min < 60 -> "pre $min min"
        min < 24 * 60 -> "pre ${min / 60} h"
        else -> "pre ${min / (24 * 60)} d"
    }
}
