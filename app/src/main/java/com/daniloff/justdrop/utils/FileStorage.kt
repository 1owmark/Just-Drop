package com.daniloff.justdrop.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.InputStream

class FileStorage(private val context: Context) {
    fun save(
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ) {
        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val relativePath = when {
            mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/Just Drop")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                inputStream.copyTo(output)
            } ?: throw IllegalStateException("Output stream return null")
            val updatedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }

            context.contentResolver.update(
                uri,
                updatedValues,
                null,
                null
            )
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}