package com.example.fishspot.feature.spots

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fishspot.core.model.Korisnici
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
    onPrijavi: (razlog: String, opis: String) -> Unit
) {
    var noviKomentar by remember { mutableStateOf("") }
    var prikaziPrijavu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var isLiked by remember(spot.likedBy) {
        mutableStateOf(currentUserId != null && spot.likedBy.contains(currentUserId))
    }
    var likeCount by remember(spot.likedBy) { mutableStateOf(spot.likedBy.size) }
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "heartScale"
    )
    val mojUlov = currentUserId != null && currentUserId == spot.authorId

    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            item {
                if (!spot.slikaUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = spot.slikaUrl,
                        contentDescription = "Slika ulova",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                AutorUlova(
                    ime = autor?.userName?.takeIf { it.isNotBlank() } ?: spot.authorUsername,
                    slikaUrl = autor?.slikaURL,
                    vreme = dateFormat.format(Date(spot.timestamp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vrsta ribe sa latinskim nazivom
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        spot.vrstaRibe.ifBlank { "Nepoznata vrsta" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (spot.latinskiNaziv.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            spot.latinskiNaziv,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (spot.naziv.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(spot.naziv, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Podaci o ulovu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PodatakUlova("Dužina", if (spot.duzinaRibe > 0) formatBroj(spot.duzinaRibe, "cm") else "—", Modifier.weight(1f))
                    PodatakUlova("Težina", if (spot.tezinaRibe > 0) formatBroj(spot.tezinaRibe, "kg") else "—", Modifier.weight(1f))
                    PodatakUlova("Tehnika", spot.tehnika.ifBlank { "—" }, Modifier.weight(1f))
                    PodatakUlova("Mamac", spot.mamac.ifBlank { "—" }, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lajk, broj komentara i prijava
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentUserId != null) {
                            isLiked = !isLiked
                            likeCount = if (isLiked) likeCount + 1 else likeCount - 1
                            onToggleLike(spot.id, isLiked)
                        }
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Sviđa mi se",
                            tint = if (isLiked) Color.Red else Color.Gray,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(heartScale)
                        )
                    }
                    Text("$likeCount", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Komentari", tint = Color.Gray,
                        modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${comments.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    Spacer(modifier = Modifier.weight(1f))

                    // Svoj ulov korisnik ne može da prijavi
                    if (!mojUlov && currentUserId != null) {
                        IconButton(
                            onClick = { prikaziPrijavu = true },
                            enabled = !vecPrijavljen
                        ) {
                            Icon(
                                Icons.Default.Report,
                                contentDescription = if (vecPrijavljen) "Ulov je već prijavljen" else "Prijavi ulov",
                                tint = if (vecPrijavljen) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                if (vecPrijavljen) {
                    Text(
                        "Prijavio si ovaj ulov. Hvala, pregledaćemo ga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Komentari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (comments.isEmpty()) {
                item {
                    Text("Nema komentara.", fontStyle = FontStyle.Italic, modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                items(comments) { comment ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comment.authorUsername, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dateFormat.format(Date(comment.timestamp)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(comment.text)
                    }
                    HorizontalDivider()
                }
            }
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = noviKomentar,
                onValueChange = { noviKomentar = it },
                placeholder = { Text("Dodaj komentar...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (noviKomentar.isNotBlank()) {
                        onAddComment(noviKomentar)
                        noviKomentar = ""
                    }
                },
                modifier = Modifier.size(52.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Pošalji", tint = Color.White)
            }
        }
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

@Composable
private fun AutorUlova(ime: String, slikaUrl: String?, vreme: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!slikaUrl.isNullOrBlank() && slikaUrl != "null") {
            AsyncImage(
                model = slikaUrl,
                contentDescription = "Profilna slika",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(ime, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(vreme, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PodatakUlova(oznaka: String, vrednost: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(oznaka, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(vrednost, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun PrijavaUlovaDialog(onDismiss: () -> Unit, onPotvrdi: (String, String) -> Unit) {
    var izabranRazlog by remember { mutableStateOf<String?>(null) }
    var opis by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Prijavi ulov") },
        text = {
            Column {
                Text("Zašto prijavljuješ ovaj ulov?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                RAZLOZI_PRIJAVE.forEach { razlog ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = izabranRazlog == razlog,
                                onClick = { izabranRazlog = razlog },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = izabranRazlog == razlog, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(razlog, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = opis,
                    onValueChange = { if (it.length <= 500) opis = it },
                    label = { Text("Dodatno objašnjenje (opciono)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { izabranRazlog?.let { onPotvrdi(it, opis) } },
                enabled = izabranRazlog != null
            ) { Text("Prijavi", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži") }
        }
    )
}

private fun formatBroj(vrednost: Double, jedinica: String): String {
    val tekst = if (vrednost % 1.0 == 0.0) vrednost.toInt().toString()
    else String.format(Locale.getDefault(), "%.2f", vrednost).trimEnd('0').trimEnd(',', '.')
    return "$tekst $jedinica"
}
