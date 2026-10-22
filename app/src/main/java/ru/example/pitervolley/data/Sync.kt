package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.example.pitervolley.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the dataset published by the ETL (GitHub Pages) and swaps it in.
 * Falls back silently: on any failure the app keeps the bundled/cached data.
 */
object Sync {

    private const val CACHE = "data.json"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun cacheFile(context: Context): File = File(context.filesDir, CACHE)

    suspend fun refresh(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (BuildConfig.DATA_URL.isBlank()) return@runCatching 0
            val conn = URL(BuildConfig.DATA_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val ds = json.decodeFromString<Dataset>(text)
                require(ds.schema >= 1) { "unsupported schema ${ds.schema}" }
                require(ds.divisions.isNotEmpty()) { "empty dataset" }

                val tmp = File(context.filesDir, "$CACHE.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(cacheFile(context))) {
                    cacheFile(context).writeText(text)
                    tmp.delete()
                }
                Repo.apply(text)
                ds.games.size
            } finally {
                conn.disconnect()
            }
        }
    }
}
