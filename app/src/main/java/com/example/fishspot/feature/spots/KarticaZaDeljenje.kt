package com.example.fishspot.feature.spots

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Kartica ulova za Instagram/WhatsApp status (1080x1920), crta se na telefonu i deli preko Android menija.
// "Potvrđen ulov" se prikazuje samo kad je slika prošla proveru i ulov dobio poene (za 5+ kg: odobrio admin).
object KarticaZaDeljenje {
    private const val W = 1080
    private const val H = 1920

    private val POZADINA = 0xFF0B1612.toInt()
    private val POZADINA_DOLE = 0xFF132A1F.toInt()
    private val KARTICA = 0xFF132218.toInt()
    private val IVICA = 0xFF1E3328.toInt()
    private val AKCENAT = 0xFF2ECC71.toInt()
    private val NARANDZASTA = 0xFFFF7A1A.toInt()
    private val SIVA = 0xFF8FA39B.toInt()
    private val BELA = 0xFFFFFFFF.toInt()

    private val uzak = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val obican = Typeface.create("sans-serif", Typeface.NORMAL)
    private val podebljan = Typeface.create("sans-serif", Typeface.BOLD)

    suspend fun nacrtaj(
        context: Context,
        ulov: FishSpot,
        ime: String,
        potvrdjen: Boolean,
        prikaziMesto: Boolean
    ): Bitmap = withContext(Dispatchers.IO) {
        val slika = ulov.slikaUrl?.let { ucitajSliku(context, it) }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Pozadina sa blagim prelazom
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), POZADINA, POZADINA_DOLE, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // Zaglavlje: logo, naziv, oznaka potvrde
        ucitajLogo(context)?.let { logo ->
            val r = RectF(60f, 60f, 156f, 156f)
            c.save()
            val put = android.graphics.Path().apply { addRoundRect(r, 24f, 24f, android.graphics.Path.Direction.CW) }
            c.clipPath(put)
            c.drawBitmap(logo, null, r, p)
            c.restore()
        }
        tekst(c, "FISHSPOT", 184f, 128f, 46f, BELA, uzak)
        if (potvrdjen) {
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; typeface = podebljan; color = 0xFF1B6B3A.toInt() }
            val tekstSirina = t.measureText("Potvrđen ulov")
            val desno = W - 60f
            val pill = RectF(desno - tekstSirina - 100f, 74f, desno, 142f)
            p.color = 0xFFEDF7F0.toInt()
            c.drawRoundRect(pill, 34f, 34f, p)
            p.color = AKCENAT
            c.drawCircle(pill.left + 40f, pill.centerY(), 16f, p)
            val kvacica = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BELA; strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            c.drawLine(pill.left + 32f, pill.centerY(), pill.left + 38f, pill.centerY() + 7f, kvacica)
            c.drawLine(pill.left + 38f, pill.centerY() + 7f, pill.left + 49f, pill.centerY() - 6f, kvacica)
            c.drawText("Potvrđen ulov", pill.left + 70f, pill.centerY() + 11f, t)
        }

        // Fotografija ulova
        val foto = RectF(60f, 200f, W - 60f, 1180f)
        p.color = KARTICA
        c.drawRoundRect(foto, 44f, 44f, p)
        if (slika != null) {
            c.save()
            c.clipPath(android.graphics.Path().apply { addRoundRect(foto, 44f, 44f, android.graphics.Path.Direction.CW) })
            c.drawBitmap(slika, iseci(slika, foto.width() / foto.height()), foto, p)
            c.restore()
        }

        // Vrsta i težina
        val naslov = if (ulov.cuvarka) "ČUVARKA" else ulov.vrstaRibe.uppercase(Locale("sr"))
        var y = 1300f
        tekst(c, skrati(naslov, 120f, uzak, W - 120f), 60f, y, 120f, BELA, uzak)
        if (ulov.cuvarka) {
            y += 64f
            tekst(c, skrati(ulov.nazivVrsta, 44f, obican, W - 120f), 60f, y, 44f, SIVA, obican)
        }
        y += 190f
        val brojTezine = broj(ulov.tezinaRibe)
        val tezinaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 190f; typeface = uzak; color = NARANDZASTA }
        c.drawText(brojTezine, 56f, y, tezinaPaint)
        tekst(c, "kg", 56f + tezinaPaint.measureText(brojTezine) + 16f, y, 76f, NARANDZASTA, uzak)

        // Četiri polja sa podacima
        val datum = SimpleDateFormat("d. MMMM yyyy.", Locale("sr", "Latn")).format(Date(ulov.timestamp))
        val polja = listOf(
            if (ulov.cuvarka) "VRSTE" to "${ulov.cuvarkaVrste.size}" else "DUŽINA" to (if (ulov.duzinaRibe > 0) "${broj(ulov.duzinaRibe)} cm" else "—"),
            "MAMAC" to ulov.mamac.ifBlank { "—" },
            if (prikaziMesto && ulov.naziv.isNotBlank()) "MESTO" to ulov.naziv else "DATUM" to datum,
            "ULOVIO" to "@$ime"
        )
        val vrh = y + 50f
        val sirina = (W - 120f - 24f) / 2
        polja.forEachIndexed { i, (oznaka, vrednost) ->
            val x = 60f + (i % 2) * (sirina + 24f)
            val gore = vrh + (i / 2) * 164f
            val r = RectF(x, gore, x + sirina, gore + 140f)
            p.color = KARTICA
            c.drawRoundRect(r, 28f, 28f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = IVICA
            c.drawRoundRect(r, 28f, 28f, p)
            p.style = Paint.Style.FILL
            tekst(c, oznaka, x + 30f, gore + 52f, 26f, SIVA, podebljan)
            tekst(c, skrati(vrednost, 40f, podebljan, sirina - 60f), x + 30f, gore + 108f, 40f, BELA, podebljan)
        }

        // Podnožje
        val dno = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = podebljan; color = AKCENAT; textAlign = Paint.Align.CENTER }
        c.drawText("Upiši i ti svoj ulov u FishSpot · $datum", W / 2f, H - 40f, dno)
        bmp
    }

    // Sačuva karticu i otvori Android meni za deljenje
    fun podeli(context: Context, kartica: Bitmap, spotId: String) {
        val folder = File(context.cacheDir, "deljenje").apply { mkdirs() }
        val fajl = File(folder, "fishspot_ulov_$spotId.jpg")
        fajl.outputStream().use { kartica.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fajlovi", fajl)
        val namera = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Moj ulov u FishSpot-u 🎣")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(namera, "Podeli ulov").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun tekst(c: Canvas, s: String, x: Float, y: Float, velicina: Float, boja: Int, font: Typeface) {
        c.drawText(s, x, y, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = velicina; color = boja; typeface = font })
    }

    private fun skrati(s: String, velicina: Float, font: Typeface, sirina: Float): String =
        TextUtils.ellipsize(s, TextPaint().apply { textSize = velicina; typeface = font }, sirina, TextUtils.TruncateAt.END).toString()

    // Deo slike koji popunjava okvir (kao ContentScale.Crop)
    private fun iseci(b: Bitmap, odnos: Float): android.graphics.Rect {
        val odnosSlike = b.width.toFloat() / b.height
        return if (odnosSlike > odnos) {
            val w = (b.height * odnos).toInt()
            android.graphics.Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height)
        } else {
            val h = (b.width / odnos).toInt()
            android.graphics.Rect(0, (b.height - h) / 2, b.width, (b.height + h) / 2)
        }
    }

    private fun broj(v: Double): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').replace('.', ',')

    private suspend fun ucitajSliku(context: Context, url: String): Bitmap? = try {
        val zahtev = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).size(1200).build()
        (coil.Coil.imageLoader(context).execute(zahtev).drawable)?.toBitmap()
    } catch (_: Exception) {
        null
    }

    private fun ucitajLogo(context: Context): Bitmap? = try {
        androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(192, 192)
    } catch (_: Exception) {
        null
    }
}

