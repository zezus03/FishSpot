package com.example.fishspot.feature.fish

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.max

/**
 * FishDetector - ISPRAVLJENA VERZIJA SA KOREKCIJOM NORMALIZACIJE
 */
class FishDetector(context: Context) {

    companion object {
        private const val INPUT_SIZE = 256
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var lastDetectionLog = ""

    init {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "fish_detector.tflite")
            interpreter = Interpreter(modelBuffer)
            isInitialized = true

            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()

            Log.d("FishDetector", "✅ Model učitan!")
            Log.d("FishDetector", "📊 Input shape: ${inputShape?.contentToString()}")
            Log.d("FishDetector", "📊 Output shape: ${outputShape?.contentToString()}")

        } catch (e: Exception) {
            Log.e("FishDetector", "❌ Greška pri učitavanju modela: ${e.message}", e)
            isInitialized = false
        }
    }

    /**
     * GLAVNA FUNKCIJA - Detektuje da li je riba na slici
     */
    fun imaRibuNaSlici(bitmap: Bitmap): Boolean {
        if (!isInitialized || interpreter == null) {
            Log.e("FishDetector", "❌ Model nije inicijalizovan!")
            return false
        }

        return try {
            val scores = detektujModel(bitmap)

            val fishScore = scores[1]
            val noFishScore = scores[0]

            Log.d("FishDetector", "🐟 Fish score: ${String.format("%.3f", fishScore)}")
            Log.d("FishDetector", "❌ NoFish score: ${String.format("%.3f", noFishScore)}")

            // Logika odlučivanja
            val isFish = fishScore > noFishScore
            val isHighConfidence = fishScore > CONFIDENCE_THRESHOLD
            val isDifferent = (fishScore - noFishScore) > 0.1

            val result = isFish && isHighConfidence && isDifferent

            lastDetectionLog = buildString {
                append("═══════════════════════\n")
                append("ANALIZA DETEKTOVANJA\n")
                append("═══════════════════════\n")
                append("Fish score:    ${String.format("%.1f%%", fishScore * 100)}\n")
                append("NoFish score:  ${String.format("%.1f%%", noFishScore * 100)}\n")
                append("Razlika:       ${String.format("%.1f%%", (fishScore - noFishScore) * 100)}\n")
                append("───────────────────────\n")
                append("Fish > NoFish?    ${if (isFish) "✅ DA" else "❌ NE"}\n")
                append("Score > 0.5?      ${if (isHighConfidence) "✅ DA" else "❌ NE"}\n")
                append("Razlika > 0.1?    ${if (isDifferent) "✅ DA" else "❌ NE"}\n")
                append("───────────────────────\n")
                append("REZULTAT: ${if (result) "🐟 RIBA" else "❌ NIJE RIBA"}\n")
                append("═══════════════════════\n")
            }

            Log.d("FishDetector", lastDetectionLog)

            result

        } catch (e: Exception) {
            Log.e("FishDetector", "❌ Greška pri detektovanju: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Pokreće inference i vraća [Fish score, NoFish score]
     */
    private fun detektujModel(bitmap: Bitmap): FloatArray {
        val interpreter = interpreter ?: return floatArrayOf(0f, 1f)

        val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // POPRAVKA NORMALIZACIJE:
        // EfficientNetB0 ima ugrađen rescaling sloj. Pikseli moraju ostati u opsegu [0, 255].
        // NormalizeOp(0f, 1f) znači: (vrednost - 0) / 1, što zadržava originalne vrednosti piksela.
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 1f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(argbBitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, 1),
            DataType.FLOAT32
        )

        interpreter.run(tensorImage.buffer, outputBuffer.buffer)

        val rawFishScore = outputBuffer.floatArray[0]
        val fishScore = rawFishScore.coerceIn(0f, 1f)
        val noFishScore = 1.0f - fishScore

        return floatArrayOf(fishScore, noFishScore)
    }

    fun getLastDetectionReport(): String {
        return lastDetectionLog.ifEmpty { "Još nije izvršena analiza" }
    }

    fun testModel(bitmap: Bitmap): DetectionResult {
        return try {
            val scores = detektujModel(bitmap)

            DetectionResult(
                fishScore = scores[0],
                noFishScore = scores[1],
                isFish = imaRibuNaSlici(bitmap),
                confidence = max(scores[0], scores[1]),
                difference = (scores[0] - scores[1]).coerceIn(-1f, 1f)
            )
        } catch (e: Exception) {
            DetectionResult(
                fishScore = 0f,
                noFishScore = 0f,
                isFish = false,
                confidence = 0f,
                difference = 0f,
                error = e.message
            )
        }
    }

    fun zatvori() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.d("FishDetector", "✅ Model zatvoren i memorija oslobođena")
        } catch (e: Exception) {
            Log.e("FishDetector", "Greška pri zatvaranju: ${e.message}")
        }
    }

    data class DetectionResult(
        val fishScore: Float,
        val noFishScore: Float,
        val isFish: Boolean,
        val confidence: Float,
        val difference: Float,
        val error: String? = null
    )
}