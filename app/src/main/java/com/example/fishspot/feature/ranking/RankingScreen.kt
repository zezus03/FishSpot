package com.example.fishspot.feature.ranking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.core.model.Korisnici

// NOVI IMPORTI UMESNO CLICKABLE
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    rankingViewModel: RankingViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onUserSelected: (userId: String) -> Unit
) {
    val userRanking by rankingViewModel.userRanking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rang Lista Korisnika") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Nazad")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(userRanking) { index, user ->
                RankingListItem(
                    rank = index + 1,
                    user = user,
                    onUserClick = { userId ->
                        onUserSelected(userId)
                    }
                )
            }
        }
    }
}

@Composable
fun RankingListItem(
    rank: Int,
    user: Korisnici,
    onUserClick: (userId: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // MAGIJA KOJA SPREČAVA PUCANJE:
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onUserClick(user.id) })
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank.",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(40.dp)
            )
            AsyncImage(
                model = user.slikaURL,
                contentDescription = "Profilna slika",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.userName, style = MaterialTheme.typography.titleMedium)
                Text(text = user.imePrezime, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "${user.poeni} poena",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}