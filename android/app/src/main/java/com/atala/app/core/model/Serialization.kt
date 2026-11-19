package com.atala.app.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * JSON as the iPhone app writes it (Swift `Codable` with `.iso8601` dates), so the saved file and
 * the `jsonb` columns of the account read the same on both platforms: absent keys for nil values,
 * every other value written, unknown keys ignored.
 */
val AtalaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** Swift writes a UUID in upper case. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString().uppercase())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** ISO 8601 to the second ("2026-11-19T08:30:00Z"): Swift's `.iso8601` strategy rejects fractions. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS)))

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }
    }
}
