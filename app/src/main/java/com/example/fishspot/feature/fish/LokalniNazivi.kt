package com.example.fishspot.feature.fish

// Lokalni nazivi riba u Srbiji: korisnik može da ukuca "karaš" ili "bandar", a čuva se pravi naziv vrste
// (Babuška, Grgeč), pa na profilu i u listama uvek stoji isti naziv. Neki nazivi važe za više vrsta
// (npr. "tolstolobik"), pa se tada nude sve te vrste.
object LokalniNazivi {
    private val poVrsti: Map<String, List<String>> = mapOf(
        "amur" to listOf("beli amur"),
        "babuska" to listOf("karaš", "melez", "pastak"),
        "beli-tolstolobik" to listOf("tolstolobik"),
        "bodorka" to listOf("žutooka"),
        "bucov" to listOf("bolen", "belun"),
        "crni-patuljasti-som" to listOf("cvergl", "somić", "američki somić"),
        "crnooka" to listOf("crnooka deverika"),
        "gavcica" to listOf("gaovica", "kartofla"),
        "grgec" to listOf("bandar", "kostreš", "bulješ", "okan"),
        "jaz" to listOf("protfiš"),
        "kalifornijska-pastrmka" to listOf("dužičasta pastrmka", "pastrmka"),
        "karas" to listOf("zlatni karaš"),
        "krupatica" to listOf("kesega"),
        "nosara" to listOf("šljivar"),
        "ostalo" to listOf("kesega", "najlonka", "špicer"),
        "patuljasti-som" to listOf("cvergl", "somić", "američki somić"),
        "potocna-pastrmka" to listOf("pastrmka"),
        "saran" to listOf("divljak", "golać", "ljuskaš"),
        "sivi-tolstolobik" to listOf("tolstolobik"),
        "skobalj" to listOf("podust"),
        "smud-kamenjar" to listOf("kamenjar"),
        "suncanica" to listOf("sunčica"),
        "ukljeva" to listOf("kaugler", "beovica"),
    )

    // Objašnjenje kad korisnik ukuca naziv koji se često meša
    private val napomene: Map<String, String> = mapOf(
        "karas" to "Mnogi babušku zovu samo „karaš“, a pravi karaš je druga, zaštićena vrsta.",
        "kesega" to "Kesega (najlonka, špicer) nije na spisku, pa izaberi Ostalo. Krupaticu ponekad pogrešno zovu kesega.",
        "tolstolobik" to "Postoje beli i sivi tolstolobik: sivi ima tamnije šare i veću glavu.",
        "divljak" to "Divljak, golać i ljuskaš su oblici šarana.",
        "golac" to "Divljak, golać i ljuskaš su oblici šarana.",
        "ljuskas" to "Divljak, golać i ljuskaš su oblici šarana.",
    )

    // Lokalni naziv vrste koji odgovara unetom tekstu ("kar" -> "karaš" za babušku), ili null
    fun pogodak(vrstaId: String, unos: String): String? {
        val trazeno = LovostajChecker.normalizujTekst(unos)
        if (trazeno.length < 3) return null
        return poVrsti[vrstaId]?.firstOrNull { naziv ->
            val n = LovostajChecker.normalizujTekst(naziv)
            n.startsWith(trazeno) || n.split(" ").any { it.startsWith(trazeno) }
        }
    }

    // Napomena za uneti tekst, ako ga ljudi često mešaju
    fun napomena(unos: String): String? {
        val trazeno = LovostajChecker.normalizujTekst(unos).trim()
        if (trazeno.length < 3) return null
        return napomene.entries.firstOrNull { it.key.startsWith(trazeno) || trazeno.startsWith(it.key) }?.value
    }
}
