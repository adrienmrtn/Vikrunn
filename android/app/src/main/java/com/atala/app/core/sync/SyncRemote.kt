package com.atala.app.core.sync

import com.atala.app.core.account.AuthError
import com.atala.app.core.account.AuthService
import com.atala.app.core.account.SupabaseResponse
import com.atala.app.core.account.string
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.store.AppStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** PostgREST calls made as the signed-in user (row level security limits them to their own rows). */
object SyncRemote {
    suspend fun rows(table: String, query: List<Pair<String, String>>, store: AppStore): List<JsonObject> =
        send(store, "/rest/v1/$table", "GET", query, null, null).jsonRows

    /** Inserts or updates rows and returns the columns asked for (to read the server's `updated_at`). */
    suspend fun upsert(
        table: String,
        conflict: String,
        rows: List<JsonObject>,
        returning: String,
        store: AppStore,
    ): List<JsonObject> {
        if (rows.isEmpty()) return emptyList()
        val query = listOf("on_conflict" to conflict, "select" to returning)
        return send(
            store, "/rest/v1/$table", "POST", query, JsonArray(rows).toString(),
            "resolution=merge-duplicates,return=representation",
        ).jsonRows
    }

    /** Changes columns of the rows matched by [query] (PostgREST filters). */
    suspend fun update(table: String, query: List<Pair<String, String>>, values: JsonObject, store: AppStore) {
        send(store, "/rest/v1/$table", "PATCH", query, values.toString(), "return=minimal")
    }

    private suspend fun send(
        store: AppStore,
        path: String,
        method: String,
        query: List<Pair<String, String>>,
        body: String?,
        prefer: String?,
    ): SupabaseResponse {
        val response = try {
            AuthService.authorizedSend(store, path, method, query, body, prefer, timeoutSeconds = 40)
        } catch (error: AuthError) {
            throw if (error == AuthError.SessionExpired) SyncFailure.SessionExpired else SyncFailure.Offline
        } catch (_: Throwable) {
            throw SyncFailure.Offline
        }
        if (!response.isSuccess) {
            if (response.status == 401) throw SyncFailure.SessionExpired
            throw SyncFailure.Server(response.status)
        }
        return response
    }
}

/**
 * Reads the account: profile, plan (when changed or [everything]), the list of runs, and the runs
 * the phone does not have in that version. Changes nothing locally.
 */
internal suspend fun fetchSnapshot(store: AppStore, everything: Boolean): SyncRemoteSnapshot {
    val account = store.current.account?.takeIf { !it.isGuest } ?: throw SyncFailure.Interrupted
    val state = store.current.syncState
    val mine = "user_id" to "eq.${account.userId}"
    val one = "limit" to "1"
    val snapshot = SyncRemoteSnapshot()

    val profiles = SyncRemote.rows("profiles", listOf("select" to "answers,settings,updated_at", mine, one), store)
    profiles.firstOrNull()?.let { row ->
        val stamp = row.string("updated_at") ?: return@let
        snapshot.profileStamp = stamp
        snapshot.answers = SyncCoding.decode(OnboardingAnswers.serializer(), row["answers"])
        snapshot.settings = SyncCoding.decode(AppSettings.serializer(), row["settings"])
    }

    val planMeta = SyncRemote.rows("plans", listOf("select" to "updated_at", mine, one), store)
    planMeta.firstOrNull()?.string("updated_at")?.let { stamp ->
        snapshot.planStamp = stamp
        if (everything || stamp != state.planStamp) {
            val plans = SyncRemote.rows("plans", listOf("select" to "plan,updated_at", mine, one), store)
            plans.firstOrNull()?.let { row ->
                val fresh = row.string("updated_at") ?: return@let
                snapshot.planStamp = fresh
                snapshot.plan = SyncCoding.decode(TrainingPlan.serializer(), row["plan"])
            }
        }
    }

    val runMeta = SyncRemote.rows(
        "runs", listOf("select" to "id,updated_at,deleted_at", mine, "order" to "updated_at.asc"), store,
    )
    val localKeys = store.current.runs.map { SyncCoding.key(it.id) }.toSet()
    val wanted = mutableListOf<String>()
    for (row in runMeta) {
        val id = row.string("id") ?: continue
        val stamp = row.string("updated_at") ?: continue
        val key = id.lowercase()
        val deletedAt = row["deleted_at"]
        val deleted = deletedAt != null && deletedAt !is JsonNull
        snapshot.runs.add(SyncRemoteRun(key, stamp, deleted))
        if (deleted || key in state.deletedRunIds) continue
        if (everything || key !in localKeys || state.runStamps[key] != stamp) wanted.add(key)
    }

    for (chunk in wanted.chunked(15)) {
        val rows = SyncRemote.rows(
            "runs", listOf("select" to "id,run", mine, "id" to "in.(${chunk.joinToString(",")})"), store,
        )
        for (row in rows) {
            val id = row.string("id") ?: continue
            val record = SyncCoding.decode(RunRecord.serializer(), row["run"]) ?: continue
            snapshot.runRecords[id.lowercase()] = record
        }
    }
    return snapshot
}
