package com.example.fishspot.feature.fish

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val DANA_ZA_USKORO = 30
private val DANA_U_MESECU = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private val OZNAKE_MESECI = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
private val NAZIVI_MESECI = listOf(
    "januar", "februar", "mart", "april", "maj", "jun",
    "jul", "avgust", "septembar", "oktobar", "novembar", "decembar"
)

private enum class Status { U_LOVOSTAJU, USKORO, DOZVOLJENO, SAMO_MERA }

private data class StavkaLovostaja(
    val propis: LovostajChecker.Propis,
    val status: Status,
    val dana: Int // do kraja zabrane (U_LOVOSTAJU) ili do početka (USKORO, DOZVOLJENO)
)

@Composable
fun LovostajScreen(onNavigateBack: () -> Unit) {
    var pretraga by remember { mutableStateOf("") }

    val danas = remember { Calendar.getInstance() }
    val danasBroj = (danas.get(Calendar.MONTH) + 1) * 100 + danas.get(Calendar.DAY_OF_MONTH)
    val tekuciMesec = danas.get(Calendar.MONTH) + 1

    val sveStavke = remember {
        LovostajChecker.sviPropisi().map { propis ->
            val p = propis.pravilo
            when {
                p.pocetak == null || p.kraj == null -> StavkaLovostaja(propis, Status.SAMO_MERA, 0)
                LovostajChecker.uPeriodu(p, danasBroj) ->
                    StavkaLovostaja(propis, Status.U_LOVOSTAJU, danaDo(danas, p.kraj))
                else -> {
                    val doPocetka = danaDo(danas, p.pocetak)
                    StavkaLovostaja(propis, if (doPocetka <= DANA_ZA_USKORO) Status.USKORO else Status.DOZVOLJENO, doPocetka)
                }
            }
        }
    }
    val brojULovostaju = sveStavke.count { it.status == Status.U_LOVOSTAJU }

    val trazeno = LovostajChecker.normalizujTekst(pretraga)
    val filtrirano = sveStavke.filter { trazeno.isEmpty() || LovostajChecker.normalizujTekst(it.propis.naziv).contains(trazeno) }
    val grupe = listOf(
        "U lovostaju danas" to filtrirano.filter { it.status == Status.U_LOVOSTAJU }.sortedBy { it.dana },
        "Počinje uskoro" to filtrirano.filter { it.status == Status.USKORO }.sortedBy { it.dana },
        "Dozvoljen ulov" to filtrirano.filter { it.status == Status.DOZVOLJENO }.sortedBy { it.dana },
        "Bez lovostaja, samo minimalna mera" to filtrirano.filter { it.status == Status.SAMO_MERA }.sortedBy { it.propis.naziv }
    ).filter { it.second.isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().background(FishBoje.Pozadina)) {
        FishZaglavlje(
            oznaka = "Propisi o ribolovu",
            naslov = "Lovostaj",
            onNazad = onNavigateBack,
            ikona = Icons.Default.EventBusy,
            podnaslov = {
                Text(
                    "Danas je ${danas.get(Calendar.DAY_OF_MONTH)}. ${NAZIVI_MESECI[tekuciMesec - 1]}",
                    color = FishBoje.TekstSekundarni,
                    fontSize = 14.sp
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Sazetak(brojULovostaju)
            }
            item {
                OutlinedTextField(
                    value = pretraga,
                    onValueChange = { pretraga = it },
                    placeholder = { Text("Pretraži vrstu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (pretraga.isNotEmpty()) {
                        {
                            IconButton(onClick = { pretraga = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Obriši pretragu")
                            }
                        }
                    } else null,
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (grupe.isEmpty()) {
                item {
                    Text(
                        "Nema vrste koja odgovara pretrazi.",
                        color = FishBoje.TekstPriguseni,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    )
                }
            }

            grupe.forEach { (naslov, stavke) ->
                item(key = naslov) {
                    NaslovSekcije(naslov, modifier = Modifier.padding(top = 8.dp))
                }
                if (stavke.first().status == Status.SAMO_MERA) {
                    item(key = "$naslov-lista") { KarticaSamoMera(stavke) }
                } else {
                    items(stavke, key = { it.propis.naziv }) { stavka ->
                        KarticaVrste(stavka, tekuciMesec)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = FishBoje.TekstPriguseni, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Podaci su informativni. Pre pecanja proveri važeći pravilnik i pravila " +
                            "korisnika ribolovnog područja, jer mogu imati strože zabrane.",
                        color = FishBoje.TekstPriguseni,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Sazetak(brojULovostaju: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$brojULovostaju",
            color = if (brojULovostaju > 0) FishBoje.Greska else FishBoje.Akcenat,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(16.dp))
        Text(
            when {
                brojULovostaju == 0 -> "Nijedna vrsta trenutno nije u lovostaju"
                brojULovostaju == 1 -> "vrsta je trenutno u lovostaju i mora se vratiti u vodu"
                else -> "vrsta je trenutno u lovostaju i moraju se vratiti u vodu"
            },
            color = FishBoje.TekstSekundarni,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun KarticaVrste(stavka: StavkaLovostaja, tekuciMesec: Int) {
    val p = stavka.propis.pravilo
    val (boja, oznaka) = when (stavka.status) {
        Status.U_LOVOSTAJU -> FishBoje.Greska to (if (stavka.dana == 0) "Ističe danas" else "Još ${daniTekst(stavka.dana)}")
        Status.USKORO -> FishBoje.Upozorenje to "Počinje za ${daniTekst(stavka.dana)}"
        else -> FishBoje.Akcenat to "Dozvoljeno"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (stavka.status == Status.U_LOVOSTAJU) boja.copy(alpha = 0.5f) else FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stavka.propis.naziv,
                color = FishBoje.TekstPrimarni,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                oznaka,
                color = boja,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(boja.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("Zabrana ${datumTekst(p.pocetak!!)} – ${datumTekst(p.kraj!!)}")
                if (p.minimalnaDužina > 0) append("  •  min. ${p.minimalnaDužina.toInt()} cm")
            },
            color = FishBoje.TekstSekundarni,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        TrakaMeseci(p, tekuciMesec, boja)
    }
}

// 12 polja za mesece; obojeni su meseci u kojima važi zabrana (makar deo meseca)
@Composable
private fun TrakaMeseci(pravilo: LovostajChecker.PraviloRibe, tekuciMesec: Int, boja: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..12).forEach { mesec ->
            val zabrana = (1..DANA_U_MESECU[mesec - 1]).any { dan -> LovostajChecker.uPeriodu(pravilo, mesec * 100 + dan) }
            val tekuci = mesec == tekuciMesec
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (zabrana) boja.copy(alpha = if (tekuci) 1f else 0.6f) else FishBoje.Krug)
                )
                Text(
                    OZNAKE_MESECI[mesec - 1],
                    color = if (tekuci) FishBoje.TekstPrimarni else FishBoje.TekstPriguseni,
                    fontSize = 10.sp,
                    fontWeight = if (tekuci) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun KarticaSamoMera(stavke: List<StavkaLovostaja>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        stavke.forEachIndexed { i, stavka ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(FishBoje.Akcenat, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(stavka.propis.naziv, color = FishBoje.TekstPrimarni, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    "min. ${stavka.propis.pravilo.minimalnaDužina.toInt()} cm",
                    color = FishBoje.TekstSekundarni,
                    fontSize = 13.sp
                )
            }
            if (i < stavke.lastIndex) HorizontalDivider(color = FishBoje.IvicaKartice)
        }
    }
}

// Broj dana od danas do sledećeg datuma (mesec * 100 + dan), uključujući taj dan
private fun danaDo(danas: Calendar, datum: Int): Int {
    val pocetakDana = (danas.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val cilj = (pocetakDana.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.MONTH, datum / 100 - 1)
        set(Calendar.DAY_OF_MONTH, minOf(datum % 100, getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (before(pocetakDana)) add(Calendar.YEAR, 1)
    }
    return TimeUnit.MILLISECONDS.toDays(cilj.timeInMillis - pocetakDana.timeInMillis).toInt()
}

private fun datumTekst(datum: Int) = "${datum % 100}.${datum / 100}."

private fun daniTekst(dana: Int): String = when {
    dana == 0 -> "danas"
    dana % 10 == 1 && dana % 100 != 11 -> "$dana dan"
    else -> "$dana dana"
}
