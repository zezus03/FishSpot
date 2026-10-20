package com.example.fishspot.feature.fish

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proverava fish_detector.tflite na telefonu, kroz isti FishDetector kao aplikacija.
 *
 * Drugi test koristi slike iz /data/local/tmp/fishspot_test/{Fish,NoFish}
 * (adb push fishspot_test /data/local/tmp/) i preskače se ako ih nema.
 */
@RunWith(AndroidJUnit4::class)
class FishDetectorTest {

    private lateinit var detektor: FishDetector

    @Before
    fun pripremi() {
        detektor = FishDetector(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After
    fun zatvori() = detektor.zatvori()

    @Test
    fun modelSeUcitavaIDajeVerovatnocu() {
        val slika = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val rezultat = detektor.analiziraj(slika)

        assertNull("Model ne radi na telefonu: ${rezultat.greska}", rezultat.greska)
        assertTrue(rezultat.verovatnocaRibe in 0f..1f)
    }

    @Test
    fun tacnostNaTestSlikama() {
        val koren = File("/data/local/tmp/fishspot_test")
        assumeTrue("Nema test slika u ${koren.path}", File(koren, "Fish").isDirectory)

        var tacno = 0
        var ukupno = 0
        val ocene = mutableListOf<Pair<Float, Boolean>>() // (verovatnoća ribe, da li je stvarno riba)
        for ((folder, jeRiba) in listOf("Fish" to true, "NoFish" to false)) {
            File(koren, folder).listFiles()?.sortedBy { it.name }?.forEach { fajl ->
                val slika = BitmapFactory.decodeFile(fajl.path) ?: return@forEach
                val rezultat = detektor.analiziraj(slika)
                ukupno++
                ocene += rezultat.verovatnocaRibe to jeRiba
                if (rezultat.jeRiba == jeRiba) {
                    tacno++
                } else {
                    Log.w(TAG, "GREŠKA ${fajl.name}: očekivano ${if (jeRiba) "RIBA" else "NIJE RIBA"}, " +
                        "verovatnoća ribe ${"%.2f".format(rezultat.verovatnocaRibe)}")
                }
            }
        }

        // Koliko bi grešaka bilo na drugim pragovima (za izbor PRAG_RIBE u FishDetector-u)
        for (prag in listOf(0.60f, 0.65f, 0.70f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f)) {
            val propustene = ocene.count { (p, riba) -> riba && p <= prag }
            val lazne = ocene.count { (p, riba) -> !riba && p > prag }
            Log.i(TAG, "Prag ${"%.2f".format(prag)}: propuštene ribe $propustene, lažne ribe $lazne, " +
                "tačnost ${"%.1f".format((ukupno - propustene - lazne) * 100f / ukupno)}%")
        }

        val tacnost = tacno.toFloat() / ukupno
        Log.i(TAG, "Tačnost na telefonu: $tacno/$ukupno = ${"%.1f".format(tacnost * 100)}%")
        assertTrue("Tačnost ${"%.1f".format(tacnost * 100)}% je ispod 90%", tacnost >= 0.90f)
    }

    companion object {
        private const val TAG = "FishDetectorTest"
    }
}
