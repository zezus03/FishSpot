package com.example.fishspot.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

// Model za glavnu tabelu
data class VodostajPodaci(
    val reka: String,
    val stanica: String,
    val vodostaj: String,
    val promena: String,
    val proticaj: String,
    val temperatura: String,
    val link: String
)

// NOVI model za detaljnu istoriju na pola sata
data class DetaljniVodostaj(
    val vreme: String,
    val vodostaj: String
)

class WaterLevelViewModel : ViewModel() {

    private val _vodostaji = MutableStateFlow<List<VodostajPodaci>>(emptyList())
    val vodostaji = _vodostaji.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _detaljniPodaci = MutableStateFlow<List<DetaljniVodostaj>>(emptyList())
    val detaljniPodaci = _detaljniPodaci.asStateFlow()

    private val _isDetaljniLoading = MutableStateFlow(false)
    val isDetaljniLoading = _isDetaljniLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // POMOĆNA FUNKCIJA: Kreira fabriku koja dozvoljava Androidu da prihvati RHMZ sertifikat
    private fun sslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }

    fun fetchVodostaj() {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect("https://www.hidmet.gov.rs/latin/osmotreni/stanje_voda.php")
                    .sslSocketFactory(sslSocketFactory()) // ISPRAVLJENO: Koristi univerzalni SSL bypass
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get()

                val rows = doc.select("table tr")
                val privremenaLista = mutableListOf<VodostajPodaci>()
                var trenutnaReka = ""

                for (row in rows) {
                    val polja = row.select("td")
                    if (polja.isEmpty()) continue

                    val link = row.select("a[href]").firstOrNull()?.attr("href") ?: ""
                    val tekstovi = polja.map { it.text().trim() }.filter { it.isNotBlank() }

                    if (tekstovi.size == 6) {
                        trenutnaReka = tekstovi[0]
                        if (trenutnaReka.lowercase() == "reka") continue

                        privremenaLista.add(VodostajPodaci(
                            trenutnaReka, tekstovi[1], tekstovi[2], tekstovi[3], tekstovi[4], tekstovi[5], link
                        ))
                    } else if (tekstovi.size == 5 && trenutnaReka.isNotBlank()) {
                        privremenaLista.add(VodostajPodaci(
                            trenutnaReka, tekstovi[0], tekstovi[1], tekstovi[2], tekstovi[3], tekstovi[4], link
                        ))
                    }
                }
                _vodostaji.value = privremenaLista
            } catch (e: Exception) {
                Log.e("WaterLevel", "Greška pri skidanju vodostaja", e)
                _errorMessage.value = "Nije moguće preuzeti podatke. Proverite internet vezu."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchDetaljeStanice(link: String) {
        if (link.isBlank()) return
        _isDetaljniLoading.value = true
        _detaljniPodaci.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stanicaId = link.substringAfter("id=").substringBefore("&").filter { it.isDigit() }
                if (stanicaId.isBlank()) return@launch

                val nrtUrl = "https://www.hidmet.gov.rs/latin/osmotreni/nrt_tabela_grafik.php?hm_id=$stanicaId&period=7"

                val doc = Jsoup.connect(nrtUrl)
                    .sslSocketFactory(sslSocketFactory()) // ISPRAVLJENO: Koristi univerzalni SSL bypass i za detalje
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get()

                val rows = doc.select("tr")
                val privremenaLista = mutableListOf<DetaljniVodostaj>()

                val regex = """\d{2}\.\d{2}\.\d{4}\s+(\d{2}:\d{2})\s+(-?\d+)""".toRegex()

                for (row in rows) {
                    val rowText = row.text().replace('\u00A0', ' ').replace("\\s+".toRegex(), " ").trim()

                    val match = regex.find(rowText)
                    if (match != null) {
                        val vreme = match.groupValues[1]
                        val vodostaj = match.groupValues[2]

                        privremenaLista.add(DetaljniVodostaj(vreme, vodostaj))
                    }
                }

                val poslednjaMerenja = privremenaLista.take(9)

                val finalnaLista = poslednjaMerenja.mapIndexed { index, trenutni ->
                    if (index < poslednjaMerenja.size - 1) {
                        val stariji = poslednjaMerenja[index + 1]
                        val razlika = trenutni.vodostaj.toInt() - stariji.vodostaj.toInt()
                        val trend = when {
                            razlika > 0 -> " ⬆"
                            razlika < 0 -> " ⬇"
                            else -> " ⏺"
                        }
                        trenutni.copy(vodostaj = "${trenutni.vodostaj}$trend")
                    } else {
                        trenutni
                    }
                }

                _detaljniPodaci.value = finalnaLista.take(8)

            } catch (e: Exception) {
                Log.e("WaterLevel", "Greška pri čitanju detalja: ${e.message}")
            } finally {
                _isDetaljniLoading.value = false
            }
        }
    }

    fun ocistiDetalje() {
        _detaljniPodaci.value = emptyList()
    }
}