@file:UseSerializers(InstantSerializer::class)

package com.atala.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class VoiceSettings(
    val enabled: Boolean = true,
    val announceSteps: Boolean = true,
    val announcePace: Boolean = true,
    val announceEveryKm: Boolean = true,
)

@Serializable
data class AppSettings(
    val voice: VoiceSettings = VoiceSettings(),
    /** Named after Apple Health on the iPhone; on Android it turns the Health Connect import on. */
    val healthImportEnabled: Boolean = false,
)

@Serializable
data class Account(
    @SerialName("userID") val userId: String,
    val email: String? = null,
    val isGuest: Boolean,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Instant? = null,
) {
    /** True for an account that syncs with the server (not a guest). */
    val isReal: Boolean get() = !isGuest
}

/**
 * Sync bookkeeping saved with the rest of the store (`SyncState` in SyncServiceState.swift).
 * "Dirty" dates use the phone's clock and mean "changed here, not sent yet".
 */
@Serializable
data class SyncState(
    /** False until the phone and the account have been matched once after sign-in. Nothing is sent before. */
    val reconciled: Boolean = false,
    val profileDirtyAt: Instant? = null,
    val planDirtyAt: Instant? = null,
    /** Run id (lowercased UUID) to the date of its last local change. */
    val runsDirtyAt: Map<String, Instant> = emptyMap(),
    /** Runs deleted on this phone that still have to be removed from the account. */
    @SerialName("deletedRunIDs") val deletedRunIds: List<String> = emptyList(),
    val profileStamp: String? = null,
    val planStamp: String? = null,
    val runStamps: Map<String, String> = emptyMap(),
) {
    val hasPendingChanges: Boolean
        get() = profileDirtyAt != null || planDirtyAt != null || runsDirtyAt.isNotEmpty() || deletedRunIds.isNotEmpty()
}
