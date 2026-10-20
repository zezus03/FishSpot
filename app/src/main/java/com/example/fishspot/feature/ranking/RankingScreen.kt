package com.example.fishspot.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.core.model.Korisnici
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import com.google.firebase.auth.FirebaseAuth

private val Zlato = Color(0xFFFFC107)
private val Srebro = Color(0xFFB0BEC5)
private val Bronza = Color(0xFFCD7F32)

@Composable
fun RankingScreen(
    rankingViewModel: RankingViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onUserSelected: (userId: String) -> Unit
) {
    val userRanking by rankingViewModel.userRanking.collectAsState()
    val mojId = remember { FirebaseAuth.getInstance().currentUser?.uid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            FishZaglavlje(
                oznaka = "FishSpot • rang lista",
                naslov = "Najbolji ribolovci",
                ikona = Icons.Default.EmojiEvents,
                onNazad = onNavigateBack,
                podnaslov = {
                    Text("Poeni se dobijaju za ulove, po težini ribe", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                }
            )
        }

        if (userRanking.isNotEmpty()) {
            item {
                Postolje(
                    prvi = userRanking.getOrNull(0),
                    drugi = userRanking.getOrNull(1),
                    treci = userRanking.getOrNull(2),
                    mojId = mojId,
                    onUserSelected = onUserSelected
                )
            }
        }

        if (userRanking.size > 3) {
            item {
                NaslovSekcije("Ostali", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp))
            }
            itemsIndexed(userRanking.drop(3), key = { _, user -> user.id }) { index, user ->
                RankingListItem(rank = index + 4, user = user, jaSam = user.id == mojId, onUserClick = onUserSelected)
            }
        }
    }
}

// Prva tri mesta: drugi levo, prvi u sredini (najviši), treći desno
@Composable
private fun Postolje(
    prvi: Korisnici?,
    drugi: Korisnici?,
    treci: Korisnici?,
    mojId: String?,
    onUserSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MestoNaPostolju(drugi, 2, Srebro, 70.dp, 64.dp, mojId, onUserSelected, Modifier.weight(1f))
        MestoNaPostolju(prvi, 1, Zlato, 100.dp, 80.dp, mojId, onUserSelected, Modifier.weight(1f))
        MestoNaPostolju(treci, 3, Bronza, 50.dp, 64.dp, mojId, onUserSelected, Modifier.weight(1f))
    }
}

@Composable
private fun MestoNaPostolju(
    user: Korisnici?,
    mesto: Int,
    boja: Color,
    visinaStuba: Dp,
    velicinaSlike: Dp,
    mojId: String?,
    onUserSelected: (String) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.then(
            if (user != null) Modifier.pointerInput(user.id) { detectTapGestures(onTap = { onUserSelected(user.id) }) }
            else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bez ranog "return" iz Column-a: u ovoj verziji Compose-a to ruši aplikaciju (Stack.pop)
        if (user == null) {
            Spacer(Modifier.height(visinaStuba))
        } else {
            PopunjenoMesto(user, mesto, boja, visinaStuba, velicinaSlike, mojId)
        }
    }
}

@Composable
private fun ColumnScope.PopunjenoMesto(
    user: Korisnici,
    mesto: Int,
    boja: Color,
    visinaStuba: Dp,
    velicinaSlike: Dp,
    mojId: String?
) {
    if (mesto == 1) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Zlato, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
    }
    Avatar(user = user, velicina = velicinaSlike, ivica = boja)
    Spacer(Modifier.height(6.dp))
    Text(
        user.userName.ifBlank { "Pecaroš" } + if (user.id == mojId) " (ti)" else "",
        color = FishBoje.TekstPrimarni,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
    Text("${user.poeni} poena", color = boja, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(visinaStuba)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Brush.verticalGradient(listOf(boja.copy(alpha = 0.45f), boja.copy(alpha = 0.08f))))
            .border(1.dp, boja.copy(alpha = 0.5f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("$mesto", color = FishBoje.TekstPrimarni, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RankingListItem(
    rank: Int,
    user: Korisnici,
    jaSam: Boolean = false,
    onUserClick: (userId: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (jaSam) FishBoje.Akcenat.copy(alpha = 0.10f) else FishBoje.Kartica)
            .border(1.dp, if (jaSam) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            // detectTapGestures umesto clickable: navigacija tokom ripple animacije je rušila aplikaciju
            .pointerInput(user.id) { detectTapGestures(onTap = { onUserClick(user.id) }) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$rank", color = FishBoje.TekstPriguseni, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp))
        Avatar(user = user, velicina = 44.dp, ivica = if (jaSam) FishBoje.Akcenat else FishBoje.IvicaKartice)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.userName.ifBlank { "Pecaroš" }, color = FishBoje.TekstPrimarni, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (jaSam) {
                    Spacer(Modifier.width(6.dp))
                    Text("Ti", color = FishBoje.Pozadina, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(FishBoje.Akcenat, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            if (user.imePrezime.isNotBlank()) {
                Text(user.imePrezime, color = FishBoje.TekstPriguseni, fontSize = 12.sp, maxLines = 1)
            }
        }
        Text(
            "${user.poeni}",
            color = FishBoje.Akcenat,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(3.dp))
        Text("p", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
    }
}

// Profilna slika ili prvo slovo imena, sa obojenom ivicom
@Composable
private fun Avatar(user: Korisnici, velicina: Dp, ivica: Color) {
    val slika = user.slikaURL.takeIf { it.isNotBlank() && it != "null" }
    if (slika != null) {
        AsyncImage(
            model = slika,
            contentDescription = "Profilna slika",
            modifier = Modifier
                .size(velicina)
                .clip(CircleShape)
                .border(2.5.dp, ivica, CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(velicina)
                .background(FishBoje.Krug, CircleShape)
                .border(2.5.dp, ivica, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(user.userName.trim().firstOrNull()?.uppercase() ?: "?", color = FishBoje.Akcenat,
                fontWeight = FontWeight.Bold, fontSize = (velicina.value * 0.38f).sp)
        }
    }
}
