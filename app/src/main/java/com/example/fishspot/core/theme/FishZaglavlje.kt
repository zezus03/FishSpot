package com.example.fishspot.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zaglavlje ekrana u stilu vremenske prognoze: mala oznaka VELIKIM SLOVIMA, naslov sa ikonom
 * u krugu, okruglo dugme za nazad i prelaz iz tamno zelene u boju pozadine.
 * [ikona] ili [slika] (npr. avatar Deda Pere) se prikazuju levo od naslova.
 */
@Composable
fun FishZaglavlje(
    oznaka: String,
    naslov: String,
    onNazad: (() -> Unit)?,
    ikona: ImageVector? = null,
    slika: (@Composable () -> Unit)? = null,
    podnaslov: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(FishBoje.ZaglavljeGore, FishBoje.Pozadina)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onNazad != null) {
                    IconButton(
                        onClick = onNazad,
                        modifier = Modifier
                            .size(36.dp)
                            .background(FishBoje.Krug, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazad",
                            tint = FishBoje.TekstSekundarni, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    oznaka.uppercase(),
                    color = FishBoje.TekstPriguseni,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    slika != null -> slika()
                    ikona != null -> Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(FishBoje.Krug, CircleShape)
                            .border(1.dp, FishBoje.IvicaKartice, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(20.dp))
                    }
                }
                if (slika != null || ikona != null) Spacer(Modifier.width(12.dp))
                Column {
                    Text(naslov, color = FishBoje.TekstPrimarni, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    podnaslov?.invoke()
                }
            }
        }
    }
}
