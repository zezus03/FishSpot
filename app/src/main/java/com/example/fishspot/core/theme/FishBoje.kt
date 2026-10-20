package com.example.fishspot.core.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta aplikacije (tamno zelena, kao ekran vremenske prognoze)
object FishBoje {
    val Pozadina = Color(0xFF0B1612)
    val Kartica = Color(0xFF132218)
    val IvicaKartice = Color(0xFF1E3328)
    val Polje = Color(0xFF0F1E18)
    val Krug = Color(0xFF1B3028)
    val ZaglavljeGore = Color(0xFF142D22)

    val Akcenat = Color(0xFF2ECC71)
    val AkcenatTamni = Color(0xFF1A7A44)
    val Greska = Color(0xFFE74C3C)
    val Upozorenje = Color(0xFFF1C40F)
    val Zvezdica = Color(0xFFFFB300)

    val TekstPrimarni = Color.White
    val TekstSekundarni = Color(0xFFB0BDB9)
    val TekstPriguseni = Color(0xFF6B8078)

    // Pastelne pločice sa obojenom ikonom (pozadina, ikona)
    val PlavaPlocica = Color(0xFFECF4FB) to Color(0xFF1565C0)
    val ZelenaPlocica = Color(0xFFEDF7F0) to Color(0xFF1B6B3A)
    val LjubicastaPlocica = Color(0xFFF2E8FC) to Color(0xFF6A1B9A)
    val NarandzastaPlocica = Color(0xFFFBEDE9) to Color(0xFFBF360C)
    val TirkiznaPlocica = Color(0xFFE0F4F1) to Color(0xFF00695C)
    val TekstNaPlocici = Color(0xFF1A1A1A)
    val PrigusenNaPlocici = Color(0xFF555555)
}

// Naslov sekcije: mala, podebljana, prigušena VELIKA slova
@Composable
fun NaslovSekcije(tekst: String, modifier: Modifier = Modifier) {
    Text(
        text = tekst.uppercase(),
        color = FishBoje.TekstPriguseni,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(bottom = 2.dp)
    )
}

@Composable
fun fishPoljeBoje() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FishBoje.TekstPrimarni,
    unfocusedTextColor = FishBoje.TekstPrimarni,
    focusedContainerColor = FishBoje.Polje,
    unfocusedContainerColor = FishBoje.Polje,
    errorContainerColor = FishBoje.Polje,
    focusedBorderColor = FishBoje.Akcenat,
    unfocusedBorderColor = FishBoje.IvicaKartice,
    errorBorderColor = FishBoje.Greska,
    focusedLabelColor = FishBoje.Akcenat,
    unfocusedLabelColor = FishBoje.TekstSekundarni,
    errorLabelColor = FishBoje.Greska,
    focusedPlaceholderColor = FishBoje.TekstPriguseni,
    unfocusedPlaceholderColor = FishBoje.TekstPriguseni,
    focusedLeadingIconColor = FishBoje.Akcenat,
    unfocusedLeadingIconColor = FishBoje.TekstPriguseni,
    focusedTrailingIconColor = FishBoje.TekstSekundarni,
    unfocusedTrailingIconColor = FishBoje.TekstPriguseni,
    focusedSuffixColor = FishBoje.TekstSekundarni,
    unfocusedSuffixColor = FishBoje.TekstPriguseni,
    focusedSupportingTextColor = FishBoje.TekstPriguseni,
    unfocusedSupportingTextColor = FishBoje.TekstPriguseni,
    errorSupportingTextColor = FishBoje.Greska,
    cursorColor = FishBoje.Akcenat,
    errorTextColor = FishBoje.TekstPrimarni,
    errorTrailingIconColor = FishBoje.Greska,
    errorLeadingIconColor = FishBoje.Greska
)

@Composable
fun fishCipBoje() = FilterChipDefaults.filterChipColors(
    containerColor = FishBoje.Polje,
    labelColor = FishBoje.TekstSekundarni,
    selectedContainerColor = FishBoje.Akcenat,
    selectedLabelColor = Color(0xFF0B1612),
    selectedLeadingIconColor = Color(0xFF0B1612)
)
