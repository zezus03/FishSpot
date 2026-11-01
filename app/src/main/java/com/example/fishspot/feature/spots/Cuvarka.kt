package com.example.fishspot.feature.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.feature.fish.VrstaRibe

// Prekidač "Čuvarka" u zaglavlju odeljka Riba: više riba i vrsta zajedno, ukupna težina, bez dužine
@Composable
fun CuvarkaDugme(ukljuceno: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (ukljuceno) FishBoje.Akcenat else FishBoje.Akcenat.copy(alpha = 0.08f))
            .border(1.dp, if (ukljuceno) FishBoje.Akcenat else FishBoje.AkcenatTamni, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_cuvarka), contentDescription = null,
            tint = if (ukljuceno) FishBoje.Pozadina else FishBoje.Akcenat, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Čuvarka", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (ukljuceno) FishBoje.Pozadina else FishBoje.Akcenat
        )
    }
}

// Izabrane vrste u čuvarki kao čipovi sa ×
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VrsteCuvarke(vrste: List<VrstaRibe>, onUkloni: (VrstaRibe) -> Unit) {
    if (vrste.isEmpty()) {
        Text("Dodaj vrste koje su u čuvarki (najviše $MAKS_VRSTA_CUVARKE)", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vrste.forEach { vrsta ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FishBoje.Akcenat.copy(alpha = 0.15f))
                    .border(1.dp, FishBoje.AkcenatTamni, RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(vrsta.naziv, color = FishBoje.TekstPrimarni, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier.padding(start = 4.dp).size(24.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { onUkloni(vrsta) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Ukloni ${vrsta.naziv}", tint = FishBoje.TekstSekundarni,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// Provera težine čuvarke (ukupno do 30 kg); null znači da je sve u redu
fun proveraCuvarke(vrste: List<VrstaRibe>, tezinaKg: Double?): String? = when {
    vrste.isEmpty() -> "Izaberi bar jednu vrstu u čuvarki"
    tezinaKg == null || tezinaKg <= 0 -> "Unesi ukupnu težinu čuvarke"
    tezinaKg > MAKS_TEZINA_CUVARKE -> "Čuvarka može imati najviše ${MAKS_TEZINA_CUVARKE.toInt()} kg"
    else -> null
}
