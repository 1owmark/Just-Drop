package com.daniloff.justdrop.model

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?
)
