package com.example.fishspot.feature.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.fishPoljeBoje
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlokiranKorisnik(val uid: String, val ime: String)

/**
 * Blokiranje i prijava korisnika (traži ih Google Play za aplikacije sa sadržajem korisnika).
 * Spisak je u Korisnici/{moj uid}/blokirani/{uid}; pravila baze zabranjuju poruke u oba smera,
 * a aplikacija sakriva ulove, komentare i razgovore blokiranih.
 */
object Blokiranje {
    private val db = FirebaseFirestore.getInstance()
    private val _spisak = MutableStateFlow<List<BlokiranKorisnik>>(emptyList())
    val spisak: StateFlow<List<BlokiranKorisnik>> = _spisak.asStateFlow()
    private val _uidovi = MutableStateFlow<Set<String>>(emptySet())
    /** Uid-ovi blokiranih korisnika, za brzo filtriranje */
    val uidovi: StateFlow<Set<String>> = _uidovi.asStateFlow()
    private var slusalac: ListenerRegistration? = null

    init {
        // Spisak prati prijavljenog korisnika (prijava, odjava, promena naloga)
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            slusalac?.remove()
            slusalac = null
            _spisak.value = emptyList()
            _uidovi.value = emptySet()
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            slusalac = db.collection("Korisnici").document(uid).collection("blokirani")
                .addSnapshotListener { s, e ->
                    if (e != null) { Log.e("Blokiranje", "Učitavanje blokiranih", e); return@addSnapshotListener }
                    val lista = s?.documents.orEmpty().map { BlokiranKorisnik(it.id, it.getString("ime").orEmpty()) }
                    _spisak.value = lista.sortedBy { it.ime.lowercase() }
                    _uidovi.value = lista.map { it.uid }.toSet()
                }
        }
    }

    /** Poziva se pri pokretanju, da spisak bude spreman pre prvog prikaza mape */
    fun pokreni() = Unit

    fun blokiraj(uid: String, ime: String) {
        val moj = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid == moj) return
        db.collection("Korisnici").document(moj).collection("blokirani").document(uid)
            .set(mapOf("ime" to ime.take(60), "vreme" to System.currentTimeMillis()))
            .addOnSuccessListener { FishPoruka.prikazi("$ime je blokiran") }
            .addOnFailureListener { FishPoruka.prikazi("Blokiranje nije uspelo") }
    }

    fun odblokiraj(uid: String, ime: String) {
        val moj = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("Korisnici").document(moj).collection("blokirani").document(uid).delete()
            .addOnSuccessListener { FishPoruka.prikazi("$ime više nije blokiran") }
    }

    fun prijavi(uid: String, razlog: String, opis: String, kontekst: String) {
        val moj = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("PrijaveKorisnika").add(
            mapOf(
                "prijavioId" to moj,
                "prijavljeniId" to uid,
                "razlog" to razlog.take(100),
                "opis" to opis.take(500),
                "kontekst" to kontekst.take(1000),
                "vreme" to System.currentTimeMillis()
            )
        )
            .addOnSuccessListener { FishPoruka.prikazi("Prijava je poslata. Hvala!") }
            .addOnFailureListener { FishPoruka.prikazi("Prijava nije poslata, pokušaj ponovo") }
    }
}

private val RAZLOZI_PRIJAVE_KORISNIKA = listOf(
    "Uvrede ili uznemiravanje",
    "Neprikladan sadržaj (slike, ime, poruke)",
    "Spam ili reklame",
    "Lažni ulovi ili varanje poena",
    "Lažni nalog",
    "Nešto drugo"
)

/**
 * Meni ⋮ sa „Prijavi korisnika“ i „Blokiraj / Odblokiraj“.
 * kontekst: šta se prijavljuje (npr. tekst poruke ili ID ulova), da admin zna gde da gleda.
 */
