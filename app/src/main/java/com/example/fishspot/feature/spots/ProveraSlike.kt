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
    POSLATA("poslata", true, "Poslata slika: donosi poene"),
    AI("ai", false, "Slika je napravljena ili izmenjena veštačkom inteligencijom (AI) i ne donosi poene"),
    SCREENSHOT("screenshot", false, "Snimak ekrana ne donosi poene"),
    STARA("stara", false, "Slika je starija od ${ProveraSlike.NAJVISE_DANA} dana i ne donosi poene"),
    NEPOZNATA("nepoznata", false, "Slika nema podatke sa kamere (npr. skinuta sa interneta) i ne donosi poene"),
}

// Procena poena pre čuvanja, po istim pravilima kao server (functions/ulovi.js: poeniZaNoviUlov, stanjePoena)
object PoeniUlova {
    data class Procena(val poeni: Int, val opis: String, val cekaAdmina: Boolean = false)

    private fun osnovni(kg: Double): Int = when {
        kg > 5.0 -> 50
        kg > 2.0 -> 20
        kg > 1.0 -> 10
        kg > 0.5 -> 5
        kg > 0.0 -> 2
        else -> 0
    }

    fun proceni(kg: Double?, izvor: IzvorSlike?, cuvarka: Boolean): Procena? {
        if (kg == null || kg <= 0 || izvor == null) return null
        if (!izvor.donosiPoene) return Procena(0, izvor.opis)
        val osnovno = osnovni(kg)
        var poeni = if (izvor == IzvorSlike.APLIKACIJA) Math.round(osnovno * 1.2).toInt() else osnovno
        val bonus = if (izvor == IzvorSlike.APLIKACIJA) " + 20% za sliku iz aplikacije" else ""
        if (cuvarka) {
            poeni = minOf(poeni, 20)
            return Procena(poeni, "Čuvarka donosi najviše 20 poena")
        }
        if (poeni >= 50) return Procena(poeni, "Riba preko 5 kg: poeni stižu kad admin odobri ulov", cekaAdmina = true)
        return Procena(poeni, "$osnovno za težinu$bonus")
    }
}

// Otisak slike (dHash, 64 bita), isti postupak kao na serveru (functions/slike.js):
// slika se svede na 9x8 sivih polja i za svako polje se gleda da li je svetlije od desnog suseda
object OtisakSlike {
    fun izracunaj(context: Context, uri: Uri): String? = try {
        val slika = com.example.fishspot.feature.fish.ucitajSlikuUspravno(context, uri, 800)
        val w = slika.width
        val h = slika.height
        val s = 9
        val v = 8
        val zbir = DoubleArray(s * v)
        val broj = DoubleArray(s * v)
        val red = IntArray(w)
        for (y in 0 until h) {
            slika.getPixels(red, 0, w, 0, y, w, 1)
            val r = minOf(v - 1, (y * v) / h)
            for (x in 0 until w) {
                val k = minOf(s - 1, (x * s) / w)
                val p = red[x]
                zbir[r * s + k] += 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
                broj[r * s + k] += 1.0
            }
        }
        slika.recycle()
        var bitovi = 0UL
        for (r in 0 until v) for (k in 0 until s - 1) {
            val levo = zbir[r * s + k] / broj[r * s + k]
            val desno = zbir[r * s + k + 1] / broj[r * s + k + 1]
            bitovi = (bitovi shl 1) or (if (levo > desno) 1UL else 0UL)
        }
        bitovi.toString(16).padStart(16, '0')
    } catch (_: Exception) {
        null
    }
}

// Određuje izvor slike iz galerije po podacima kamere (EXIF), imenu fajla i folderu.
// Nije neprobojno (fajl može da se preimenuje), ali uz otisak slike na serveru i prijave
// korisnika odseca najčešće varanje: skinutu sliku, screenshot i staru sliku.
object ProveraSlike {
    const val NAJVISE_DANA = 3
    private const val DAN_MS = 24L * 60 * 60 * 1000

    // Slike iz galerije starije od NAJVISE_DANA ne dobijaju poene (za testiranje sa starim slikama: false)
    const val PROVERAVAJ_STAROST = true

    // Imena kako ih aplikacije stvarno čuvaju na telefonu:
    // WhatsApp: IMG-20261022-WA0003.jpg, Viber: IMG-2aa845223bded79db36f42f452698749-V.jpg (ili viber_image_...),
    // Messenger: Messenger_creation_EE9E5949-....jpeg (ili received_123....jpeg).
    // Facebook (FB_IMG_...) i ostalo skinuto sa interneta se ne prepoznaje kao poslata slika.
    private val whatsapp = Regex("""^img-(\d{8})-wa\d+""")
    private val poslataIme = listOf(
        whatsapp,
        Regex("""^img-[0-9a-f]{20,}-v\."""), Regex("""^viber"""),
        Regex("""^messenger_creation_"""), Regex("""^received_\d+""")
    )
    private val poslataFolder = listOf("whatsapp", "viber", "messenger")

    fun oceni(context: Context, uri: Uri, sada: Long = System.currentTimeMillis()): IzvorSlike {
        val fajl = podaciFajla(context, uri)
        val ime = fajl.ime.lowercase(Locale.ROOT)
        val folder = fajl.folder.lowercase(Locale.ROOT)

        android.util.Log.d("ProveraSlike", "uri=$uri ime=$ime folder=$folder datum=${fajl.datum}")
        if (imaAiOznaku(context, uri)) return IzvorSlike.AI
        if ("screenshot" in ime || "screenshot" in folder || "snimak ekrana" in ime) return IzvorSlike.SCREENSHOT

        val exif = podaciKamere(context, uri)
        val skora = { datum: Long? ->
            !PROVERAVAJ_STAROST || (datum != null && sada - datum <= NAJVISE_DANA * DAN_MS && datum <= sada + DAN_MS)
        }

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
        // Svaka kolona se čita posebno: birač slika (photo picker, galerija) ne podržava sve kolone,
        // a upit sa nepodržanom kolonom pukne ceo. Ime fajla (DISPLAY_NAME) podržava svaki.
        fun kolona(ime: String): Any? = try {
            context.contentResolver.query(uri, arrayOf(ime), null, null, null)?.use { c ->
                if (!c.moveToFirst() || c.isNull(0)) null
                else if (c.getType(0) == android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(0) else c.getString(0)
            }
        } catch (_: Exception) {
            null
        }
        val ime = kolona(OpenableColumns.DISPLAY_NAME) as? String ?: ""
        val folder = listOfNotNull(
            kolona(MediaStore.MediaColumns.RELATIVE_PATH) as? String,
            kolona(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME) as? String
        ).joinToString("/")
        val datum = (kolona(MediaStore.Images.ImageColumns.DATE_TAKEN) as? Long)?.takeIf { it > 0 }
            ?: (kolona(MediaStore.MediaColumns.DATE_MODIFIED) as? Long)?.takeIf { it > 0 }?.times(1000)
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
