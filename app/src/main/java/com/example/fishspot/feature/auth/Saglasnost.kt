package com.example.fishspot.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.example.fishspot.feature.profile.POLITIKA_PRIVATNOSTI_URL
import com.example.fishspot.feature.profile.USLOVI_KORISCENJA_URL

// Rečenica ispod dugmadi za registraciju i prijavu: nastavkom korisnik prihvata uslove i politiku privatnosti
@Composable
fun SaglasnostUslova(modifier: Modifier = Modifier, boja: Color = Color.Black.copy(alpha = 0.6f)) {
    val context = LocalContext.current
    val link = SpanStyle(color = Color(0xFF1B7A3E), fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
    val tekst = buildAnnotatedString {
        append("Nastavkom prihvataš ")
        pushStringAnnotation("url", USLOVI_KORISCENJA_URL)
        withStyle(link) { append("Uslove korišćenja") }
        pop()
        append(" i ")
        pushStringAnnotation("url", POLITIKA_PRIVATNOSTI_URL)
        withStyle(link) { append("Politiku privatnosti") }
        pop()
        append(".")
    }
    ClickableText(
        text = tekst,
        style = TextStyle(color = boja, fontSize = 12.sp, textAlign = TextAlign.Center),
        modifier = modifier,
        onClick = { pozicija ->
            tekst.getStringAnnotations("url", pozicija, pozicija).firstOrNull()?.let {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
            }
        }
    )
}
