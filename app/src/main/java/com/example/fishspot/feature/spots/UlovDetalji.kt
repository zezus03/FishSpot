package com.example.fishspot.feature.spots

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fishspot.core.model.Korisnici
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Razlozi za prijavu ulova (čuvaju se kao tekst u kolekciji Prijave)
private val RAZLOZI_PRIJAVE = listOf(
    "Ulov ne odgovara opisu (vrsta, dužina ili težina)",
    "Na slici nije riba",
    "Riba je u lovostaju ili ispod mere",
    "Neprikladan sadržaj",
    "Ostalo"
)

// Prikaz izabranog ulova: slika, autor, podaci o ribi, lajk, prijava i komentari
@Composable
fun UlovDetalji(
    spot: FishSpot,
    autor: Korisnici?,
    comments: List<Comment>,
    currentUserId: String?,
    vecPrijavljen: Boolean,
    onAddComment: (String) -> Unit,
    onToggleLike: (String, Boolean) -> Unit,
    onPrijavi: (razlog: String, opis: String) -> Unit,
    onAutorKlik: () -> Unit = {},
    onIzmeni: () -> Unit = {},
    onObrisi: () -> Unit = {}
) {
    var noviKomentar by remember { mutableStateOf("") }
    var prikaziPrijavu by remember { mutableStateOf(false) }
    var potvrdaBrisanja by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault()) }

    var isLiked by remember(spot.likedBy) {
        mutableStateOf(currentUserId != null && spot.likedBy.contains(currentUserId))
    }
    var likeCount by remember(spot.likedBy) { mutableStateOf(spot.likedBy.size) }
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.25f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "heartScale"
    )
    val mojUlov = currentUserId != null && currentUserId == spot.authorId

    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item { NaslovnaSlika(spot) }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Dodir na autora otvara njegov profil sa ulovima
                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAutorKlik)) {
                    AutorUlova(
                        ime = autor?.userName?.takeIf { it.isNotBlank() } ?: spot.authorUsername,
                        slikaUrl = autor?.slikaURL,
                        vreme = dateFormat.format(Date(spot.timestamp))
                    )
                }

                if (spot.naziv.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(28.dp).background(FishBoje.Krug, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp),
                                tint = FishBoje.Akcenat)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(spot.naziv, color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (spot.opis.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(spot.opis, color = FishBoje.TekstSekundarni, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))
                NaslovSekcije("Podaci o ulovu")
                Spacer(modifier = Modifier.height(8.dp))

                // Podaci o ulovu: 2 x 2 pastelne pločice, kao parametri u prognozi
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlocicaPodatka(Icons.Default.Straighten, "Dužina",
                        if (spot.duzinaRibe > 0) formatBroj(spot.duzinaRibe, "cm") else "—",
                        FishBoje.PlavaPlocica, Modifier.weight(1f))
                    PlocicaPodatka(Icons.Default.Scale, "Težina",
                        if (spot.tezinaRibe > 0) formatBroj(spot.tezinaRibe, "kg") else "—",
                        FishBoje.ZelenaPlocica, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlocicaPodatka(Icons.Default.Phishing, "Tehnika", spot.tehnika.ifBlank { "—" },
                        FishBoje.LjubicastaPlocica, Modifier.weight(1f))
                    PlocicaPodatka(Icons.Default.PestControl, "Mamac", spot.mamac.ifBlank { "—" },
                        FishBoje.NarandzastaPlocica, Modifier.weight(1f))
                }

                // Vremenski uslovi u trenutku ulova (ako su zabeleženi)
                val uslovi = listOfNotNull(
                    spot.temperatura.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Thermostat to it },
                    spot.pritisak.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Speed to it },
                    spot.vetar.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Air to it }
                )
                if (uslovi.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    NaslovSekcije("Uslovi pri ulovu")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uslovi.forEach { (ikona, tekst) -> Tablet(ikona, tekst) }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lajk, broj komentara i prijava
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tablet(
                        ikona = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        tekst = "$likeCount",
                        bojaIkone = if (isLiked) FishBoje.Greska else FishBoje.TekstSekundarni,
                        velikoSlovo = true,
                        ikonaModifier = Modifier.scale(heartScale),
                        onClick = {
                            if (currentUserId != null) {
                                isLiked = !isLiked
                                likeCount = if (isLiked) likeCount + 1 else likeCount - 1
                                onToggleLike(spot.id, isLiked)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Tablet(Icons.Default.ChatBubbleOutline, "${comments.size}", velikoSlovo = true)

                    Spacer(modifier = Modifier.weight(1f))

                    // Svoj ulov korisnik menja i briše, a tuđi može da prijavi
                    if (mojUlov) {
                        IconButton(
                            onClick = onIzmeni,
                            modifier = Modifier
                                .size(44.dp)
                                .background(FishBoje.Kartica, CircleShape)
                                .border(1.dp, FishBoje.IvicaKartice, CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Izmeni ulov", tint = FishBoje.Akcenat)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { potvrdaBrisanja = true },
                            modifier = Modifier
                                .size(44.dp)
                                .background(FishBoje.Greska.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, FishBoje.Greska.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Obriši ulov", tint = FishBoje.Greska)
                        }
                    } else if (currentUserId != null) {
                        IconButton(
                            onClick = { prikaziPrijavu = true },
                            enabled = !vecPrijavljen,
                            modifier = Modifier
                                .size(44.dp)
                                .background(FishBoje.Greska.copy(alpha = if (vecPrijavljen) 0.08f else 0.15f), CircleShape)
                                .border(1.dp, FishBoje.Greska.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Report,
                                contentDescription = if (vecPrijavljen) "Ulov je već prijavljen" else "Prijavi ulov",
                                tint = FishBoje.Greska.copy(alpha = if (vecPrijavljen) 0.4f else 1f)
                            )
                        }
                    }
                }
                if (vecPrijavljen) {
                    Text(
                        "Prijavio si ovaj ulov. Hvala, pregledaćemo ga.",
                        color = FishBoje.Greska,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                NaslovSekcije("Komentari")
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (comments.isEmpty()) {
                item {
                    Text(
                        "Još nema komentara. Budi prvi!",
                        color = FishBoje.TekstPriguseni,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(comments, key = { it.id.ifBlank { "${it.authorId}_${it.timestamp}" } }) { comment ->
                    KomentarMehur(comment, dateFormat.format(Date(comment.timestamp)))
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Polje za novi komentar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FishBoje.Kartica)
        ) {
            HorizontalDivider(color = FishBoje.IvicaKartice)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = noviKomentar,
                    onValueChange = { noviKomentar = it },
                    placeholder = { Text("Dodaj komentar...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = fishPoljeBoje(),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (noviKomentar.isNotBlank()) {
                            onAddComment(noviKomentar)
                            noviKomentar = ""
                        }
                    },
                    enabled = noviKomentar.isNotBlank(),
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (noviKomentar.isNotBlank()) FishBoje.Akcenat else FishBoje.Krug,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Pošalji",
                        tint = if (noviKomentar.isNotBlank()) FishBoje.Pozadina else FishBoje.TekstPriguseni
                    )
                }
            }
        }
    }

    if (potvrdaBrisanja) {
        AlertDialog(
            onDismissRequest = { potvrdaBrisanja = false },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = FishBoje.Greska) },
            title = { Text("Obriši ulov?", fontWeight = FontWeight.Bold) },
            text = { Text("Ulov, njegova slika i komentari biće trajno obrisani, a poeni za njega oduzeti.") },
            confirmButton = {
                Button(
                    onClick = {
                        potvrdaBrisanja = false
                        onObrisi()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
                ) { Text("Obriši", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { potvrdaBrisanja = false }) { Text("Otkaži", color = FishBoje.TekstSekundarni) }
            }
        )
    }

    if (prikaziPrijavu) {
        PrijavaUlovaDialog(
            onDismiss = { prikaziPrijavu = false },
            onPotvrdi = { razlog, opis ->
                prikaziPrijavu = false
                onPrijavi(razlog, opis)
            }
        )
    }
}

// Velika slika sa nazivom vrste preko donjeg dela
@Composable
private fun NaslovnaSlika(spot: FishSpot) {
    val naziv = spot.vrstaRibe.ifBlank { "Nepoznata vrsta" }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(24.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
    ) {
        if (!spot.slikaUrl.isNullOrBlank()) {
            AsyncImage(
                model = spot.slikaUrl,
                contentDescription = "Slika ulova",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.SetMeal,
                contentDescription = null,
                modifier = Modifier.size(72.dp).align(Alignment.Center),
                tint = FishBoje.TekstPriguseni
            )
        }
        // Tamni prelaz da tekst bude čitljiv na svakoj slici
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, FishBoje.Pozadina.copy(alpha = 0.9f))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(naziv, color = FishBoje.TekstPrimarni, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (spot.latinskiNaziv.isNotBlank()) {
                Text(spot.latinskiNaziv, color = FishBoje.Akcenat, fontSize = 14.sp, fontStyle = FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun AutorUlova(ime: String, slikaUrl: String?, vreme: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(ime = ime, slikaUrl = slikaUrl, velicina = 44)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(ime, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Upecano $vreme", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
        }
    }
}

// Profilna slika ili prvo slovo imena u krugu
@Composable
private fun Avatar(ime: String, slikaUrl: String?, velicina: Int) {
    if (!slikaUrl.isNullOrBlank() && slikaUrl != "null") {
        AsyncImage(
            model = slikaUrl,
            contentDescription = "Profilna slika",
            modifier = Modifier
                .size(velicina.dp)
                .clip(CircleShape)
                .border(2.dp, FishBoje.Akcenat, CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(velicina.dp)
                .background(FishBoje.Krug, CircleShape)
                .border(1.dp, FishBoje.IvicaKartice, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ime.trim().firstOrNull()?.uppercase() ?: "?",
                color = FishBoje.Akcenat,
                fontWeight = FontWeight.Bold,
                fontSize = if (velicina >= 40) 18.sp else 13.sp
            )
        }
    }
}

// Pastelna pločica sa obojenom ikonom (isti stil kao parametri u vremenskoj prognozi)
@Composable
private fun PlocicaPodatka(
    ikona: ImageVector,
    oznaka: String,
    vrednost: String,
    boje: Pair<Color, Color>,
    modifier: Modifier = Modifier
) {
    val (pozadina, bojaIkone) = boje
    Row(
        modifier = modifier
            .background(pozadina, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(bojaIkone.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ikona, contentDescription = null, modifier = Modifier.size(18.dp), tint = bojaIkone)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(oznaka, color = FishBoje.PrigusenNaPlocici, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(vrednost, color = FishBoje.TekstNaPlocici, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Mali zaobljeni "tablet" sa ikonom i tekstom (uslovi, lajk, komentari)
@Composable
private fun Tablet(
    ikona: ImageVector,
    tekst: String,
    bojaIkone: Color = FishBoje.Akcenat,
    velikoSlovo: Boolean = false,
    ikonaModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = if (velikoSlovo) 10.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = bojaIkone,
            modifier = Modifier.size(if (velikoSlovo) 20.dp else 16.dp).then(ikonaModifier))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            tekst,
            color = if (velikoSlovo) FishBoje.TekstPrimarni else FishBoje.TekstSekundarni,
            fontSize = if (velikoSlovo) 15.sp else 12.sp,
            fontWeight = if (velikoSlovo) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun KomentarMehur(comment: Comment, vreme: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Avatar(ime = comment.authorUsername, slikaUrl = null, velicina = 32)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .background(
                    FishBoje.Kartica,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                )
                .border(
                    1.dp, FishBoje.IvicaKartice,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorUsername, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(vreme, color = FishBoje.TekstPriguseni, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(comment.text, color = FishBoje.TekstSekundarni, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PrijavaUlovaDialog(onDismiss: () -> Unit, onPotvrdi: (String, String) -> Unit) {
    var izabranRazlog by remember { mutableStateOf<String?>(null) }
    var opis by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FishBoje.Kartica,
        titleContentColor = FishBoje.TekstPrimarni,
        textContentColor = FishBoje.TekstSekundarni,
        icon = { Icon(Icons.Default.Report, contentDescription = null, tint = FishBoje.Greska) },
        title = { Text("Prijavi ulov", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Zašto prijavljuješ ovaj ulov?", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                RAZLOZI_PRIJAVE.forEach { razlog ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = izabranRazlog == razlog,
                                onClick = { izabranRazlog = razlog },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = izabranRazlog == razlog,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = FishBoje.Akcenat,
                                unselectedColor = FishBoje.TekstPriguseni
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(razlog, fontSize = 14.sp,
                            color = if (izabranRazlog == razlog) FishBoje.TekstPrimarni else FishBoje.TekstSekundarni)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = opis,
                    onValueChange = { if (it.length <= 500) opis = it },
                    label = { Text("Dodatno objašnjenje (opciono)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fishPoljeBoje(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { izabranRazlog?.let { onPotvrdi(it, opis) } },
                enabled = izabranRazlog != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FishBoje.Greska,
                    contentColor = Color.White,
                    disabledContainerColor = FishBoje.Krug,
                    disabledContentColor = FishBoje.TekstPriguseni
                )
            ) { Text("Prijavi", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži", color = FishBoje.TekstSekundarni) }
        }
    )
}

private fun formatBroj(vrednost: Double, jedinica: String): String {
    val tekst = if (vrednost % 1.0 == 0.0) vrednost.toInt().toString()
    else String.format(Locale.getDefault(), "%.2f", vrednost).trimEnd('0').trimEnd(',', '.')
    return "$tekst $jedinica"
}