// Pregled kartice pre deljenja: prekidač za mesto (podrazumevano sakriveno) i dugme Podeli
@Composable
fun PodeliUlovDialog(ulov: FishSpot, ime: String, potvrdjen: Boolean, onZatvori: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prikaziMesto by remember { mutableStateOf(false) }
    var kartica by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(prikaziMesto) {
        kartica = KarticaZaDeljenje.nacrtaj(context, ulov, ime, potvrdjen, prikaziMesto)
    }

    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = FishBoje.Pozadina
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Podeli ulov", color = FishBoje.TekstPrimarni, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onZatvori) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                        .background(FishBoje.Kartica),
                    contentAlignment = Alignment.Center
                ) {
                    val slika: ImageBitmap? = remember(kartica) { kartica?.asImageBitmap() }
                    if (slika != null) Image(slika, contentDescription = "Kartica ulova", modifier = Modifier.fillMaxSize())
                    else CircularProgressIndicator(color = FishBoje.Akcenat)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Prikaži mesto", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                        Text("Bez toga se umesto mesta vidi datum", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    }
                    Switch(checked = prikaziMesto, onCheckedChange = { prikaziMesto = it }, colors = SwitchDefaults.colors(
                        checkedThumbColor = FishBoje.Pozadina, checkedTrackColor = FishBoje.Akcenat,
                        uncheckedThumbColor = FishBoje.TekstPriguseni, uncheckedTrackColor = FishBoje.Polje,
                        uncheckedBorderColor = FishBoje.IvicaKartice
                    ))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val k = kartica ?: return@Button
                        scope.launch { KarticaZaDeljenje.podeli(context, k, ulov.id) }
                    },
                    enabled = kartica != null,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Podeli", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