@Composable
fun MeniKorisnika(uid: String, ime: String, kontekst: String = "", boja: Color = FishBoje.TekstSekundarni) {
    val moj = FirebaseAuth.getInstance().currentUser?.uid
    if (uid.isBlank() || uid == moj) return
    val blokirani by Blokiranje.uidovi.collectAsState()
    val blokiran = uid in blokirani
    var otvoren by remember { mutableStateOf(false) }
    var prijava by remember { mutableStateOf(false) }
    var potvrdaBlokiranja by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { otvoren = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Još opcija za korisnika", tint = boja)
        }
        DropdownMenu(
            expanded = otvoren, onDismissRequest = { otvoren = false },
            modifier = Modifier.background(FishBoje.Kartica)
        ) {
            DropdownMenuItem(
                text = { Text("Prijavi korisnika", color = FishBoje.TekstPrimarni) },
                leadingIcon = { Icon(Icons.Default.Report, null, tint = FishBoje.Greska) },
                onClick = { otvoren = false; prijava = true }
            )
            DropdownMenuItem(
                text = { Text(if (blokiran) "Odblokiraj" else "Blokiraj", color = FishBoje.TekstPrimarni) },
                leadingIcon = { Icon(Icons.Default.Block, null, tint = if (blokiran) FishBoje.Akcenat else FishBoje.Greska) },
                onClick = {
                    otvoren = false
                    if (blokiran) Blokiranje.odblokiraj(uid, ime) else potvrdaBlokiranja = true
                }
            )
        }
    }

    if (potvrdaBlokiranja) {
        AlertDialog(
            onDismissRequest = { potvrdaBlokiranja = false },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            icon = { Icon(Icons.Default.Block, null, tint = FishBoje.Greska) },
            title = { Text("Blokirati korisnika $ime?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Neće moći da ti šalje poruke, a ti nećeš videti njegove ulove ni komentare. " +
                    "On ne dobija obaveštenje. Blokiranje poništavaš na profilu, u spisku blokiranih.")
            },
            confirmButton = {
                Button(
                    onClick = { Blokiranje.blokiraj(uid, ime); potvrdaBlokiranja = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
                ) { Text("Blokiraj", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { potvrdaBlokiranja = false }) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
        )
    }

    if (prijava) {
        PrijavaKorisnikaDialog(ime, onDismiss = { prijava = false }) { razlog, opis, iBlokiraj ->
            Blokiranje.prijavi(uid, razlog, opis, kontekst)
            if (iBlokiraj && !blokiran) Blokiranje.blokiraj(uid, ime)
            prijava = false
        }
    }
}

@Composable
private fun PrijavaKorisnikaDialog(ime: String, onDismiss: () -> Unit, onPotvrdi: (String, String, Boolean) -> Unit) {
    var razlog by remember { mutableStateOf<String?>(null) }
    var opis by remember { mutableStateOf("") }
    var iBlokiraj by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FishBoje.Kartica,
        titleContentColor = FishBoje.TekstPrimarni,
        textContentColor = FishBoje.TekstSekundarni,
        icon = { Icon(Icons.Default.Report, null, tint = FishBoje.Greska) },
        title = { Text("Prijavi korisnika $ime", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                RAZLOZI_PRIJAVE_KORISNIKA.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = razlog == r, onClick = { razlog = r }, role = Role.RadioButton)
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = razlog == r, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = FishBoje.Akcenat, unselectedColor = FishBoje.TekstPriguseni))
                        Spacer(Modifier.width(8.dp))
                        Text(r, fontSize = 14.sp, color = if (razlog == r) FishBoje.TekstPrimarni else FishBoje.TekstSekundarni)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = opis, onValueChange = { if (it.length <= 500) opis = it },
                    label = { Text("Šta se desilo? (opciono)") }, minLines = 2,
                    colors = fishPoljeBoje(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { iBlokiraj = !iBlokiraj }.padding(top = 6.dp)
                ) {
                    Checkbox(checked = iBlokiraj, onCheckedChange = { iBlokiraj = it },
                        colors = CheckboxDefaults.colors(checkedColor = FishBoje.Akcenat))
                    Text("Blokiraj i ovog korisnika", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { razlog?.let { onPotvrdi(it, opis.trim(), iBlokiraj) } },
                enabled = razlog != null,
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
            ) { Text("Pošalji prijavu", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
    )
}

/** Spisak blokiranih na profilu, sa poništavanjem */
@Composable
fun BlokiraniDialog(onZatvori: () -> Unit) {
    val spisak by Blokiranje.spisak.collectAsState()
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = FishBoje.Kartica,
        titleContentColor = FishBoje.TekstPrimarni,
        textContentColor = FishBoje.TekstSekundarni,
        title = { Text("Blokirani korisnici", fontWeight = FontWeight.Bold) },
        text = {
            if (spisak.isEmpty()) Text("Nisi blokirao nijednog korisnika.")
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(spisak, key = { it.uid }) { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(k.ime.ifBlank { "Pecaroš" }, color = FishBoje.TekstPrimarni, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Blokiranje.odblokiraj(k.uid, k.ime) }) {
                            Text("Odblokiraj", color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onZatvori) { Text("Zatvori", color = FishBoje.Akcenat) } }
    )
}
