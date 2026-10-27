package com.example.autocontrol

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

/** On-device Latin + Korean OCR, sequential so ownership of the Bitmap is clear. */
class TextScanner {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val korean = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    fun scan(bitmap: Bitmap, onResult: (String) -> Unit) {
        val input = InputImage.fromBitmap(bitmap, 0)
        latin.process(input)
            .addOnSuccessListener { latinResult ->
                korean.process(input)
                    .addOnSuccessListener { koreanResult ->
                        onResult(latinResult.text + "\n" + koreanResult.text)
                    }
                    .addOnFailureListener { onResult(latinResult.text) }
            }
            .addOnFailureListener {
                korean.process(input)
                    .addOnSuccessListener { onResult(it.text) }
                    .addOnFailureListener { onResult("") }
            }
    }

    fun close() {
        latin.close()
        korean.close()
    }
}
