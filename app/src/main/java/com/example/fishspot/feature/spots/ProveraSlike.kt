package com.example.fishspot.feature.spots

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Locale

// Odakle je slika ulova; vrednost se upisuje u ulov (izvorSlike), a poene po njoj dodeljuje server
enum class IzvorSlike(val kod: String, val donosiPoene: Boolean, val opis: String) {
    APLIKACIJA("aplikacija", true, "Slikano u aplikaciji: +20% poena"),
    KAMERA("kamera", true, "Slika sa kamere telefona: donosi poene"),
    POSLATA("poslata", true, "Poslata slika (Viber, WhatsApp, Messenger): donosi poene"),
    AI("ai", false, "Slika je napravljena ili izmenjena veštačkom inteligencijom (AI) i ne donosi poene"),
    SCREENSHOT("screenshot", false, "Snimak ekrana ne donosi poene"),
    STARA("stara", false, "Slika je starija od ${ProveraSlike.NAJVISE_DANA} dana i ne donosi poene"),
    NEPOZNATA("nepoznata", false, "Slika nema podatke sa kamere (npr. skinuta sa interneta) i ne donosi poene"),
}

// Određuje izvor slike iz galerije po podacima kamere (EXIF), imenu fajla i folderu.
// Nije neprobojno (fajl može da se preimenuje), ali uz otisak slike na serveru i prijave
// korisnika odseca najčešće varanje: skinutu sliku, screenshot i staru sliku.
object ProveraSlike {
    const val NAJVISE_DANA = 3
    private const val DAN_MS = 24L * 60 * 60 * 1000

    // WhatsApp: IMG-20261028-WA0003.jpg, Viber: viber_image_..., Messenger: received_123....jpeg
    private val whatsapp = Regex("""^img-(\d{8})-wa\d+""")
    private val poslataIme = listOf(whatsapp, Regex("""^viber"""), Regex("""^received_\d+"""))
    private val poslataFolder = listOf("whatsapp", "viber", "messenger")

    fun oceni(context: Context, uri: Uri, sada: Long = System.currentTimeMillis()): IzvorSlike {
        val fajl = podaciFajla(context, uri)
        val ime = fajl.ime.lowercase(Locale.ROOT)
        val folder = fajl.folder.lowercase(Locale.ROOT)

        if (imaAiOznaku(context, uri)) return IzvorSlike.AI
        if ("screenshot" in ime || "screenshot" in folder || "snimak ekrana" in ime) return IzvorSlike.SCREENSHOT

        val exif = podaciKamere(context, uri)
        val skora = { datum: Long? -> datum != null && sada - datum <= NAJVISE_DANA * DAN_MS && datum <= sada + DAN_MS }

        if (exif.imaKameru) {
            return if (skora(exif.datum ?: fajl.datum)) IzvorSlike.KAMERA else IzvorSlike.STARA
        }

        val poslata = poslataIme.any { it.containsMatchIn(ime) } || poslataFolder.any { it in folder }
        if (poslata) {
            val datumIzImena = whatsapp.find(ime)?.groupValues?.get(1)?.let { parsiraj(it, "yyyyMMdd") }
            // WhatsApp u imenu ima samo dan, pa se računa kraj tog dana
            val datum = datumIzImena?.plus(DAN_MS - 1) ?: fajl.datum
            return if (skora(datum)) IzvorSlike.POSLATA else IzvorSlike.STARA
        }
        return IzvorSlike.NEPOZNATA
    }

    // AI alati upisuju u sliku oznaku porekla (IPTC DigitalSourceType u XMP-u ili u C2PA potpisu):
    // "trainedAlgorithmicMedia" (ChatGPT, Gemini), "compositeWithTrainedAlgorithmicMedia" (AI izmena,
    // npr. Magic Editor). Sam C2PA potpis nije dovoljan razlog, jer neki telefoni potpisuju i obične slike.
    // Oznaka nestaje kad se slika pošalje preko Vibera ili WhatsApp-a, a Geminijev nevidljivi SynthID
    // ne može da se proveri na telefonu.
    private val aiOznake = listOf("algorithmicmedia", "made with google ai").map { it.toByteArray(Charsets.US_ASCII) }
    private const val NAJVISE_BAJTOVA = 30 * 1024 * 1024

    private fun imaAiOznaku(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { ulaz ->
            val izlaz = java.io.ByteArrayOutputStream()
            val bafer = ByteArray(64 * 1024)
            while (izlaz.size() < NAJVISE_BAJTOVA) {
                val n = ulaz.read(bafer)
                if (n < 0) break
                izlaz.write(bafer, 0, n)
            }
            val sadrzaj = izlaz.toByteArray()
            aiOznake.any { sadrzi(sadrzaj, it) }
        } ?: false
    } catch (_: Exception) {
        false
    }

    // Traženje teksta u bajtovima bez obzira na velika i mala slova
    private fun sadrzi(sadrzaj: ByteArray, trazeno: ByteArray): Boolean {
        fun malo(b: Byte): Int = b.toInt().let { if (it in 65..90) it + 32 else it }
        val prvi = malo(trazeno[0])
        for (i in 0..sadrzaj.size - trazeno.size) {
            if (malo(sadrzaj[i]) != prvi) continue
            var j = 1
            while (j < trazeno.size && malo(sadrzaj[i + j]) == malo(trazeno[j])) j++
            if (j == trazeno.size) return true
        }
        return false
    }

    private data class Fajl(val ime: String, val folder: String, val datum: Long?)
    private data class Kamera(val imaKameru: Boolean, val datum: Long?)

    private fun podaciFajla(context: Context, uri: Uri): Fajl {
        var ime = ""
        var folder = ""
        var datum: Long? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    fun tekst(kolona: String) = c.getColumnIndex(kolona).takeIf { it >= 0 }?.let { c.getString(it) }
                    fun broj(kolona: String) = c.getColumnIndex(kolona).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                    ime = tekst(OpenableColumns.DISPLAY_NAME) ?: ""
                    folder = listOfNotNull(
                        tekst(MediaStore.MediaColumns.RELATIVE_PATH),
                        tekst(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    ).joinToString("/")
                    datum = broj(MediaStore.Images.ImageColumns.DATE_TAKEN)?.takeIf { it > 0 }
                        ?: broj(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf { it > 0 }?.times(1000)
                }
            }
        } catch (_: Exception) {
        }
        return Fajl(ime, folder, datum)
    }

    private fun podaciKamere(context: Context, uri: Uri): Kamera = try {
        context.contentResolver.openInputStream(uri)?.use { ulaz ->
            val exif = ExifInterface(ulaz)
            val kamera = !exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank()
            val datum = (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME))?.let { parsiraj(it, "yyyy:MM:dd HH:mm:ss") }
            Kamera(kamera, datum)
        } ?: Kamera(false, null)
    } catch (_: Exception) {
        Kamera(false, null)
    }

    private fun parsiraj(tekst: String, format: String): Long? = try {
        SimpleDateFormat(format, Locale.US).apply { isLenient = false }.parse(tekst.trim())?.time
    } catch (_: Exception) {
        null
    }
}
