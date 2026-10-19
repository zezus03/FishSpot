package com.example.fishspot.viewmodel

import java.util.Calendar

object LovostajChecker {

    // Struktura koja čuva period zabrane i minimalnu meru u cm (0 znači da nema propisane mere)
    data class PraviloRibe(
        val pocetak: Int?,
        val kraj: Int?,
        val minimalnaDužina: Double = 0.0
    )

    // Mapa sa svim zakonskim propisima (Zabrana + Minimalna mera)
    // Format datuma: Mesec * 100 + Dan (Npr. 15. april = 415)
    private val lovostaji = mapOf(
        "Bucov" to PraviloRibe(415, 615, 30.0),
        "Deverika" to PraviloRibe(415, 531, 20.0),
        "Klen" to PraviloRibe(415, 531, 20.0),
        "Lipljen" to PraviloRibe(301, 531, 30.0),
        "Mladica" to PraviloRibe(301, 831, 100.0),
        "Mrena" to PraviloRibe(415, 531, 25.0),
        "Plotica" to PraviloRibe(415, 531, 20.0),
        "Protfis" to PraviloRibe(415, 531, 20.0), // Jaz
        "Jaz" to PraviloRibe(415, 531, 20.0),
        "Saran" to PraviloRibe(401, 531, 30.0),
        "Skobalj" to PraviloRibe(415, 531, 20.0),
        "Som" to PraviloRibe(501, 615, 60.0),
        "Stuka" to PraviloRibe(201, 331, 40.0),
        "Smud" to PraviloRibe(301, 430, 40.0),
        "Smud kamenjar" to PraviloRibe(301, 430, 25.0),

        // Pastrmke i zlatovčice (Zimski lovostaj 1.10. - 1.3. ili 28.2.)
        "Potocna pastrmka" to PraviloRibe(1001, 228, 25.0),
        "Drimska pastrmka" to PraviloRibe(1001, 301, 25.0),
        "Duzicasta pastrmka" to PraviloRibe(1001, 301, 25.0),
        "Makedonska pastrmka" to PraviloRibe(1001, 301, 25.0),
        "Ohridska pastrmka" to PraviloRibe(1001, 228, 40.0),
        "Jezerska zlatovcica" to PraviloRibe(1001, 228, 25.0),
        "Potocna zlatovcica" to PraviloRibe(1001, 228, 20.0),

        // Vrste bez lovostaja, ali sa minimalnom merom
        "Grgec" to PraviloRibe(null, null, 10.0),
        "Bandar" to PraviloRibe(null, null, 10.0),
        "Krkusa" to PraviloRibe(null, null, 10.0),
        "Manic" to PraviloRibe(null, null, 20.0),
        "Nosara" to PraviloRibe(null, null, 15.0),
        "Sljivar" to PraviloRibe(null, null, 15.0),
        "Potocna mrena" to PraviloRibe(null, null, 15.0),
        "Sapaca" to PraviloRibe(null, null, 15.0),
        "Recna skoljka" to PraviloRibe(null, null, 8.0),

        // Vrste bez zabrane i bez mere (bela riba, kesega, itd.) - dodate radi prepoznavanja
        "Bodorka" to PraviloRibe(null, null, 0.0),
        "Crnooka" to PraviloRibe(null, null, 0.0),
        "Crvenperka" to PraviloRibe(null, null, 0.0),
        "Gaovica" to PraviloRibe(null, null, 0.0),
        "Kartofla" to PraviloRibe(null, null, 0.0),
        "Jegulja" to PraviloRibe(null, null, 0.0),
        "Kesega" to PraviloRibe(null, null, 0.0),
        "Najlonka" to PraviloRibe(null, null, 0.0),
        "Krupatica" to PraviloRibe(null, null, 0.0),
        "Sabljarka" to PraviloRibe(null, null, 0.0),
        "Ukljeva" to PraviloRibe(null, null, 0.0),
        "Kaugler" to PraviloRibe(null, null, 0.0),
        "Beovica" to PraviloRibe(null, null, 0.0),
        "Vijun" to PraviloRibe(null, null, 0.0),
        "Stampajzer" to PraviloRibe(null, null, 0.0)
    )

