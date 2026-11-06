package com.example.fishspot.feature.fish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fishspot.core.theme.FishBoje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// Atlas riba: slika i opis svake vrste (izgled, stanište, mrest, pecanje...).
// Podaci su u assets/atlas (atlas.json i <id>.webp), pa rade i bez interneta.
// Pravi ih alati/atlas_opisi.py i alati/atlas_slike.py iz sadrzaj/atlas_riba.

enum class StatusVrste { DOMACA, UNESENA, ZABRANA }

data class OdeljakAtlasa(val naslov: String, val tekst: String)

data class VrstaAtlasa(
    val id: String,
    val naziv: String,
    val latinski: String,
    val porodica: String,
    val drugoIme: String,
    val status: StatusVrste,
    val uvod: String,
    val odeljci: List<OdeljakAtlasa>,
    val narodniNazivi: List<String> = emptyList()
)

object AtlasRiba {
    @Volatile private var vrste: List<VrstaAtlasa>? = null

    // Lokalna imena koja su ista riba kao vrsta u atlasu
    private val drugaImena = mapOf("bandar" to "grgec", "nosara" to "sljivar", "sapaca" to "potocna_mrena", "protfis" to "jaz")

    // Id vrste u bazi (VrsteRiba), gde su i narodni nazivi, kad se razlikuje od id-a u atlasu
    private val idUBazi = mapOf(
        "smudj" to "smud", "smudj_kamenjar" to "smud-kamenjar", "cverglan" to "patuljasti-som",
        "sljivar" to "nosara", "zlatni_karas" to "karas"
    )

    // Vrsta iz atlasa za id vrste iz baze (ulovi): "smud" -> smudj, "crni-patuljasti-som" -> cverglan
    fun poIdUBazi(context: Context, vrstaId: String): VrstaAtlasa? {
        if (vrstaId == "crni-patuljasti-som") return poId(context, "cverglan")
        val atlasId = idUBazi.entries.firstOrNull { it.value == vrstaId }?.key ?: vrstaId.replace("-", "_")
        return poId(context, atlasId)
    }

    fun sve(context: Context): List<VrstaAtlasa> = vrste ?: synchronized(this) {
        vrste ?: ucitaj(context).also { vrste = it }
    }

    fun poId(context: Context, id: String): VrstaAtlasa? = sve(context).firstOrNull { it.id == id }

    // Vrsta po nazivu sa ekrana lovostaja ("Smuđ kamenjar" -> smudj_kamenjar)
    fun poNazivu(context: Context, naziv: String): VrstaAtlasa? {
        val id = naziv.lowercase().trim()
            .replace("š", "s").replace("č", "c").replace("ć", "c").replace("ž", "z").replace("đ", "dj")
            .replace(" ", "_")
        return poId(context, drugaImena[id] ?: id)
    }

    private fun ucitaj(context: Context): List<VrstaAtlasa> = try {
        val niz = JSONArray(context.assets.open("atlas/atlas.json").bufferedReader().use { it.readText() })
        (0 until niz.length()).map { i ->
            val o = niz.getJSONObject(i)
            val odeljci = o.getJSONArray("odeljci")
            VrstaAtlasa(
                id = o.getString("id"),
                naziv = o.getString("naziv"),
                latinski = o.optString("latinski"),
                porodica = o.optString("porodica"),
                drugoIme = o.optString("drugoIme"),
                status = when (o.optString("status")) {
                    "zabrana" -> StatusVrste.ZABRANA
                    "unesena" -> StatusVrste.UNESENA
                    else -> StatusVrste.DOMACA
                },
                uvod = o.optString("uvod"),
                narodniNazivi = o.getString("id").let { id ->
                    LokalniNazivi.nazivi(idUBazi[id] ?: id.replace("_", "-"))
                        .filter { !it.equals(o.optString("drugoIme"), ignoreCase = true) && !it.equals(o.getString("naziv"), ignoreCase = true) }
                },
                odeljci = (0 until odeljci.length()).map { j ->
                    val od = odeljci.getJSONObject(j)
                    OdeljakAtlasa(od.getString("naslov"), od.getString("tekst"))
                }
            )
        }
    } catch (e: Exception) {
        android.util.Log.w("AtlasRiba", "Atlas nije učitan", e)
        emptyList()
    }

    suspend fun slika(context: Context, id: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.assets.open("atlas/$id.webp").use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }
}

// Pozadina iza crteža: boja starog papira, kao na tablama iz kojih su slike
private val Papir = Color(0xFFF3EBD3)

