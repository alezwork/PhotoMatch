package com.photomatch

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.photomatch.data.ThumbFetcher
import com.photomatch.data.ThumbKeyer

class PhotoMatchApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbFetcher.Factory(contentResolver))
                add(ThumbKeyer())
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.3).build()
            }
            .diskCache(null) // MediaStore already caches thumbnails on disk
            .crossfade(220)
            .build()
}
