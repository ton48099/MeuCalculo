package com.radardeganhos.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 100% offline com o modelo embarcado do ML Kit (latino/português).
 * Não precisa de internet nem do Google Play Services.
 */
class ScreenReader {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        } catch (t: Throwable) {
            if (cont.isActive) cont.resumeWithException(t)
        }
    }

    fun close() {
        recognizer.close()
    }
}

/** Reduz a captura para acelerar o OCR sem perder os textos grandes do card. */
fun Bitmap.scaledForOcr(maxHeight: Int = 2000): Bitmap {
    if (height <= maxHeight) return this
    val ratio = maxHeight.toFloat() / height
    val w = (width * ratio).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, w, maxHeight, true)
    return scaled
}
