package com.example.fishspot.userinterface

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishspot.R
import com.example.fishspot.viewmodel.BotChatMessage
import com.example.fishspot.viewmodel.BotSender
import com.example.fishspot.viewmodel.BotChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotChatScreen(botViewModel: BotChatViewModel = viewModel()) {
    val messages by botViewModel.messages.collectAsState()
    val isThinking by botViewModel.isThinking.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    // Kada deda počne da razmišlja, skrolujemo dole da animacija ne pobegne ispod ekrana
    LaunchedEffect(isThinking) {
        if (isThinking && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_deda_pera),
                            contentDescription = "Deda Pera Logo Gornji",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF6C4E3D), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Pera Alas AI",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFD6D6)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding() // OVO PODIŽE ČET KADA SE POJAVI TASTATURA!
                .background(Color(0xFFF5F6F8))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { message ->
                    BotChatBubble(message = message)
                }
            }
// ================== ANIMACIJA RAZMIŠLJANJA (IZNAD POLJA ZA KUCAJ) ==================
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_deda_pera),
                        contentDescription = "Deda Pera razmišlja",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF6C4E3D), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFE9ECEF),
                        shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pera Alas razmislja o odgovoru... 🎣",
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF6C4E3D),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Sve znam sinko...", color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF6C4E3D),
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            botViewModel.sendQuestionToBot(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF6C4E3D)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Pošalji",
                        tint = Color.White
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
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.ic_deda_pera),
                contentDescription = "Deda Pera Avatar",
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(45.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF6C4E3D), CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFFE9ECEF)
        val textColor = if (isUser) Color.White else Color.Black
        val shape = if (isUser) {
            RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
        } else {
            RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
        }

        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(
                text = cistTekst,
                color = textColor,
                fontSize = 15.sp,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}