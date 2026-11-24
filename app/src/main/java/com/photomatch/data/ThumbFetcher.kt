package com.photomatch.data

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Image model for Coil.
 *
 * * Small requests (grids, strips, ambient glow) go through MediaStore's
 *   thumbnail cache (`loadThumbnail`) — instant, but capped at ~512 px.
 * * Large requests (the swipe card, full screen) decode the original file with
 *   `ImageDecoder` straight to the exact pixel size needed, so photos stay as
 *   sharp as the original. The decoder subsamples JPEGs in DCT space, honours
 *   EXIF rotation and produces hardware bitmaps (no extra copy on the GPU).
 */
data class Thumb(val uri: Uri, val isVideo: Boolean = false)

class ThumbFetcher(
    private val data: Thumb,
    private val options: Options,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }.coerceIn(32, 4096)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(32, 4096)
        val large = max(w, h) > SMALL_LIMIT
        val bmp = if (large && !data.isVideo) {
            runCatching { decodeSharp(w, h) }.getOrNull() ?: resolver.loadThumbnail(data.uri, Size(w, h), null)
        } else {
            resolver.loadThumbnail(data.uri, Size(w, h), null)
        }
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun decodeSharp(w: Int, h: Int) =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, data.uri)) { decoder, info, _ ->
            val iw = info.size.width; val ih = info.size.height
            // "cover" scale: the image must fill the requested box without being upscaled
            val s = max(w.toFloat() / iw, h.toFloat() / ih).coerceAtMost(1f)
            if (s < 1f) decoder.setTargetSize((iw * s).roundToInt().coerceAtLeast(1), (ih * s).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_DEFAULT
        }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options, resolver)
    }

    companion object {
        const val SMALL_LIMIT = 520
    }
}

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = data.uri.toString()
}
