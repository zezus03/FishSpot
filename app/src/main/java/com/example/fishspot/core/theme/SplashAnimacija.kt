package com.example.fishspot.core.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.fishspot.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Slojevi logoa (iste veličine, sklapaju se u ceo logo). Tačke su u delovima slike (0..1).
private const val ODNOS_SLIKE = 1000f / 676f
private val STOPALA_RIBOLOVCA = TransformOrigin(0.74f, 0.70f) // oko njih se ribolovac naginje
private val SREDINA_RIBE = TransformOrigin(0.22f, 0.45f)
private const val POVRSINA_VODE = 0.70f // riba se ne vidi ispod ove linije dok izlazi iz vode

// Riba se crta samo iznad vode, pa izgleda kao da izranja
private val IznadVode = GenericShape { size, _ ->
    addRect(Rect(0f, 0f, size.width, size.height * POVRSINA_VODE))
}

/**
 * Animacija logoa pri pokretanju (oko 3 s): talasi se iscrtaju, ribolovac zabaci i povuče štap,
 * riba iskoči iz vode, pa se pojavi natpis. Dodir na ekran je preskače.
 */
@Composable
fun SplashAnimacija(onGotovo: () -> Unit) {
    val talasi = remember { Animatable(0f) }
    val covek = remember { Animatable(0f) }
    val povlacenje = remember { Animatable(0f) } // ugao naginjanja ribolovca (stepeni)
    val riba = remember { Animatable(0f) }       // 0 = u vodi, 1 = na mestu
    val ribaVidljiva = remember { Animatable(0f) }
    val tekst = remember { Animatable(0f) }
    val puls = remember { Animatable(1f) }
    val nestajanje = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { talasi.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
        delay(250)
        launch { covek.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
        delay(550)
        // Ribolovac se nagne unazad i povuče štap...
        launch {
            povlacenje.animateTo(-7f, tween(320, easing = FastOutSlowInEasing))
            povlacenje.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
        delay(180)
        // ...a riba iskoči iz vode i blago se zaljulja
        launch { ribaVidljiva.animateTo(1f, tween(180)) }
        launch { riba.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
        delay(650)
        launch { tekst.animateTo(1f, tween(500, easing = LinearOutSlowInEasing)) }
        delay(550)
        puls.animateTo(1.035f, tween(160))
        puls.animateTo(1f, tween(260))
        delay(500)
        nestajanje.animateTo(0f, tween(320))
        onGotovo()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = nestajanje.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onGotovo() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(ODNOS_SLIKE)
                .graphicsLayer { scaleX = puls.value; scaleY = puls.value }
        ) {
            // Ribolovac sa štapom
            Sloj(R.drawable.logo_sloj_covek) {
                alpha = covek.value
                translationY = (1f - covek.value) * 40.dp.toPx()
                transformOrigin = STOPALA_RIBOLOVCA
                rotationZ = povlacenje.value
            }
            // Riba sa prskanjem, izranja iza talasa
            Box(Modifier.fillMaxSize().clip(IznadVode)) {
                Sloj(R.drawable.logo_sloj_riba) {
                    alpha = ribaVidljiva.value
                    translationY = (1f - riba.value) * size.height * 0.40f
                    transformOrigin = SREDINA_RIBE
                    rotationZ = (1f - riba.value) * -35f
                    val s = 0.75f + 0.25f * riba.value
                    scaleX = s; scaleY = s
                }
            }
            // Talasi se iscrtavaju sleva nadesno
            Image(
                painter = painterResource(R.drawable.logo_sloj_talasi),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (talasi.value * 1.5f).coerceAtMost(1f) }
                    .drawWithContent {
                        clipRect(right = size.width * talasi.value) { this@drawWithContent.drawContent() }
                    }
            )
            // Natpis FishSpot
            Sloj(R.drawable.logo_sloj_tekst, opis = "FishSpot") {
                alpha = tekst.value
                translationY = (1f - tekst.value) * 30.dp.toPx()
                val s = 0.9f + 0.1f * tekst.value
                scaleX = s; scaleY = s
            }
        }
    }
}

@Composable
private fun Sloj(@DrawableRes slika: Int, opis: String? = null, animacija: GraphicsLayerScope.() -> Unit) {
    Image(
        painter = painterResource(slika),
        contentDescription = opis,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize().graphicsLayer(animacija)
    )
}
