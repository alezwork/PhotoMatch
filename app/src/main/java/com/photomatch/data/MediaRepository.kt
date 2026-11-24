package com.photomatch.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Reads the whole library from MediaStore with a single, column-minimal cursor.
 * 100k items load in well under a second; pixels are never touched here.
 */
class MediaRepository(private val context: Context) {

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.ORIENTATION,
        )
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val out = ArrayList<MediaItem>(4096)
        context.contentResolver.query(
            uri, cols, sel, args,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            out.ensureCapacity(c.count)
            val iId = c.getColumnIndexOrThrow(cols[0])
            val iType = c.getColumnIndexOrThrow(cols[1])
            val iTaken = c.getColumnIndexOrThrow(cols[2])
            val iAdded = c.getColumnIndexOrThrow(cols[3])
            val iSize = c.getColumnIndexOrThrow(cols[4])
            val iW = c.getColumnIndexOrThrow(cols[5])
            val iH = c.getColumnIndexOrThrow(cols[6])
            val iDur = c.getColumnIndexOrThrow(cols[7])
            val iName = c.getColumnIndexOrThrow(cols[8])
            val iBucket = c.getColumnIndexOrThrow(cols[9])
            val iMime = c.getColumnIndexOrThrow(cols[10])
            val iOri = c.getColumnIndexOrThrow(cols[11])
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val taken = c.getLong(iTaken)
                val time = if (taken > 0) taken else c.getLong(iAdded) * 1000
                var w = c.getInt(iW)
                var h = c.getInt(iH)
                val ori = c.getInt(iOri)
                if (ori == 90 || ori == 270) { val t = w; w = h; h = t }
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = video,
                    time = time,
                    size = c.getLong(iSize),
                    width = w,
                    height = h,
                    durationMs = if (video) c.getLong(iDur) else 0,
                    name = c.getString(iName) ?: "",
                    bucket = c.getString(iBucket) ?: "",
                    mime = c.getString(iMime) ?: "",
                )
            }
        }
        out.sortByDescending { it.time }
        out
    }

    /** 32x32 grayscale thumbnail for hashing, straight from the system thumbnail cache. */
    fun grayThumb(uri: Uri, into: ByteArray, offset: Int, scratch: Bitmap, canvas: Canvas, paint: Paint): Boolean {
        return try {
            val t = context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
            scratch.eraseColor(0)
            canvas.drawBitmap(t, null, android.graphics.Rect(0, 0, scratch.width, scratch.height), paint)
            t.recycle()
            val n = scratch.width * scratch.height
            val px = IntArray(n)
            scratch.getPixels(px, 0, scratch.width, 0, 0, scratch.width, scratch.height)
            for (i in 0 until n) into[offset + i] = (px[i] and 0xFF).toByte()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun details(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext MediaDetails()
        try {
            val u = try { MediaStore.setRequireOriginal(item.uri) } catch (_: Throwable) { item.uri }
            val stream = try { context.contentResolver.openInputStream(u) } catch (_: SecurityException) {
                context.contentResolver.openInputStream(item.uri)
            }
            stream?.use { s ->
                val e = ExifInterface(s)
                val ll = e.latLong
                fun t(tag: String) = e.getAttribute(tag)?.takeIf { it.isNotBlank() }
                val make = t(ExifInterface.TAG_MAKE)
                val model = t(ExifInterface.TAG_MODEL)
                val camera = listOfNotNull(make, model?.removePrefix(make ?: "")?.trim()).joinToString(" ").ifBlank { null }
                val fnum = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                val exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                MediaDetails(
                    camera = camera,
                    lens = t(ExifInterface.TAG_LENS_MODEL),
                    iso = t(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                    aperture = if (fnum > 0) "ƒ/" + String.format(Locale.US, "%.1f", fnum) else null,
                    exposure = if (exp > 0) (if (exp < 1) "1/${(1 / exp).toInt()} с" else String.format(Locale.US, "%.1f с", exp)) else null,
                    focal = if (focal > 0) "${focal.toInt()} мм" else null,
                    lat = ll?.get(0), lon = ll?.get(1),
                )
            } ?: MediaDetails()
        } catch (_: Throwable) {
            MediaDetails()
        }
    }

    /** Up to [n] dominant colours of a photo (ARGB), brightest first. Uses a 48 px thumbnail. */
    suspend fun palette(item: MediaItem, n: Int = 6): List<Int> = withContext(Dispatchers.IO) {
        try {
            val bmp = context.contentResolver.loadThumbnail(item.uri, Size(48, 48), null)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            bmp.recycle()
            // 4 bits per channel buckets, average colour per bucket
            val cnt = IntArray(4096); val rs = LongArray(4096); val gs = LongArray(4096); val bs = LongArray(4096)
            for (c in px) {
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                cnt[k]++; rs[k] += r.toLong(); gs[k] += g.toLong(); bs[k] += b.toLong()
            }
            val order = (0 until 4096).filter { cnt[it] > 0 }.sortedByDescending { cnt[it] }
            val out = ArrayList<Int>()
            for (k in order) {
                val r = (rs[k] / cnt[k]).toInt(); val g = (gs[k] / cnt[k]).toInt(); val b = (bs[k] / cnt[k]).toInt()
                val far = out.all { o ->
                    val dr = ((o shr 16) and 255) - r; val dg = ((o shr 8) and 255) - g; val db = (o and 255) - b
                    dr * dr + dg * dg + db * db > 2400
                }
                if (far) out += (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                if (out.size == n) break
            }
            out.sortedByDescending { c -> 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    companion object {
        fun grayPaint() = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    }
}
