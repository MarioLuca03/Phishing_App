package com.example.phishingapp.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImageTextExtractor(private val context: Context) {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun extractTextFromUri(imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Încarcă imaginea
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (bitmap == null) {
                    return@withContext null
                }
                
                // Creează InputImage pentru ML Kit
                val image = InputImage.fromBitmap(bitmap, 0)
                
                // Extrage textul folosind coroutines
                val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                    val task = textRecognizer.process(image)
                    task.addOnSuccessListener { text ->
                        continuation.resume(text)
                    }.addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
                }
                
                // Combină toate liniile de text
                result.text
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                    val task = textRecognizer.process(image)
                    task.addOnSuccessListener { text ->
                        continuation.resume(text)
                    }.addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
                }
                
                result.text
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun close() {
        textRecognizer.close()
    }
}

