package com.example.fishspot.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje

// Pitanja koja se nude na početku razgovora (šalju se jednim dodirom)
private val PREDLOZENA_PITANJA = listOf(
    "Koji mamac je najbolji za šarana?",
    "Kada najbolje grize smuđ?",
    "Kako da vežem udicu za štapić?",
    "Kakvo vreme je dobro za pecanje?"
)

@Composable
fun BotChatScreen(
    botViewModel: BotChatViewModel = viewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val messages by botViewModel.messages.collectAsState()
    val isThinking by botViewModel.isThinking.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    // Kada deda počne da razmišlja, skrolujemo dole da animacija ne pobegne ispod ekrana
    LaunchedEffect(isThinking) {
        if (isThinking && messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun posalji(tekst: String) {
        if (tekst.isNotBlank() && !isThinking) {
            botViewModel.sendQuestionToBot(tekst)
            inputText = ""
        }
    }

    // Na početku je u razgovoru samo dedin pozdrav
    val pocetakRazgovora = messages.all { it.sender == BotSender.BOT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina)
            .imePadding() // podiže čet kada se pojavi tastatura
    ) {
        FishZaglavlje(
            oznaka = "FishSpot • AI savetnik",
            naslov = "Deda Pera",
            onNazad = onNavigateBack,
            slika = { DedaAvatar(48.dp) },
            podnaslov = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (isThinking) FishBoje.Upozorenje else FishBoje.Akcenat, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThinking) "razmišlja..." else "stari alas, tu je za savet",
                        color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { message -> BotChatBubble(message = message) }

            if (pocetakRazgovora && !isThinking) {
                item {
                    Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                        NaslovSekcije("Pitaj dedu")
                        Spacer(Modifier.height(6.dp))
                        PREDLOZENA_PITANJA.forEach { pitanje ->
                            Text(
                                pitanje,
                                color = FishBoje.TekstPrimarni,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(FishBoje.Kartica)
                                    .border(1.dp, FishBoje.AkcenatTamni, RoundedCornerShape(16.dp))
                                    .clickable { posalji(pitanje) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        // Deda razmišlja: tri tačkice koje pulsiraju
        AnimatedVisibility(
            visible = isThinking,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DedaAvatar(32.dp)
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(FishBoje.Kartica, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                        .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    TackiceRazmisljanja()
                }
            }
        }

        // Polje za pitanje
        Box(modifier = Modifier.fillMaxWidth().background(FishBoje.Kartica)) {
            HorizontalDivider(color = FishBoje.IvicaKartice)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Pitaj dedu Peru...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = fishPoljeBoje(),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                val mozeSlanje = inputText.isNotBlank() && !isThinking
                IconButton(
                    onClick = { posalji(inputText) },
                    enabled = mozeSlanje,
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (mozeSlanje) FishBoje.Akcenat else FishBoje.Krug, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Pošalji",
                        tint = if (mozeSlanje) FishBoje.Pozadina else FishBoje.TekstPriguseni
                    )
                }
            }
        }
    }
}

@Composable
fun BotChatBubble(message: BotChatMessage) {
    val isUser = message.sender == BotSender.USER
    val cistTekst = message.text.replace("**", "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            DedaAvatar(34.dp)
            Spacer(Modifier.width(8.dp))
        }
        val oblik = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

        Text(
            text = cistTekst,
            color = if (isUser) FishBoje.Pozadina else FishBoje.TekstPrimarni,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(if (isUser) FishBoje.Akcenat else FishBoje.Kartica, oblik)
                .then(if (isUser) Modifier else Modifier.border(1.dp, FishBoje.IvicaKartice, oblik))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun DedaAvatar(velicina: Dp) {
    Image(
        painter = painterResource(id = R.drawable.ic_deda_pera),
        contentDescription = "Deda Pera",
        modifier = Modifier
            .size(velicina)
            .clip(CircleShape)
            .border(2.dp, FishBoje.Akcenat, CircleShape),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun TackiceRazmisljanja() {
    val prelaz = rememberInfiniteTransition(label = "tackice")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val prozirnost by prelaz.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse),
                label = "tackica$i"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(prozirnost)
                    .background(FishBoje.Akcenat, CircleShape)
            )
        }
    }
}
