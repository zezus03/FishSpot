
package com.example.fishspot.feature.spots

import androidx.annotation.DrawableRes
import com.example.fishspot.R

// Vrsta u čuvarki (isto kao VrstaRibe, sa praznim podrazumevanim vrednostima zbog čitanja iz baze)
data class VrstaKratko(val id: String = "", val naziv: String = "", val latinskiNaziv: String = "")

const val MAKS_TEZINA_CUVARKE = 30.0

// Podaci o ribi (vrsta, dužina, težina, čuvarka) mogu da se menjaju samo ovoliko posle objave,
// da niko kasnije ne bi "povećao" stari ulov (npr. pred kraj takmičenja). Isto proveravaju pravila baze.
const val MINUTA_ZA_IZMENU_RIBE = 30

val FishSpot.ribaMozeDaSeMenja: Boolean
    get() = objavljeno?.let { System.currentTimeMillis() - it.time < MINUTA_ZA_IZMENU_RIBE * 60_000L } ?: false
const val MAKS_VRSTA_CUVARKE = 10

// Naziv za prikaz: kod čuvarke sve vrste ("Bodorka, Ukljeva"), inače vrsta ribe
val FishSpot.nazivVrsta: String
    get() = if (cuvarka && cuvarkaVrste.isNotEmpty()) cuvarkaVrste.joinToString(", ") { it.naziv } else vrstaRibe

data class KategorijaMamca(val naziv: String, @DrawableRes val slika: Int, val mamci: List<String>)

// Vrsta varalice ili tip mušice (slika može da fali, tada se prikazuje ikonica)
data class PodvrstaMamca(val naziv: String, @DrawableRes val slika: Int?)

data class DetaljiMamca(val velicine: List<String>, val ukusi: List<Pair<String, List<String>>>)

data class FishSpot(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val naziv: String = "",
    val vrstaRibe: String = "",
    val vrstaId: String = "", // ID iz kolekcije VrsteRiba (prazno za stare ulove)
    val latinskiNaziv: String = "",
    val ocena: Float = 0.0f,
    val opis: String = "",
    val slikaUrl: String? = null, // glavna slika (na njoj je riba)
    val dodatneSlike: List<String> = emptyList(), // do 4 dodatne (ekipa, štap, varalica)
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val duzinaRibe: Double = 0.0,
    val tezinaRibe: Double = 0.0,
    val mamac: String = "",
    val tehnika: String = "",
    val temperatura: String = "",
    val pritisak: String = "",
    val vetar: String = "",
    val izvorSlike: String = "", // vidi ProveraSlike.kt; prazno kod starih ulova
    // Čuvarka: više riba (i vrsta) zajedno, težina je ukupna (do 30 kg), bez dužine;
    // vrstaId/vrstaRibe su tada prva izabrana vrsta
    val cuvarka: Boolean = false,
    val cuvarkaVrste: List<VrstaKratko> = emptyList(),
    // Vreme objave po satu servera (upisuje ga Firestore); podaci o ribi mogu da se menjaju 30 min posle toga
    @com.google.firebase.firestore.ServerTimestamp val objavljeno: java.util.Date? = null,
    val likedBy: List<String> = emptyList(),
    // Autor je sakrio tačnu lokaciju: lat/lng su približni (SkrivenaLokacija.kt)
    val skrivenaLokacija: Boolean = false
)
// Ponuđeni mamci i tehnike (dodavanje i izmena ulova)
object OpcijeUlova {
    const val OSTALO = "Ostalo"
    private val osnovniMamci = listOf("Crv", "Glista", "Šećerac", "Boila", "Pop-up", "Keder", "Varalica", "Mušica")
    val mamci = osnovniMamci + OSTALO
    val tehnike = listOf("Plovak", "Dubinac", "Varalica", "Feeder", "Mušičarenje")

