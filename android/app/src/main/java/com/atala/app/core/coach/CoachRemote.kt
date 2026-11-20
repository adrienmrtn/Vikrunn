package com.atala.app.core.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * The few Supabase calls the coach needs (edge function `coach`, table `coach_messages`), with the
 * account's token. Public project settings, as in SupabaseConfig.swift: the publishable key is
 * public by design. When a shared Supabase client exists in core/account, these calls can move to it.
 */
internal object CoachRemote {
    private const val URL_STRING = "https://kjmfbogagvknyzprppdu.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_oaRQC-5ha2WUk5RWfTGF2w_Al28lax3"

    class Response(val status: Int, val body: String) {
        val isSuccess: Boolean get() = status in 200..299

        val json: JsonObject
            get() = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

        val rows: List<JsonObject>
            get() = runCatching { (Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject } }
                .getOrNull().orEmpty()

        /** Machine-readable error code: GoTrue `error_code`, PostgREST `code`, edge functions `error`. */
        val errorCode: String
            get() {
                val body = json
                for (key in listOf("error_code", "code", "error")) {
                    val text = runCatching { body[key]?.jsonPrimitive?.content }.getOrNull()
                    if (!text.isNullOrEmpty()) return text
                }
                return ""
            }
    }

    /** Sends a request; null when the server could not be reached. Runs off the main thread. */
    suspend fun send(
        path: String,
        method: String,
        token: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonElement? = null,
        timeoutMs: Int = 30_000,
    ): Response? = withContext(Dispatchers.IO) {
        runCatching {
            val queryText = query.joinToString("&") { (name, value) ->
                java.net.URLEncoder.encode(name, "UTF-8") + "=" + java.net.URLEncoder.encode(value, "UTF-8")
            }
            val url = URL(URL_STRING + path + if (queryText.isEmpty()) "" else "?$queryText")
            val connection = url.openConnection() as HttpURLConnection
            try {
                // PATCH is accepted by Android's HttpURLConnection.
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = timeoutMs
                connection.useCaches = false
                connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-Client-Info", "atala-android")
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Response(status, text)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
