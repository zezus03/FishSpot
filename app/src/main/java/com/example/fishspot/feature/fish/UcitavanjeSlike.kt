package com.example.fishspot.feature.fish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Učitava sliku onako kako je korisnik vidi: ispravlja rotaciju iz EXIF podataka
 * (kamera telefona često snima "položeno" i samo upiše orijentaciju) i odmah je smanjuje
 * tako da duža strana bude oko [maksimalnaStrana] px, umesto da učita punih 12+ MP.
 */
fun ucitajSlikuUspravno(context: Context, uri: Uri, maksimalnaStrana: Int = 1024): Bitmap {
    val resolver = context.contentResolver

    // 1. Samo dimenzije, da izračunamo koliko smanjiti
    val dimenzije = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dimenzije) }

    var umanjenje = 1
    while (maxOf(dimenzije.outWidth, dimenzije.outHeight) / (umanjenje * 2) >= maksimalnaStrana) {
        umanjenje *= 2
    }

    // 2. Učitavanje u smanjenoj veličini
    val opcije = BitmapFactory.Options().apply {
        inSampleSize = umanjenje
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val slika = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcije) }
        ?: throw IllegalArgumentException("Slika ne može da se učita: $uri")

    // 3. Rotacija i preslikavanje iz EXIF podataka
    val orijentacija = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val matrica = Matrix()
    when (orijentacija) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrica.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrica.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrica.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrica.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrica.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrica.postRotate(90f); matrica.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrica.postRotate(270f); matrica.postScale(-1f, 1f) }
        else -> return slika
    }
    val uspravna = Bitmap.createBitmap(slika, 0, 0, slika.width, slika.height, matrica, true)
    if (uspravna != slika) slika.recycle()
    return uspravna
}
