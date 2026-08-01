package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtil {
    fun getFileFromUri(context: Context, uri: Uri, maxSizeMb: Int): Pair<File?, String?> {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "temp_file"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = it.getString(nameIndex)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }

            val maxSizeBytes = maxSizeMb * 1024 * 1024L
            if (size > maxSizeBytes) {
                return Pair(null, "File too large (limit is ${maxSizeMb}MB)")
            }

            val tempFile = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalRead > maxSizeBytes) {
                            return Pair(null, "File too large (limit is ${maxSizeMb}MB)")
                        }
                    }
                    output.flush()
                }
            }
            return Pair(tempFile, null)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(null, "Failed to read file: ${e.message}")
        }
    }
}
