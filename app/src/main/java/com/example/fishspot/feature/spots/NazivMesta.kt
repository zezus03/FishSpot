package com.example.fishspot.feature.spots

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

// Naziv mesta iz tačke na mapi (npr. "Velesnica, Kladovo"), preko Android Geocoder-a, latinicom.
// Vraća null bez interneta ili kad telefon nema Geocoder; korisnik tada upisuje naziv sam.
object NazivMesta {
    private val latinica = Locale.forLanguageTag("sr-Latn-RS")

    suspend fun predlog(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, latinica)
        val adresa = withTimeoutOrNull(8000) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<Address?> { nastavak ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(adrese: MutableList<Address>) { nastavak.resume(adrese.firstOrNull()) }
                            override fun onError(poruka: String?) { nastavak.resume(null) }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                    }
                }
            } catch (_: Exception) {
                null
            }
        } ?: return null
        return napravi(adresa)
    }

    // Uži deo (deo grada ili selo) pa grad ili opština, bez ponavljanja i bez kodova tipa "8FVC+2X"
    private fun napravi(a: Address): String? {
        val delovi = listOf(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea.takeIf { a.locality == null && a.subAdminArea == null })
            .mapNotNull { it?.trim()?.takeIf { d -> d.isNotEmpty() && !d.contains('+') } }
            .distinct()
        return uLatinicu(delovi.joinToString(", ")).takeIf { it.isNotEmpty() }?.take(100)
    }

    // Geocoder ponekad vrati ćirilicu i kad se traži latinica ("Сурачево" -> "Suračevo")
    private val cirilica = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ђ' to "Đ", 'Е' to "E", 'Ж' to "Ž", 'З' to "Z",
        'И' to "I", 'Ј' to "J", 'К' to "K", 'Л' to "L", 'Љ' to "Lj", 'М' to "M", 'Н' to "N", 'Њ' to "Nj", 'О' to "O",
        'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'Ћ' to "Ć", 'У' to "U", 'Ф' to "F", 'Х' to "H", 'Ц' to "C",
        'Ч' to "Č", 'Џ' to "Dž", 'Ш' to "Š"
    ).let { velika -> velika + velika.map { (k, v) -> k.lowercaseChar() to v.lowercase() } }

    fun uLatinicu(tekst: String): String = buildString { tekst.forEach { append(cirilica[it] ?: it.toString()) } }
}