@Composable
fun SlikaVrste(id: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, siva: Boolean = false) {
    val context = LocalContext.current
    val slika by produceState<Bitmap?>(null, id) { value = AtlasRiba.slika(context, id) }
    Box(modifier.background(Papir), contentAlignment = Alignment.Center) {
        slika?.let {
            Image(
                it.asImageBitmap(), contentDescription = null, contentScale = contentScale,
                colorFilter = if (siva) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
        }
    }
}

// Mala sličica vrste za spiskove; na dodir otvara opis
@Composable
fun SlicicaVrste(vrsta: VrstaAtlasa, sirina: Dp = 64.dp, onClick: () -> Unit) {
    SlikaVrste(
        vrsta.id,
        modifier = Modifier
            .size(width = sirina, height = sirina * 0.6f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    )
}

@Composable
fun AtlasDialog(vrsta: VrstaAtlasa, onZatvori: () -> Unit) {
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FISHSPOT • ATLAS RIBA", color = FishBoje.TekstPriguseni, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                val donjaTraka = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + donjaTraka)
                ) {
                    SlikaVrste(
                        vrsta.id,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(vrsta.naziv, color = FishBoje.TekstPrimarni, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (vrsta.latinski.isNotBlank()) {
                        Text(vrsta.latinski, color = FishBoje.Akcenat, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                    }
                    val podaci = listOfNotNull(
                        vrsta.porodica.takeIf { it.isNotBlank() },
                        vrsta.drugoIme.takeIf { it.isNotBlank() }?.let { "drugo ime: $it" }
                    ).joinToString("  •  ")
                    if (podaci.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(podaci, color = FishBoje.TekstSekundarni, fontSize = 13.sp)
                    }
                    if (vrsta.narodniNazivi.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Narodni nazivi: " + vrsta.narodniNazivi.joinToString(", "),
                            color = FishBoje.TekstSekundarni, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OznakaStatusa(vrsta.status)
                    if (vrsta.uvod.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        TekstAtlasa(vrsta.uvod, FishBoje.TekstSekundarni)
                    }
                    Spacer(Modifier.height(14.dp))
                    vrsta.odeljci.forEachIndexed { i, odeljak ->
                        OdeljakKartica(odeljak, otvorenNaPocetku = i == 0)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Podaci su informativni. Za lovostaj i mere uvek važi propis i pravila korisnika ribarskog područja.",
                        color = FishBoje.TekstPriguseni, fontSize = 12.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OznakaStatusa(status: StatusVrste) {
    val (tekst, boja) = when (status) {
        StatusVrste.ZABRANA -> "TRAJNA ZABRANA ULOVA" to FishBoje.Greska
        StatusVrste.UNESENA -> "UNESENA VRSTA" to FishBoje.Upozorenje
        StatusVrste.DOMACA -> "DOMAĆA VRSTA" to FishBoje.Akcenat
    }
    Text(
        tekst, color = boja, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(boja.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun OdeljakKartica(odeljak: OdeljakAtlasa, otvorenNaPocetku: Boolean) {
    var otvoren by remember { mutableStateOf(otvorenNaPocetku) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .clickable { otvoren = !otvoren }
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(odeljak.naslov, color = FishBoje.TekstPrimarni, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (otvoren) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = FishBoje.TekstPriguseni)
        }
        if (otvoren) {
            Spacer(Modifier.height(8.dp))
            TekstAtlasa(odeljak.tekst, FishBoje.TekstSekundarni)
        }
    }
}

// Prikaz teksta iz opisa: pasusi, stavke sa crticom ("- ", i uvučene "  - ") i **podebljano** / *iskošeno*
@Composable
private fun TekstAtlasa(tekst: String, boja: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tekst.split("\n").filter { it.isNotBlank() }.forEach { red ->
            val uvlaka = red.takeWhile { it == ' ' }.length
            val sadrzaj = red.trim()
            if (sadrzaj.startsWith("- ")) {
                Row(Modifier.padding(start = (uvlaka * 6).dp)) {
                    Text("•", color = FishBoje.Akcenat, fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(oznaceno(sadrzaj.removePrefix("- ")), color = boja, fontSize = 15.sp, lineHeight = 21.sp)
                }
            } else {
                Text(oznaceno(sadrzaj), color = boja, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

private fun oznaceno(tekst: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < tekst.length) {
        when {
            tekst.startsWith("**", i) -> {
                val kraj = tekst.indexOf("**", i + 2)
                if (kraj < 0) { append(tekst.substring(i)); break }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                append(tekst.substring(i + 2, kraj)); pop()
                i = kraj + 2
            }
            tekst[i] == '*' -> {
                val kraj = tekst.indexOf('*', i + 1)
                if (kraj < 0) { append(tekst.substring(i)); break }
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(tekst.substring(i + 1, kraj)); pop()
                i = kraj + 1
            }
            else -> { append(tekst[i]); i++ }
        }
    }
}
