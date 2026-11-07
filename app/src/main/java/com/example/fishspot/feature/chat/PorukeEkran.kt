package com.example.fishspot.feature.chat

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Svi razgovori: dodir otvara razgovor, bez obzira da li je drugi pecaroš sada na mapi
@Composable
fun PorukeEkran(razgovori: List<Razgovor>, onOtvori: (Razgovor) -> Unit, onZatvori: () -> Unit) {
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("FISHSPOT • PORUKE", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Poruke", color = FishBoje.TekstPrimarni, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onZatvori) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                }
                if (razgovori.isEmpty()) {
                    Text(
                        "Još nemaš poruka. Piši pecarošu sa mape ili iz detalja njegovog ulova.",
                        color = FishBoje.TekstSekundarni, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(razgovori, key = { it.uid }) { r -> RedRazgovora(r) { onOtvori(r) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun RedRazgovora(r: Razgovor, onClick: () -> Unit) {
    val novo = r.neprocitano > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (novo) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(FishBoje.Krug),
            contentAlignment = Alignment.Center
        ) {
            if (r.slika.isNotBlank()) {
                AsyncImage(model = r.slika, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(r.ime.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.ime, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(vreme(r.vreme), color = if (novo) FishBoje.Akcenat else FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (r.odMene) "Ti: " else "") + r.poslednja,
                    color = if (novo) FishBoje.TekstPrimarni else FishBoje.TekstSekundarni,
                    fontWeight = if (novo) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (novo) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.background(FishBoje.Akcenat, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${r.neprocitano}", color = FishBoje.Pozadina, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Danas: "14:05", ranije: "28.10."
private fun vreme(ms: Long): String {
    if (ms <= 0) return ""
    val danas = Calendar.getInstance()
    val tada = Calendar.getInstance().apply { timeInMillis = ms }
    val istiDan = danas.get(Calendar.YEAR) == tada.get(Calendar.YEAR) && danas.get(Calendar.DAY_OF_YEAR) == tada.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (istiDan) "HH:mm" else "d.M.", Locale.getDefault()).format(Date(ms))
}
