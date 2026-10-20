package com.example.fishspot.core.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.fishspot.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Slojevi logoa su iste veličine i sklapaju se u ceo logo. Tačke su u delovima slike (0..1).
private const val ODNOS_SLIKE = 1000f / 676f
private val STOPALA = TransformOrigin(0.738f, 0.695f)  // ribolovac se naginje oko stopala
private val SREDINA_RIBE = TransformOrigin(0.20f, 0.41f)
private val UDICA = TransformOrigin(0.2615f, 0.201f)    // upecana riba se klati oko udice
private val MESTO_SKOKA = Offset(0.215f, 0.633f)       // gde riba probija površinu
private const val POVRSINA = 0.65f                     // ispod ove linije riba je još u vodi

// Momenti u sceni (ms od početka)
private const val T_SKOK = 850L
private const val T_KVACENJE = 1450L
private const val T_NATPIS = 1900L
private const val T_KRAJ = 3300L

// Riba se crta samo iznad vode, pa izgleda kao da izranja
private val IznadVode = GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, size.height * POVRSINA)) }

// Kapljica koja leti iz vode: brzina i gravitacija su u širinama slike po sekundi
private class Kap(
    val pocetak: Float, val vx: Float, val vy: Float,
    val poluprecnik: Float, val zivot: Float, val boja: Color, val izvor: Offset
)

private val BOJE_KAPI = listOf(Color(0xFF7CFF3A), Color(0xFF39E62B), Color(0xFFB8FF8A), Color(0xFF2ECC71), Color.White)

private fun napraviKapi(): List<Kap> {
    val r = Random(7)
    fun kapi(broj: Int, pocetak: Float, izvor: Offset, brzinaOd: Float, brzinaDo: Float, ugaoOd: Float, ugaoDo: Float, velicina: Float) =
        List(broj) {
            val ugao = Math.toRadians((ugaoOd + r.nextFloat() * (ugaoDo - ugaoOd)).toDouble())
            val brzina = brzinaOd + r.nextFloat() * (brzinaDo - brzinaOd)
            Kap(
                pocetak = pocetak + r.nextFloat() * 0.12f,
                vx = (cos(ugao) * brzina).toFloat(),
                vy = (sin(ugao) * brzina).toFloat(),
                poluprecnik = velicina * (0.4f + r.nextFloat() * 0.8f),
                zivot = 0.7f + r.nextFloat() * 0.6f,
                boja = BOJE_KAPI[r.nextInt(BOJE_KAPI.size)],
                izvor = izvor
            )
        }
    return kapi(46, T_SKOK / 1000f, MESTO_SKOKA, 0.18f, 0.62f, -165f, -15f, 0.0075f) +
        // varnice kad riba zagrize udicu
        kapi(14, T_KVACENJE / 1000f, Offset(UDICA.pivotFractionX, UDICA.pivotFractionY), 0.08f, 0.28f, -180f, 180f, 0.004f)
}

/**
 * Scena pri pokretanju (oko 3,3 s): pojavi se ribolovac, riba iskoči iz vode uz prskanje kapljica,
 * zagrize udicu dok ribolovac trzne štap, pa se pojavi natpis. Dodir na ekran je preskače.
 */
