package com.example.gpstest.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface AGpsFileHandler {
    suspend fun readFile(uri: Uri): Result<ByteArray>
    fun getSupportedTypes(): List<String>
}

class AGpsFileHandlerImpl(
    private val context: Context
) : AGpsFileHandler {

    override suspend fun readFile(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Cannot open file: $uri"))
            
            val data = inputStream.use { it.readBytes() }
            
            if (data.isEmpty()) {
                return@withContext Result.failure(IOException("File is empty"))
            }
            
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSupportedTypes(): List<String> {
        return listOf("bin", "xml", "txt")
    }
}
