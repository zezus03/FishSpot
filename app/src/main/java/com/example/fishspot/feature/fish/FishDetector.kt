package com.example.fishspot.feature.fish

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

/**
 * Proverava da li je na slici riba (model fish_detector.tflite, EfficientNetV2B0, float16).
 *
 * Model je treniran skriptom ml/train_fish_detector.py sa image_dataset_from_directory,
 * koji klase sortira po abecedi: Fish = 0, NoFish = 1. Sigmoid izlaz modela je zato
 * verovatnoća klase 1, tj. da riba NIJE na slici.
 */
class FishDetector(context: Context) {

    companion object {
        private const val TAG = "FishDetector"
        private const val VELICINA_ULAZA = 256

        // Odluka: riba ako je verovatnoća ribe veća od ovog praga. Izabran na 128 slika sa telefona
        // (fishspot_test, model od 20.10.2026., FishDetectorTest na telefonu): 2 propuštene ribe,
        // 3 lažne, tačnost 96.1%. Prag 0.90 ima istu tačnost, ali propušta više pravih ulova.
        private const val PRAG_RIBE = 0.85f
    }

    data class Rezultat(
        val verovatnocaRibe: Float,
        val jeRiba: Boolean,
        val greska: String? = null
    )

    private var interpreter: Interpreter? = null

    // EfficientNet u Kerasu ima ugrađen sloj za skaliranje, pa pikseli ostaju u opsegu 0–255
    // (NormalizeOp(0, 1) ih ne menja, samo ih prebacuje u float)
    private val obradaSlike = ImageProcessor.Builder()
        .add(ResizeOp(VELICINA_ULAZA, VELICINA_ULAZA, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 1f))
        .build()

    init {
        try {
            interpreter = Interpreter(FileUtil.loadMappedFile(context, "fish_detector.tflite"))
            Log.d(TAG, "Model učitan, ulaz ${interpreter?.getInputTensor(0)?.shape()?.contentToString()}, " +
                "izlaz ${interpreter?.getOutputTensor(0)?.shape()?.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri učitavanju modela: ${e.message}", e)
        }
    }

    // Pokreće model jednom i vraća verovatnoću ribe i odluku
    fun analiziraj(slika: Bitmap): Rezultat {
        val model = interpreter ?: return Rezultat(0f, false, "Model nije učitan")

        return try {
            val argb = if (slika.config == Bitmap.Config.ARGB_8888) slika else slika.copy(Bitmap.Config.ARGB_8888, false)
            val ulaz = obradaSlike.process(TensorImage(DataType.FLOAT32).apply { load(argb) })
            val izlaz = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
            model.run(ulaz.buffer, izlaz.buffer)

            val verovatnocaNijeRiba = izlaz.floatArray[0].coerceIn(0f, 1f)
            val verovatnocaRibe = 1f - verovatnocaNijeRiba
            val jeRiba = verovatnocaRibe > PRAG_RIBE

            Log.d(TAG, "Riba: ${"%.1f".format(verovatnocaRibe * 100)}% " +
                "(slika ${slika.width}x${slika.height}) -> ${if (jeRiba) "RIBA" else "NIJE RIBA"}")
            Rezultat(verovatnocaRibe, jeRiba)
        } catch (e: Exception) {
            Log.e(TAG, "Greška pri analizi: ${e.message}", e)
            Rezultat(0f, false, e.message)
        }
    }

    fun zatvori() {
        interpreter?.close()
        interpreter = null
    }
}
