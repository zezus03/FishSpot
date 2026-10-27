package com.example.fishspot.feature.fish

/**
 * Najveća dužina i težina koje imaju smisla za svaku vrstu (malo iznad poznatih rekorda),
 * da se ne bi objavljivali ulovi tipa "skobalj od 3000 cm". Iste granice proveravaju i pravila baze
 * (firestore.rules, funkcija ulovUGranicama), pa se ne mogu zaobići.
 */
object OgranicenjaRibe {
    data class Granica(val maxDuzinaCm: Double, val maxTezinaKg: Double)

    private val GRANICE = mapOf(
        "saran" to Granica(120.0, 45.0),
        "babuska" to Granica(50.0, 3.0),
        "karas" to Granica(55.0, 3.5),
        "amur" to Granica(150.0, 50.0),
        "beli-tolstolobik" to Granica(125.0, 55.0),
        "sivi-tolstolobik" to Granica(160.0, 60.0),
        "linjak" to Granica(70.0, 7.5),
        "deverika" to Granica(85.0, 9.0),
        "krupatica" to Granica(45.0, 1.5),
        "crnooka" to Granica(50.0, 2.0),
        "nosara" to Granica(55.0, 3.0),
        "bodorka" to Granica(55.0, 2.5),
        "plotica" to Granica(60.0, 3.5),
        "crvenperka" to Granica(50.0, 2.2),
        "klen" to Granica(80.0, 8.0),
        "jaz" to Granica(100.0, 8.0),
        "bucov" to Granica(120.0, 13.0),
        "skobalj" to Granica(55.0, 2.5),
        "mrena" to Granica(120.0, 13.0),
        "potocna-mrena" to Granica(35.0, 0.7),
        "ukljeva" to Granica(25.0, 0.25),
        "sabljarka" to Granica(65.0, 2.2),
        "krkusa" to Granica(22.0, 0.25),
        "gavcica" to Granica(12.0, 0.05),
        "grgec" to Granica(60.0, 5.0),
        "smud" to Granica(130.0, 20.0),
        "smud-kamenjar" to Granica(55.0, 2.5),
        "balavac" to Granica(25.0, 0.4),
        "stuka" to Granica(150.0, 30.0),
        "som" to Granica(280.0, 140.0),
        "patuljasti-som" to Granica(55.0, 3.0),
        "crni-patuljasti-som" to Granica(65.0, 4.0),
        "manic" to Granica(100.0, 15.0),
        "jegulja" to Granica(150.0, 9.0),
        "keciga" to Granica(125.0, 16.0),
        "potocna-pastrmka" to Granica(100.0, 20.0),
        "kalifornijska-pastrmka" to Granica(100.0, 25.0),
        "mladica" to Granica(160.0, 60.0),
        "lipljen" to Granica(60.0, 3.5),
        "potocna-zlatovcica" to Granica(70.0, 7.0),
        "suncanica" to Granica(30.0, 0.7),
        "glavoc" to Granica(30.0, 0.5),
        "ostalo" to Granica(300.0, 150.0)
    )
    private val PODRAZUMEVANO = Granica(300.0, 150.0)

    fun granica(vrstaId: String?): Granica = GRANICE[vrstaId] ?: PODRAZUMEVANO

    /**
     * Vraća opis greške ili null ako su dužina i težina u redu za izabranu vrstu.
     * Težina mora i da odgovara dužini (najviše 2,5 puta više ili 3 puta manje od procene po formuli).
     */
    fun proveri(vrstaId: String?, nazivVrste: String, duzinaCm: Double?, tezinaKg: Double?): String? {
        val g = granica(vrstaId)
        if (duzinaCm == null || duzinaCm <= 0.0) return "Unesi dužinu ribe (cm)"
        if (tezinaKg == null || tezinaKg <= 0.0) return "Unesi težinu ribe (kg)"
        if (duzinaCm > g.maxDuzinaCm) return "$nazivVrste ne može biti duži od ${g.maxDuzinaCm.toInt()} cm"
        if (tezinaKg > g.maxTezinaKg) return "$nazivVrste ne može biti teži od ${formatKg(g.maxTezinaKg)} kg"
        if (TezinaRibe.poznataVrsta(nazivVrste)) {
            val procena = TezinaRibe.proceniKg(nazivVrste, duzinaCm)
            if (procena != null && procena > 0.0 && (tezinaKg > procena * 2.5 || tezinaKg < procena / 3.0)) {
                return "Težina ne odgovara dužini (za ${duzinaCm.toInt()} cm očekuje se oko ${formatKg(procena)} kg)"
            }
        }
        return null
    }

    private fun formatKg(kg: Double): String =
        if (kg < 1) String.format(java.util.Locale.US, "%.2f", kg) else String.format(java.util.Locale.US, "%.1f", kg).removeSuffix(".0")
}