@Composable
fun SplashAnimacija(onGotovo: () -> Unit) {
    val talasi = remember { Animatable(0f) }
    val covek = remember { Animatable(0f) }
    val trzaj = remember { Animatable(0f) }      // naginjanje ribolovca u stepenima
    val skok = remember { Animatable(0f) }       // 0 = riba u vodi, 1 = kod udice
    val prskanje = remember { Animatable(0f) }
    val klacenje = remember { Animatable(0f) }   // ljuljanje upecane ribe oko udice
    val bljesak = remember { Animatable(0f) }
    val natpis = remember { Animatable(0f) }
    val puls = remember { Animatable(1f) }
    val nestajanje = remember { Animatable(1f) }
    var vreme by remember { mutableFloatStateOf(0f) } // sekunde od početka, za kapljice
    val kapi = remember { napraviKapi() }

    LaunchedEffect(Unit) {
        launch {
            val start = withFrameNanos { it }
            while (isActive) withFrameNanos { vreme = (it - start) / 1_000_000_000f }
        }
        launch { talasi.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        launch { delay(100); covek.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }

        delay(T_SKOK)
        // Riba izleti iz vode: brzo na početku, usporava pri vrhu
        launch { skok.animateTo(1f, tween((T_KVACENJE - T_SKOK).toInt(), easing = CubicBezierEasing(0.15f, 0.75f, 0.3f, 1f))) }
        launch { delay(40); prskanje.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 140f)) }

        delay(T_KVACENJE - T_SKOK)
        // Zagrizla: bljesak na udici, ribolovac trzne štap, riba se zaljulja
        launch { bljesak.animateTo(1f, tween(90)); bljesak.animateTo(0f, tween(380)) }
        launch {
            trzaj.animateTo(4f, tween(170, easing = FastOutSlowInEasing)) // zabaci se unazad
            trzaj.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 90f))
        }
        launch {
            klacenje.animateTo(12f, tween(140))
            klacenje.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 110f))
        }

        delay(T_NATPIS - T_KVACENJE)
        launch { natpis.animateTo(1f, tween(550, easing = LinearOutSlowInEasing)) }
        delay(700)
        puls.animateTo(1.03f, tween(170))
        puls.animateTo(1f, tween(280))
        delay(T_KRAJ - T_NATPIS - 700 - 450)
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
                translationY = (1f - covek.value) * 24.dp.toPx()
                transformOrigin = STOPALA
                rotationZ = trzaj.value
            }

            // Riba: izranja iza talasa, pa se klati na udici
            Box(Modifier.fillMaxSize().clip(IznadVode)) {
                // Upecana riba prati vrh štapa kad ribolovac trzne (ista rotacija kao ribolovac)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { transformOrigin = STOPALA; rotationZ = trzaj.value }
                        .graphicsLayer { transformOrigin = UDICA; rotationZ = klacenje.value }
                ) {
                    Sloj(R.drawable.logo_sloj_riba) {
                        val p = skok.value
                        alpha = (p * 5f).coerceAtMost(1f)
                        translationX = (1f - p) * -0.05f * size.width
                        translationY = (1f - p) * 0.45f * size.height
                        transformOrigin = SREDINA_RIBE
                        rotationZ = (1f - p) * -28f
                    }
                }
            }

            // Prskanje vode oko ribe
            Sloj(R.drawable.logo_sloj_prskanje) {
                alpha = prskanje.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(MESTO_SKOKA.x, MESTO_SKOKA.y)
                scaleX = 0.3f + 0.7f * prskanje.value
                scaleY = 0.15f + 0.85f * prskanje.value
            }

            // Talasi se iscrtavaju sleva nadesno
            Image(
                painter = painterResource(R.drawable.logo_sloj_talasi),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (talasi.value * 1.6f).coerceAtMost(1f) }
                    .drawWithContent { clipRect(right = size.width * talasi.value) { this@drawWithContent.drawContent() } }
            )

            // Kapljice i bljesak na udici
            Canvas(Modifier.fillMaxSize()) {
                kapi.forEach { crtajKap(it, vreme) }
                if (bljesak.value > 0f) {
                    val centar = Offset(size.width * UDICA.pivotFractionX, size.height * UDICA.pivotFractionY)
                    val r = size.width * (0.02f + 0.05f * bljesak.value)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = bljesak.value), Color(0xFF7CFF3A).copy(alpha = 0.5f * bljesak.value), Color.Transparent),
                            center = centar, radius = r
                        ),
                        radius = r, center = centar
                    )
                }
            }

            // Natpis FishSpot
            Sloj(R.drawable.logo_sloj_tekst, opis = "FishSpot") {
                alpha = natpis.value
                translationY = (1f - natpis.value) * 28.dp.toPx()
                val s = 0.92f + 0.08f * natpis.value
                scaleX = s; scaleY = s
            }
        }
    }
}

private const val GRAVITACIJA = 1.5f

private fun DrawScope.crtajKap(kap: Kap, vreme: Float) {
    val t = vreme - kap.pocetak
    if (t <= 0f || t >= kap.zivot) return
    val w = size.width
    val x = kap.izvor.x * w + kap.vx * w * t
    val y = kap.izvor.y * size.height + kap.vy * w * t + 0.5f * GRAVITACIJA * w * t * t
    // Kap koja padne nazad u vodu nestaje
    if (y > size.height * POVRSINA + 6f && kap.vy * w + GRAVITACIJA * w * t > 0f && kap.izvor == MESTO_SKOKA) return
    val ostatak = 1f - t / kap.zivot
    val providnost = (ostatak / 0.4f).coerceAtMost(1f)
    val r = kap.poluprecnik * w
    drawCircle(kap.boja.copy(alpha = 0.25f * providnost), radius = r * 2.2f, center = Offset(x, y))
    drawCircle(kap.boja.copy(alpha = providnost), radius = r, center = Offset(x, y))
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
