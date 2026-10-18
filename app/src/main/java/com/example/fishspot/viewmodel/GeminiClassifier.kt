package com.example.fishspot.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.example.fishspot.BuildConfig
class GeminiClassifier {
    // Tvoj pravi API ključ
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Koristimo gemini-1.5-flash (najstabilniji i najbrži model za slike)
    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    // Vraća Pair: Prva vrednost je Vrsta, druga vrednost je Težina
    suspend fun prepoznajRibu(bitmap: Bitmap, duzina: Double? = null): Pair<String, String>? {
        val promptTekst = if (duzina != null && duzina > 0) {
            "Ti si stručni ihtiolog. Prepoznaj vrstu ribe sa slike. " +
                    "Takođe, poznato je da je riba sa slike dugačka tačno $duzina cm. " +
                    "Na osnovu njene vrste i dužine, proceni njenu prosečnu težinu u kilogramima. " +
                    "Vrati mi rezultat ISKLJUČIVO u ovom formatu: 'Narodni naziv (Latinski naziv)|težina'. " +
                    "Na primer: 'Štuka (Esox lucius)|2.4'. Ne piši nikakav drugi tekst. " +
                    "Ako ne možeš da prepoznaš ribu, vrati 'Nepoznato|0'."
        } else {
            // Ako korisnik nije izmerio ribu, tražimo samo vrstu
            "Ti si stručni ihtiolog. Prepoznaj vrstu ribe sa slike. " +
                    "Vrati mi rezultat ISKLJUČIVO u ovom formatu: 'Narodni naziv (Latinski naziv)|0'. " +
                    "Na primer: 'Štuka (Esox lucius)|0'. Ne piši nikakav drugi tekst. " +
                    "Ako ne možeš da prepoznaš ribu, vrati 'Nepoznato|0'."
        }

        // UKLONJEN TRY-CATCH: Puštamo grešku da ode u UI ekran kako bi iskočio Toast!
        val response = model.generateContent(
            content {
                image(bitmap)
                text(promptTekst)
            }
        )

        val resultText = response.text?.trim() ?: "Nepoznato|0"

        // LOGOVANJE: Ispisujemo u konzoli tačno ono što je Gemini rekao
        Log.d("GeminiML", "Sirov odgovor od AI: ${resultText.replace("\n", " ")}")

        // Sečemo tekst po uspravnoj crti
        val delovi = resultText.split("|")
        return if (delovi.size >= 2) {
            Pair(delovi[0].trim(), delovi[1].trim()) // (Vrsta, Težina)
        } else {
            Pair(resultText, "") // Ako AI zaboravi uspravnu crtu
        }
    }
}