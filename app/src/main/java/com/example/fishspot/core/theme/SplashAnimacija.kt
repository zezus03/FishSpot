package com.example.fishspot.core.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.fishspot.R
import kotlinx.coroutines.delay

// Kadrovi animacije logoa (iz logo/ skice, poravnati); poslednji je pravi logo
private val KADROVI = listOf(
    R.drawable.splash_1, R.drawable.splash_2, R.drawable.splash_3,
    R.drawable.splash_4, R.drawable.splash_5, R.drawable.splash_6,
    R.drawable.splash_7, R.drawable.splash_8, R.drawable.splash_9
)
private const val TRAJANJE_KADRA = 190L
private const val PRETAPANJE = 170
private const val ZADRZI_LOGO = 750L
private const val NESTAJANJE = 300

/**
 * Kratka animacija logoa pri pokretanju aplikacije (oko 2,5 s).
 * Dodir na ekran je preskače.
 */
@Composable
fun SplashAnimacija(onGotovo: () -> Unit) {
    var kadar by remember { mutableIntStateOf(0) }
    var nestaje by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in 1 until KADROVI.size) {
            delay(TRAJANJE_KADRA)
            kadar = i
        }
        delay(ZADRZI_LOGO)
        nestaje = true
        delay(NESTAJANJE.toLong())
        onGotovo()
    }

    val providnost by animateFloatAsState(if (nestaje) 0f else 1f, tween(NESTAJANJE), label = "nestajanje")
    // Pravi logo na kraju blago "uskoči"
    val uvecanje by animateFloatAsState(if (kadar == KADROVI.lastIndex) 1f else 0.96f, tween(400), label = "uvecanje")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(providnost)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onGotovo() },
        contentAlignment = Alignment.Center
    ) {
        KADROVI.forEachIndexed { i, slika ->
            val vidljivost by animateFloatAsState(if (i == kadar) 1f else 0f, tween(PRETAPANJE), label = "kadar$i")
            if (vidljivost > 0f) {
                Image(
                    painter = painterResource(id = slika),
                    contentDescription = if (i == KADROVI.lastIndex) "FishSpot" else null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .scale(uvecanje)
                        .alpha(vidljivost)
                )
            }
        }
    }
}
