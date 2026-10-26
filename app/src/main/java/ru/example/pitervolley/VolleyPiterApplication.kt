package ru.example.pitervolley

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Общий загрузчик картинок: дисковый кеш на 100 МБ и плавное появление,
 * чтобы фото игроков не перекачивались при каждой прокрутке.
 */
class VolleyPiterApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ru.example.pitervolley.data.UiState.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .build()
}
