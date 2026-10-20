package com.example.fishspot.core.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Kratka poruka na dnu ekrana u stilu aplikacije (umesto sistemskog Toast-a, koji na
 * nekim telefonima prikazuje i ikonicu aplikacije). Poziv: FishPoruka.prikazi("Sačuvano").
 */
object FishPoruka {
    internal data class Poruka(val tekst: String, val dugo: Boolean, val greska: Boolean)

    internal val red = Channel<Poruka>(capacity = 8, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    // Poruke o greškama dobijaju crvenu ikonicu
    private val reciGreske = listOf("greška", "nije uspel", "nije moguć", "nije mogl", "nije dostup", "potrebna dozvola", "neuspe")

    fun prikazi(tekst: String, dugo: Boolean = false) {
        val malim = tekst.lowercase()
        red.trySend(Poruka(tekst, dugo, reciGreske.any { malim.contains(it) }))
    }
}

// Postavlja se jednom, preko celog sadržaja (MainActivity)
@Composable
fun FishPorukaHost() {
    var trenutna by remember { mutableStateOf<FishPoruka.Poruka?>(null) }
    var vidljiva by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (poruka in FishPoruka.red) {
            trenutna = poruka
            vidljiva = true
            delay(if (poruka.dugo) 3500 else 2200)
            vidljiva = false
            delay(250) // da se animacija nestajanja završi pre sledeće poruke
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = vidljiva,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            val poruka = trenutna ?: return@AnimatedVisibility
            val boja = if (poruka.greska) FishBoje.Greska else FishBoje.Akcenat
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp))
                    .background(FishBoje.Kartica, RoundedCornerShape(16.dp))
                    .border(1.dp, boja.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier.size(26.dp).background(boja.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (poruka.greska) Icons.Default.PriorityHigh else Icons.Default.Check,
                        contentDescription = null,
                        tint = boja,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(poruka.tekst, color = FishBoje.TekstPrimarni, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}