    // Ostali mamci po kategorijama (u bazu se upisuje naziv konkretnog mamca)
    val ostaliMamci = listOf(
        KategorijaMamca("Insekti", R.drawable.mamac_insekti, listOf(
            "Skakavac", "Cvrčak", "Muva", "Vodeni cvet", "Mrav", "Osa", "Stršljen", "Larva"
        )),
        KategorijaMamca("Riblji i mesni mamci", R.drawable.mamac_riba_meso, listOf(
            "Žaba", "Pijavica",
            "Sveža sardina", "Sardina iz konzerve", "Tuna iz konzerve", "Komadići sveže ribe",
            "Fileti sitne ribe", "Riblja koža", "Riblje meso sa kostima", "Komadići sitnih ribica",
            "Salama", "Virsle", "Pileća džigerica", "Goveđa džigerica", "Srce i drugi komadi mesa",
            "Mesni narezak", "Kuvana pileća kožica", "Komadići slanine"
        )),
        KategorijaMamca("Žitarice i semenke", R.drawable.mamac_zitarice, listOf(
            "Kukuruz šećerac iz konzerve", "Kuvani kukuruz", "Potopljen i kuvan suvi kukuruz",
            "Kuvana pšenica", "Kuvani ječam", "Kuvani ovas", "Ovsene pahuljice", "Kuvani pirinač",
            "Kuvano proso", "Kuvana konoplja", "Seme suncokreta", "Seme bundeve", "Kuvani grašak",
            "Kuvana leblebija", "Kuvani pasulj", "Kuvano sočivo", "Kuvani bob", "Kuvana heljda",
            "Mleveni lan", "Mleveno seme konoplje"
        )),
        KategorijaMamca("Hleb, testo i pekarski proizvodi", R.drawable.mamac_hleb, listOf(
            "Sredina belog hleba", "Kora hleba", "Prepečen hleb", "Francuski hleb",
            "Bajat hleb omekšan vodom", "Kukuruzno testo", "Pšenično testo", "Kuvani makaroni"
        )),
        KategorijaMamca("Voće", R.drawable.mamac_voce, listOf(
            "Dud", "Trešnja", "Višnja", "Šljiva", "Grožđe"
        )),
        KategorijaMamca("Mlečni proizvodi i sirevi", R.drawable.mamac_sirevi, listOf(
            "Kačkavalj", "Gauda", "Trapist", "Čedar", "Mladi tvrđi sir", "Dimljeni sir",
            "Parmezan", "Korica tvrdog sira"
        )),
        KategorijaMamca("Alge (trava) i drugi biljni mamci", R.drawable.mamac_alge, listOf(
            "Grašak", "Mlada trava i vodeno bilje", "Listovi biljaka", "Alge"
        )),
    )

    // Boila i pop-up se biraju sa veličinom i ukusom, npr. "Boila 16-18 mm, Jagoda"
    private val ukusiZajednicki = listOf(
        "Voćni" to listOf(
            "Jagoda", "Ananas", "Banana", "Šljiva", "Breskva", "Dinja", "Malina", "Kupina", "Dud", "Višnja",
            "Trešnja", "Crna ribizla", "Mango", "Kokos", "Limun", "Narandža", "Tutti frutti", "Mešano voće"
        ),
        "Slatki i orašasti" to listOf(
            "Vanila", "Karamela", "Čokolada", "Med", "Scopex", "Banoffee", "Krem/Mlečni", "Badem", "Lešnik",
            "Butirna kiselina"
        ),
        "Riblji i morski" to listOf(
            "Lignja", "Hobotnica", "Krill", "Tuna", "Losos", "Halibut", "Rak", "Škampi", "Dagnja", "Riblje brašno"
        ),
        "Začinski" to listOf("Beli luk", "Biber", "Čili", "Cimet", "Anis", "Začinski mix"),
    )
    private val ukusiOstali = listOf("Kukuruz", "Konoplja", "Tigrov orah", "Kikiriki", "Jetra", "Krv", "Crvi", "Glista")

    private val detaljiMamaca = mapOf(
        "Pop-up" to DetaljiMamca(
            velicine = listOf("6-8 mm", "8-10 mm", "10-12 mm", "12-14 mm", "14-16 mm", "16-18 mm", "18-20 mm",
                "20-24 mm", "Dumbell"),
            ukusi = ukusiZajednicki + ("Ostali" to ukusiOstali + "Neutralni")
        ),
        "Boila" to DetaljiMamca(
            velicine = listOf("8-10 mm", "10-12 mm", "12-14 mm", "14-16 mm", "16-18 mm", "18-20 mm", "20-24 mm",
                "24-30 mm", "Dumbell", "Mešane veličine"),
            ukusi = ukusiZajednicki + ("Ostali" to ukusiOstali +
                listOf("Ptičja hrana", "Proteinska", "Mlečna", "Žitarice", "Neutralni"))
        ),
    )

    fun detalji(osnova: String): DetaljiMamca? = detaljiMamaca[osnova]

