package com.photomatch.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    /** Milliseconds since epoch (date taken, or date added as fallback). */
    val time: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val name: String,
    val bucket: String,
    val mime: String,
) {
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 0.75f
}

/** Simple, fully-resolved EXIF/metadata used by the details panel. */
data class MediaDetails(
    val camera: String? = null,
    val lens: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    val focal: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val path: String? = null,
)