    fun normalizujTekst(tekst: String): String {
        return tekst
            .lowercase()
            .replace("š", "s")
            .replace("đ", "d")
            .replace("č", "c")
            .replace("ć", "c")
            .replace("ž", "z")
            .trim()
    }

    private fun nadjiKljucnuRec(imeRibe: String): String? {
        val cistUnos = normalizujTekst(imeRibe)
        return lovostaji.keys.find { kljuc ->
            val cistKluc = normalizujTekst(kljuc)
            cistUnos.contains(cistKluc)
        }
    }

    // 1. Provera da li je riba u vremenskom lovostaju
    fun isRibaULovostaju(imeRibe: String): Boolean {
        val kljucnaRec = nadjiKljucnuRec(imeRibe) ?: return false
        val pravilo = lovostaji[kljucnaRec] ?: return false

        // Ako vrsta nema definisan period zabrane (pocetak i kraj su null)
        if (pravilo.pocetak == null || pravilo.kraj == null) return false

        val kalendar = Calendar.getInstance()
        val trenutniMesec = kalendar.get(Calendar.MONTH) + 1
        val trenutniDan = kalendar.get(Calendar.DAY_OF_MONTH)
        val danasnjiDatumInt = (trenutniMesec * 100) + trenutniDan

        return if (pravilo.pocetak <= pravilo.kraj) {
            danasnjiDatumInt in pravilo.pocetak..pravilo.kraj
        } else {
            // Zimski lovostaj (npr. od oktobra do marta)
            danasnjiDatumInt >= pravilo.pocetak || danasnjiDatumInt <= pravilo.kraj
        }
    }

    // 2. Provera da li je riba ispod zakonske minimalne mere
    fun isRibaIspodMere(imeRibe: String, unesenaDuzina: Double): Boolean {
        if (unesenaDuzina <= 0.0) return false // Ako dužina nije unesena, preskačemo proveru
        val kljucnaRec = nadjiKljucnuRec(imeRibe) ?: return false
        val pravilo = lovostaji[kljucnaRec] ?: return false

        return unesenaDuzina < pravilo.minimalnaDužina
    }

    // 3. Generisanje kompletnog izveštaja/upozorenja (za Lovostaj ILI za meru)
    fun getUpozorenje(imeRibe: String, trenutnaDuzina: Double = 0.0): String {
        val kljucnaRec = nadjiKljucnuRec(imeRibe) ?: imeRibe
        val pravilo = lovostaji[kljucnaRec]

        val sb = java.lang.StringBuilder()
        sb.append("⚠️ OBAVEŠTENJE ZA ULOV:\n")
        sb.append("Prepoznata vrsta: $kljucnaRec.\n\n")

        var prekrsaj = false

        // Provera vremenskog lovostaja
        if (isRibaULovostaju(imeRibe) && pravilo?.pocetak != null && pravilo.kraj != null) {
            val pocetakDan = pravilo.pocetak % 100
            val pocetakMesec = pravilo.pocetak / 100
            val krajDan = pravilo.kraj % 100
            val krajMesec = pravilo.kraj / 100
            sb.append("❌ Riba je u LOVOSTAJU! Zabrana traje od $pocetakDan.$pocetakMesec. do $krajDan.$krajMesec.\n")
            prekrsaj = true
        }

        // Provera minimalne mere
        if (pravilo != null && pravilo.minimalnaDužina > 0.0) {
            if (trenutnaDuzina > 0.0 && trenutnaDuzina < pravilo.minimalnaDužina) {
                sb.append("❌ Riba je ISPOD MINIMALNE MERE! Vaš ulov iznosi $trenutnaDuzina cm, a zakonski minimum za ovu vrstu je ${pravilo.minimalnaDužina} cm.\n")
                prekrsaj = true
            }
        }

        if (prekrsaj) {
            sb.append("\n📢 Molimo vas da ribu bezbedno i odmah vratite u vodu!")
            return sb.toString()
        }

        return ""
    }
}