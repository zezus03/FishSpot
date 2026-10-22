package com.example.fishspot.feature.uvod

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import kotlinx.coroutines.launch

private const val PREF_UVOD = "uvod_prikazan"

// Da li je korisnik već video uvod (čuva se na telefonu)
fun uvodVidjen(context: Context): Boolean =
    context.getSharedPreferences("fishspot_prefs", Context.MODE_PRIVATE).getBoolean(PREF_UVOD, false)

fun oznaciUvodVidjen(context: Context) {
    context.getSharedPreferences("fishspot_prefs", Context.MODE_PRIVATE).edit().putBoolean(PREF_UVOD, true).apply()
}

private data class StranaUvoda(val ikona: ImageVector, val naslov: String, val dedaKaze: String)

private val STRANE = listOf(
    StranaUvoda(Icons.Default.Phishing, "Dobro došao u FishSpot",
        "Bistro, sinko! Ja sam deda Pera, pola veka sam proveo kraj vode. Hajde da ti pokažem šta sve ova aplikacija ume, pa da kreneš u pecanje kao pravi alas."),
    StranaUvoda(Icons.Default.Map, "Mapa ulova",
        "Na mapi vidiš ulove pecaroša iz cele Srbije. Dodirni ribicu da vidiš sliku, vrstu, težinu, mamac i komentare. Dodirom na ime autora vidiš sve njegove ulove."),
    StranaUvoda(Icons.Default.AddAPhoto, "Dodaj svoj ulov",
        "Klikni na plus i slikaj ulov. Ja proverim da je na slici zaista riba, izračunam težinu po dužini i vrsti, i opomenem te ako je riba u lovostaju ili ispod mere."),
    StranaUvoda(Icons.Default.WbSunny, "Vreme i vodostaji",
        "Pre polaska pogledaj prognozu za mesto gde pecaš. Indeks uspešnosti ti kaže kakav je dan za pecanje, a vodostaji pokazuju da li reka raste ili opada."),
    StranaUvoda(Icons.Default.CalendarMonth, "Lovostaj",
        "U kalendaru lovostaja vidiš koja je riba danas zaštićena, kad počinje zabrana i kolika je najmanja dozvoljena mera. Reku čuvamo da bi imali šta da pecamo i sutra!"),
    StranaUvoda(Icons.Default.Leaderboard, "Rang lista",
        "Za svaki ulov dobijaš poene, a što je riba teža, više poena. Na rang listi vidiš ko je najbolji pecaroš. Ajde da te vidim na postolju!"),
    StranaUvoda(Icons.Default.Forum, "Pitaj deda Peru",
        "Kad ne znaš koji mamac, predvez ili mesto, pitaj mene. Dugme sa mojom slikom je dole desno na mapi. Odgovaram na pitanja o pecanju, do 30 dnevno."),
    StranaUvoda(Icons.Default.PrivacyTip, "Tvoja privatnost",
        "Tvoja lokacija se ne prikazuje drugima dok sam ne uključiš 'Prikaži me na mapi' na profilu. Obaveštenja o vodama u blizini su isključena, a možeš ih uključiti na profilu.")
)

/**
 * Uvod koji se prikazuje pri prvom ulasku u aplikaciju (i iz profila): deda Pera
 * kroz nekoliko strana pokazuje glavne funkcije.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UvodScreen(onGotovo: () -> Unit) {
    val pager = rememberPagerState(pageCount = { STRANE.size })
    val scope = rememberCoroutineScope()
    val poslednja = pager.currentPage == STRANE.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FishBoje.ZaglavljeGore, FishBoje.Pozadina, FishBoje.Pozadina)))
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${pager.currentPage + 1} / ${STRANE.size}", color = FishBoje.TekstPriguseni, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            if (!poslednja) {
                TextButton(onClick = onGotovo) { Text("Preskoči", color = FishBoje.TekstSekundarni) }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { indeks ->
            Strana(STRANE[indeks])
        }

        // Tačkice
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(STRANE.size) { i ->
                val aktivna = i == pager.currentPage
                val sirina by animateDpAsState(if (aktivna) 22.dp else 8.dp, label = "tacka")
                val boja by animateColorAsState(if (aktivna) FishBoje.Akcenat else FishBoje.Krug, label = "boja")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(sirina)
                        .clip(CircleShape)
                        .background(boja)
                )
            }
        }

        Button(
            onClick = {
                if (poslednja) onGotovo() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).height(54.dp)
        ) {
            Text(if (poslednja) "Kreni u pecanje" else "Dalje", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Strana(strana: StranaUvoda) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ikonica funkcije
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(FishBoje.Kartica, CircleShape)
                .border(2.dp, FishBoje.AkcenatTamni, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(strana.ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(strana.naslov, color = FishBoje.TekstPrimarni, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        // Deda Pera govori
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.ic_deda_pera),
                contentDescription = "Deda Pera",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, FishBoje.Akcenat, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                strana.dedaKaze,
                color = FishBoje.TekstPrimarni,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .weight(1f)
                    .background(FishBoje.Kartica, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}