    // Vrste varalica i tipovi mušica; u bazu ide npr. "Varalica Vobler" ili "Mušica Nimfa".
    // Podvrsta "Ostalo" se čuva kao samo naziv mamca ("Varalica").
    const val PODVRSTA_OSTALO = "Ostalo"
    private val podvrste = mapOf(
        "Varalica" to listOf(
            PodvrstaMamca("Kašika (leptir)", R.drawable.varalica_kasika_leptir),
            PodvrstaMamca("Kašika (talasajuća)", R.drawable.varalica_kasika_talasajuca),
            PodvrstaMamca("Vobler", R.drawable.varalica_vobler),
            PodvrstaMamca("Silikonska varalica", R.drawable.varalica_silikonska),
            PodvrstaMamca("Spinnerbait", R.drawable.varalica_spinnerbait),
            PodvrstaMamca("Buzzbait", R.drawable.varalica_buzzbait),
            PodvrstaMamca("Površinska varalica", R.drawable.varalica_povrsinska),
            PodvrstaMamca("Žaba", R.drawable.varalica_zaba),
            PodvrstaMamca("Pilker", R.drawable.varalica_pilker),
            PodvrstaMamca("Cikada", R.drawable.varalica_cikada),
            PodvrstaMamca("Metalni jig", R.drawable.varalica_metalni_jig),
            PodvrstaMamca("Swimbait", R.drawable.varalica_swimbait),
            PodvrstaMamca("Jerkbait", R.drawable.varalica_jerkbait),
            PodvrstaMamca("Glavinjare", R.drawable.varalica_glavinjare),
            PodvrstaMamca(PODVRSTA_OSTALO, R.drawable.varalica_ostalo),
        ),
        "Mušica" to listOf(
            PodvrstaMamca("Suva mušica", R.drawable.musica_suva),
            PodvrstaMamca("Mokra mušica", R.drawable.musica_mokra),
            PodvrstaMamca("Nimfa", R.drawable.musica_nimfa),
            PodvrstaMamca("Emerger", R.drawable.musica_emerger),
            PodvrstaMamca("Streamer", R.drawable.musica_streamer),
            PodvrstaMamca("Popper", R.drawable.musica_popper),
        ),
    )

    fun podvrste(osnova: String): List<PodvrstaMamca>? = podvrste[osnova]

    fun naslovPodvrste(osnova: String) = if (osnova == "Mušica") "Tip mušice" else "Vrsta varalice"

    fun mamacZaPodvrstu(osnova: String, p: PodvrstaMamca): String =
        if (p.naziv == PODVRSTA_OSTALO) osnova else "$osnova ${p.naziv}"

    // "Varalica Vobler" -> Vobler; samo "Varalica" -> Ostalo (ako ga ta grupa ima)
    fun podvrsta(mamac: String): PodvrstaMamca? {
        val osnova = osnova(mamac)
        val lista = podvrste[osnova] ?: return null
        return if (mamac == osnova) lista.firstOrNull { it.naziv == PODVRSTA_OSTALO }
        else lista.firstOrNull { it.naziv == mamac.removePrefix("$osnova ") }
    }

    // "Boila 16-18 mm, Jagoda" -> "Boila", "Varalica Vobler" -> "Varalica"; ostali mamci ostaju isti
    fun osnova(mamac: String): String =
        (detaljiMamaca.keys + podvrste.keys).firstOrNull { mamac == it || mamac.startsWith("$it ") } ?: mamac

    fun sastavi(osnova: String, velicina: String, ukus: String) = "$osnova $velicina, $ukus"

    // "Boila 16-18 mm, Jagoda" -> ("16-18 mm", "Jagoda")
    fun rastavi(mamac: String): Pair<String, String>? {
        val osnova = osnova(mamac)
        if (detalji(osnova) == null || mamac == osnova) return null
        val ostatak = mamac.removePrefix("$osnova ")
        val zarez = ostatak.indexOf(", ")
        if (zarez < 0) return null
        return ostatak.substring(0, zarez) to ostatak.substring(zarez + 2)
    }

    // Mamac koji nije među osnovnim čipovima (npr. "Kuvani kukuruz") pripada grupi Ostalo
    fun jeOstalo(mamac: String): Boolean = mamac.isNotBlank() && osnova(mamac) !in osnovniMamci

    fun kategorija(mamac: String): KategorijaMamca? = ostaliMamci.firstOrNull { mamac in it.mamci }

    // Koje tehnike imaju smisla uz izabrani mamac (iste parove proveravaju i pravila baze)
    fun dozvoljeneTehnike(mamac: String): List<String> = when (osnova(mamac)) {
        "Varalica" -> listOf("Varalica")
        "Mušica" -> listOf("Mušičarenje")
        "Boila", "Pop-up" -> listOf("Dubinac", "Feeder")
        "Keder" -> listOf("Plovak", "Dubinac")
        else -> listOf("Plovak", "Dubinac", "Feeder")
    }
}
