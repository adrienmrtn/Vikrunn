package com.atala.app.core.account

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Why a request could not be sent (SupabaseClient.swift). HTTP errors are answers, not exceptions. */
sealed class SupabaseError : Exception() {
    class NotConfigured : SupabaseError()
    /** No connection, timeout, DNS failure... */
    class Network : SupabaseError()
    class Http(val status: Int, override val message: String) : SupabaseError()
}

/** Raw answer of a Supabase endpoint. HTTP errors are not thrown: check [isSuccess] or call [validated]. */
class SupabaseResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299

    /** The body as a JSON object, or an empty object. */
    val json: JsonObject by lazy {
        runCatching { SupabaseJson.parse(body) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    /** The body as a list of JSON objects (PostgREST answers), or an empty list. */
    val jsonRows: List<JsonObject> by lazy {
        runCatching { (SupabaseJson.parse(body) as? JsonArray)?.mapNotNull { it as? JsonObject } }.getOrNull().orEmpty()
    }

    /** Machine-readable error code: GoTrue `error_code`, PostgREST `code`, edge functions `error`. */
    val errorCode: String
        get() = listOf("error_code", "code", "error").firstNotNullOfOrNull { json.string(it)?.takeIf(String::isNotEmpty) } ?: ""

    /** Best human-readable message found in an error body (GoTrue, PostgREST and edge functions differ). */
    val errorMessage: String
        get() = listOf("msg", "error_description", "message", "error")
            .firstNotNullOfOrNull { json.string(it)?.takeIf(String::isNotEmpty) } ?: "Request failed ($status)."

    fun validated(): SupabaseResponse {
        if (!isSuccess) throw SupabaseError.Http(status, errorMessage)
        return this
    }
}

internal object SupabaseJson {
    fun parse(text: String): JsonElement? =
        if (text.isBlank()) null else kotlinx.serialization.json.Json.parseToJsonElement(text)
}

/** A string value of a JSON object (null for absent, null or non-string values). */
fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** Minimal REST client for Supabase (auth, PostgREST, edge functions) built on OkHttp. */
object SupabaseClient {
    private val jsonType = "application/json".toMediaType()

    /** No cache, no cookies: every answer comes from the server, and nothing is written to disk. */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(null)
            .retryOnConnectionFailure(false)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl? {
        val base = (SupabaseConfig.URL_STRING + path).toHttpUrlOrNull() ?: return null
        if (query.isEmpty()) return base
        // OkHttp encodes "+" in query values, so dates and e-mail addresses reach the server intact.
        return base.newBuilder().apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
    }

    /** Sends a request with the `apikey` header and, when given, the user's bearer token. */
    suspend fun send(
        path: String,
        method: String = "GET",
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        accessToken: String? = null,
        prefer: String? = null,
        timeoutSeconds: Long = 30,
    ): SupabaseResponse {
        if (!SupabaseConfig.isConfigured) throw SupabaseError.NotConfigured()
        val url = url(path, query) ?: throw SupabaseError.NotConfigured()
        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Accept", "application/json")
            .header("X-Client-Info", "atala-android")
            .header("Cache-Control", "no-cache")
            .apply {
                if (accessToken != null) header("Authorization", "Bearer $accessToken")
                if (prefer != null) header("Prefer", prefer)
                val requestBody = when {
                    body != null -> body.toRequestBody(jsonType)
                    method == "GET" || method == "HEAD" -> null
                    else -> ByteArray(0).toRequestBody(null)
                }
                method(method, requestBody)
            }
            .build()
        val timed = client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        // Reads and upserts can be repeated safely; anything else is sent once.
        val repeatable = method == "GET" || (prefer ?: "").contains("merge-duplicates")
        var attempt = 0
        while (true) {
            attempt += 1
            try {
                return timed.newCall(request).await()
            } catch (error: IOException) {
                // A connection dropped while the app was away: one more try is enough.
                val lost = error.message?.contains("reset", ignoreCase = true) == true ||
                    error.message?.contains("unexpected end of stream", ignoreCase = true) == true
                if (lost && repeatable && attempt == 1) continue
                throw SupabaseError.Network()
            }
        }
    }

    private suspend fun Call.await(): SupabaseResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use { SupabaseResponse(it.code, it.body?.string().orEmpty()) } }
                result.fold(
                    onSuccess = { if (continuation.isActive) continuation.resume(it) },
                    onFailure = { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(error as? IOException ?: IOException(error))
                        }
                    },
                )
            }
        })
    }
}
