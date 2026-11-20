package com.atala.app.core.sync

import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.store.AppState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** What the sync is doing, for the screens that show it (SyncServiceState.swift). */
enum class SyncActivity {
    Idle,
    /** Regular background exchange with the account. */
    Syncing,
    /** Right after sign-in: the account's plan and runs are being brought to the phone. */
    Restoring,
}

/** Why an exchange with the account did not go through. */
sealed class SyncFailure : Exception() {
    /** No connection, timeout. Nothing is lost: the changes stay marked and go out next time. */
    data object Offline : SyncFailure()
    /** The session is dead: the user has to sign in again. */
    data object SessionExpired : SyncFailure()
    /** The server refused or failed (HTTP status). */
    data class Server(val status: Int) : SyncFailure()
    /** The account changed (sign-out, deletion) while the exchange was running. */
    data object Interrupted : SyncFailure()

    val userMessage: String
        get() = when (this) {
            Offline -> L("We couldn't reach your account. Check your connection and try again.")
            SessionExpired -> L("Your session has expired. Please sign in again.")
            is Server -> L("Our server is having a moment. Please try again shortly.")
            Interrupted -> L("That was interrupted. Please try again.")
        }

    override val message: String get() = userMessage
}

/** Result of a sync, for the screens that show it. */
sealed interface SyncOutcome {
    data object Done : SyncOutcome
    /** Guest, signed out, or account not matched with the phone yet. */
    data object NotSignedIn : SyncOutcome
    data class Failed(val failure: SyncFailure) : SyncOutcome
}

data class SyncPullResult(
    val outcome: SyncOutcome,
    /** True when server data replaced or completed local data. */
    val changed: Boolean,
)

/** Which side wins when the phone and the account both hold a profile or a plan. */
enum class SyncMergeBias {
    /** Regular sync: the most recent change wins. */
    LastWriteWins,
    /** Right after sign-in, "Use my saved plan": the account wins. */
    Account,
    /** Right after sign-in, "Use this phone's plan": the phone wins. */
    Phone,
}

/** What to do when someone signs in on a phone that already holds data. */
enum class SyncSignInPolicy {
    /** Welcome screen and end of onboarding: the account's plan, when there is one, replaces the phone's. */
    PreferAccount,
    /** Profile: when both sides hold a plan or runs, the user chooses. */
    AskIfBoth,
}

sealed interface SyncSignInOutcome {
    /** The account's plan is now on the phone: open the app. */
    data object Restored : SyncSignInOutcome
    /** Nothing to restore (new account, or no plan saved yet): carry on; the phone's data is uploaded. */
    data object Continued : SyncSignInOutcome
    /** Both sides hold data: [SyncService.choicePending] is set and the user must choose. */
    data object NeedsChoice : SyncSignInOutcome
    data class Failed(val failure: SyncFailure) : SyncSignInOutcome
}

/** What the store held before a sign-in attempt, to put back if the account cannot be read. */
data class SyncPreviousSession(val account: Account?, val state: SyncState, val lastSyncedAt: Instant?)

/** What the account holds, downloaded before anything is changed on the phone. */
data class SyncRemoteSnapshot(
    var profileStamp: String? = null,
    var answers: OnboardingAnswers? = null,
    var settings: AppSettings? = null,
    var planStamp: String? = null,
    /** Downloaded only when it differs from what the phone last saw. */
    var plan: TrainingPlan? = null,
    val runs: MutableList<SyncRemoteRun> = mutableListOf(),
    /** Full runs, by id, for the ones that had to be downloaded. */
    val runRecords: MutableMap<String, RunRecord> = mutableMapOf(),
) {
    val hasLiveRuns: Boolean get() = runs.any { !it.deleted }
}

data class SyncRemoteRun(val key: String, val stamp: String, val deleted: Boolean)

/** JSON helpers shared by the sync code. Dates are ISO 8601, like the store's own file. */
object SyncCoding {
    /** Lowercased UUID: the id of a run in the account and in the bookkeeping. */
    fun key(id: UUID): String = id.toString().lowercase()

    /** A model as a JSON value ready for a `jsonb` column, exactly as the iPhone writes it. */
    fun <T> json(serializer: KSerializer<T>, value: T): JsonElement = AtalaJson.encodeToJsonElement(serializer, value)

    /** A `jsonb` value read from the server back into a model. Null when it does not match the model. */
    fun <T> decode(serializer: KSerializer<T>, value: JsonElement?): T? {
        if (value == null || value is JsonNull || value !is JsonObject) return null
        return runCatching { AtalaJson.decodeFromJsonElement(serializer, value) }.getOrNull()
    }

    fun isoString(date: Instant): String = DateTimeFormatter.ISO_INSTANT.format(date.truncatedTo(ChronoUnit.SECONDS))

    /** Parses a Postgres `timestamptz` as PostgREST prints it ("2026-11-17T23:08:08.336123+00:00"). */
    fun date(fromServer: String?): Instant? {
        var value = fromServer?.takeIf { it.isNotEmpty() } ?: return null
        // Drop the fraction: its length varies and one second is precise enough to compare changes.
        val dot = value.indexOf('.')
        if (dot >= 0) {
            var end = dot + 1
            while (end < value.length && value[end].isDigit()) end++
            value = value.removeRange(dot, end)
        }
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse { runCatching { Instant.parse(value) }.getOrNull() }
    }
}

/** True with a real account (not a guest, not signed out). */
val AppState.hasRealAccount: Boolean get() = account?.isGuest == false
