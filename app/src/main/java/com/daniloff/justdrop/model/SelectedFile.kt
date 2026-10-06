package com.daniloff.justdrop.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?
) : Parcelable
