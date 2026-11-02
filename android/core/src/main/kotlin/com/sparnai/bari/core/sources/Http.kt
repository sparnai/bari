package com.sparnai.bari.core.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

/** Small HTTP helper with retries for rate limiting and server errors. */
class Http(val client: OkHttpClient = defaultClient()) {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(url, headers, null)

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
        execute(url, headers + ("Content-Type" to "application/json"), json)

    private suspend fun execute(url: String, headers: Map<String, String>, jsonBody: String?): String =
        withContext(Dispatchers.IO) {
            var lastError: Exception = ApiException("Nepavyko gauti kainos")
            for (attempt in 0 until 3) {
                if (attempt > 0) delay(1500L * attempt)
                val builder = Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                headers.forEach { (k, v) -> builder.header(k, v) }
                if (jsonBody != null) builder.post(jsonBody.toRequestBody(JSON))
                val response = try {
                    client.newCall(builder.build()).execute()
                } catch (e: IOException) {
                    lastError = e
                    continue
                }
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) return@withContext body
                    lastError = ApiException("${hostOf(url)}: HTTP ${it.code}")
                    // Only rate limiting and server errors are worth retrying.
                    if (it.code != 429 && it.code < 500) throw lastError
                }
            }
            throw lastError
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        private fun hostOf(url: String): String =
            url.substringAfter("://").substringBefore('/').removePrefix("www.")
    }
}
