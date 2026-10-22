package com.example.fishspot.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.fishPoljeBoje
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Privatni razgovor sa pecarošem sa mape, u istom stilu kao razgovor sa Deda Perom.
 */
@Composable
fun PrivatniChat(
    sagovornik: ActiveUser,
    poruke: List<UserMessage>,
    mojId: String?,
    onPosalji: (String) -> Unit,
    onZatvori: () -> Unit
) {
    var novaPoruka by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(poruke.size) {
        if (poruke.isNotEmpty()) listState.animateScrollToItem(poruke.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().background(FishBoje.Pozadina)) {
        // Zaglavlje: nazad, slika i ime sagovornika
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(FishBoje.ZaglavljeGore, FishBoje.Pozadina)))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onZatvori,
                modifier = Modifier.size(38.dp).background(FishBoje.Krug, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad",
                    tint = FishBoje.TekstSekundarni, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Avatar(sagovornik, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(sagovornik.username.ifBlank { "Pecaroš" }, color = FishBoje.TekstPrimarni,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(FishBoje.Akcenat, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("na mapi u blizini", color = FishBoje.TekstSekundarni, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = FishBoje.IvicaKartice)

        // Poruke
        if (poruke.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(64.dp).background(FishBoje.Krug, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                        tint = FishBoje.Akcenat, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Pozdravi ${sagovornik.username.ifBlank { "pecaroša" }}!",
                    color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Pitaj kako radi riba ili dogovorite zajedničko pecanje.",
                    color = FishBoje.TekstPriguseni, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                itemsIndexed(poruke) { i, poruka ->
                    val prethodna = poruke.getOrNull(i - 1)
                    if (prethodna == null || !istiDan(prethodna.timestamp, poruka.timestamp)) {
                        OznakaDana(poruka.timestamp)
                    }
                    Oblacic(poruka, moja = poruka.senderId == mojId)
                }
            }
        }

        // Polje za poruku
        Box(modifier = Modifier.fillMaxWidth().background(FishBoje.Kartica)) {
            HorizontalDivider(color = FishBoje.IvicaKartice)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = novaPoruka,
                    onValueChange = { novaPoruka = it.take(1000) },
                    placeholder = { Text("Poruka...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = fishPoljeBoje(),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                val mozeSlanje = novaPoruka.isNotBlank()
                IconButton(
                    onClick = {
                        onPosalji(novaPoruka.trim())
                        novaPoruka = ""
                    },
                    enabled = mozeSlanje,
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (mozeSlanje) FishBoje.Akcenat else FishBoje.Krug, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Pošalji",
                        tint = if (mozeSlanje) FishBoje.Pozadina else FishBoje.TekstPriguseni)
                }
            }
        }
    }
}

@Composable
private fun Oblacic(poruka: UserMessage, moja: Boolean) {
    val oblik = if (moja) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (moja) Alignment.End else Alignment.Start
    ) {
        Text(
            text = poruka.text,
            color = if (moja) FishBoje.Pozadina else FishBoje.TekstPrimarni,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(if (moja) FishBoje.Akcenat else FishBoje.Kartica, oblik)
                .then(if (moja) Modifier else Modifier.border(1.dp, FishBoje.IvicaKartice, oblik))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(poruka.timestamp)),
            color = FishBoje.TekstPriguseni,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun OznakaDana(vreme: Long) {
    val tekst = when {
        istiDan(vreme, System.currentTimeMillis()) -> "Danas"
        istiDan(vreme, System.currentTimeMillis() - 24 * 3600 * 1000L) -> "Juče"
        else -> SimpleDateFormat("d. MMMM", Locale("sr", "RS")).format(Date(vreme))
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            tekst,
            color = FishBoje.TekstSekundarni,
            fontSize = 12.sp,
            modifier = Modifier
                .background(FishBoje.Krug, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Avatar(korisnik: ActiveUser, velicina: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(velicina)
            .clip(CircleShape)
            .background(FishBoje.Krug)
            .border(2.dp, FishBoje.Akcenat, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (korisnik.slikaUrl.isNotBlank()) {
            AsyncImage(
                model = korisnik.slikaUrl,
                contentDescription = korisnik.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                korisnik.username.take(1).uppercase().ifBlank { "P" },
                color = FishBoje.Akcenat,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

private fun istiDan(a: Long, b: Long): Boolean {
    val ka = Calendar.getInstance().apply { timeInMillis = a }
    val kb = Calendar.getInstance().apply { timeInMillis = b }
    return ka.get(Calendar.YEAR) == kb.get(Calendar.YEAR) && ka.get(Calendar.DAY_OF_YEAR) == kb.get(Calendar.DAY_OF_YEAR)
}
