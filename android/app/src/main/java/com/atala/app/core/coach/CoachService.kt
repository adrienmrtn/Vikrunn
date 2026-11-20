package com.atala.app.core.coach

import com.atala.app.core.account.AuthService
import com.atala.app.core.account.SupabaseResponse
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.ChatRole
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanChange
import com.atala.app.core.model.PlanProposal
import com.atala.app.core.model.ProposalStatus
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import com.atala.app.core.sync.SyncRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Talks to the coach (CoachService.swift). Uses the `coach` edge function when an account is signed
 * in, and answers from the plan on the phone otherwise (guest, offline, server error).
 */
object CoachService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isSending = MutableStateFlow(false)

    /** True while the coach's answer is on its way. Survives leaving the tab. */
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /**
     * Adds the user's message to the conversation, then the coach's answer when it comes. Talking
     * to the coach is part of Atala Pro: the caller checks it.
     */
    fun post(text: String, store: AppStore) {
        val trimmed = text.trim().take(2000)
        if (trimmed.isEmpty() || _isSending.value) return
        store.update { it.copy(messages = it.messages + ChatMessage(role = ChatRole.User, text = trimmed)) }
        _isSending.value = true
        scope.launch {
            val reply = try {
                send(trimmed, store)
            } catch (error: Exception) {
                CoachLocalReplies.reply(trimmed, store.current)
            }
            store.update { it.copy(messages = it.messages + reply) }
            _isSending.value = false
        }
    }

    /** The coach's answer to the user's message (already in the conversation or not). Never fails. */
    suspend fun send(text: String, store: AppStore): ChatMessage {
        val started = System.currentTimeMillis()
        remoteReply(text, store)?.let { return it }
        val local = CoachLocalReplies.reply(text, store.current)
        // A reply that pops up instantly feels wrong: leave a short beat.
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < 900) delay(900 - elapsed)
        return local
    }

    // Server

    /**
     * When the server said the AI coach is not available (no key yet, daily limit), the app answers
     * from the phone until this time instead of asking again for every message.
     */
    private var remotePausedUntil: Long? = null

    private suspend fun remoteReply(text: String, store: AppStore): ChatMessage? {
        if (store.current.account?.isReal != true) return null
        remotePausedUntil?.let { if (it > System.currentTimeMillis()) return null }
        val state = store.current
        val payload = buildJsonObject {
            put("messages", history(text, state))
            put("context", context(state))
        }
        val response = send(store, "/functions/v1/coach", "POST", body = payload, timeoutSeconds = 45) ?: return null
        if (!response.isSuccess) {
            when (response.errorCode) {
                "coach_not_configured" -> remotePausedUntil = System.currentTimeMillis() + 10 * 60_000
                "daily_limit" -> remotePausedUntil = System.currentTimeMillis() + 60 * 60_000
            }
            return null
        }
        remotePausedUntil = null
        val json = response.json
        val reply = json.string("reply")?.trim().orEmpty()
        if (reply.isEmpty()) return null
        // Same id as the row kept in the account, so the answer to a proposal can be saved there.
        val id = json.string("messageId")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
        val proposal = (json["proposal"] as? JsonObject)?.let { proposal(it, store.current) }
        return ChatMessage(id = id, role = ChatRole.Coach, text = reply, proposal = proposal)
    }

    /** The last messages of the conversation, ending with the user's new message. */
    private fun history(text: String, state: AppState): JsonArray {
        val items = mutableListOf<Pair<String, String>>()
        for (message in state.messages.takeLast(20)) {
            val body = message.text.trim()
            if (body.isEmpty()) continue
            items.add((if (message.role == ChatRole.User) "user" else "coach") to body.take(3500))
        }
        val newText = text.trim().take(3500)
        if (items.lastOrNull() != ("user" to newText)) items.add("user" to newText)
        // The coach answers in the app's language. Said in the message itself, so it works with every
        // version of the coach service; the user never sees this line.
        if (AppLanguage.current != "en" && items.isNotEmpty()) {
            val (role, body) = items.last()
            items[items.size - 1] = role to (body + "\n\n(Answer in " + AppLanguage.englishName + ".)")
        }
        return buildJsonArray {
            items.forEach { (role, body) -> add(buildJsonObject { put("role", role); put("text", body) }) }
        }
    }

    // Context sent to the coach

    private val dayFormatter: DateTimeFormatter get() = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun dayString(date: Instant): String = dayFormatter.format(date.atZone(ZoneId.systemDefault()).toLocalDate())

    fun day(string: String): Instant? = runCatching {
        LocalDate.parse(string, dayFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant()
    }.getOrNull()

    private fun weekdayName(date: Instant): String = Weekday.long(PlanCalendar.mondayIndex(date))

    private fun <T> raw(serializer: kotlinx.serialization.KSerializer<T>, value: T): String =
        AtalaJson.encodeToJsonElement(serializer, value).jsonPrimitive.content

    private fun rounded(value: Double): Double = if (value.isFinite()) (value * 10).roundToInt() / 10.0 else 0.0

    /**
     * What the coach knows: today, the last two weeks and the next three of the plan, the recent
     * runs, the paces, the goal, the tone and the coach's name.
     */
    private fun context(state: AppState): JsonObject {
        val today = PlanCalendar.startOfDay(Instant.now())
        val from = PlanCalendar.addDays(today, -14)
        val horizon = PlanCalendar.addDays(today, 22)
        val window = state.allWorkouts.filter { it.date >= from && it.date < horizon }.take(70)
        val workouts = buildJsonArray {
            window.forEach { workout ->
                add(buildJsonObject {
                    put("id", workout.id.toString().uppercase())
                    put("date", dayString(workout.date))
                    put("weekday", weekdayName(workout.date))
                    put("kind", raw(WorkoutKind.serializer(), workout.kind))
                    put("title", workout.title)
                    put("distanceKm", rounded(workout.distanceKm))
                    put("durationMin", workout.durationMin)
                    put("status", raw(WorkoutStatus.serializer(), workout.status))
                })
            }
        }
        val recentRuns = buildJsonArray {
            state.runs.sortedByDescending { it.date }.take(8).forEach { run ->
                add(buildJsonObject {
                    put("date", dayString(run.date))
                    put("weekday", weekdayName(run.date))
                    put("title", run.title)
                    put("distanceKm", rounded(run.distanceKm))
                    put("durationMin", run.durationSec / 60)
                    put("avgPace", Fmt.pace(run.avgPace))
                    run.feeling?.let { put("feeling", it.title) }
                })
            }
        }
        return buildJsonObject {
            put("firstName", state.firstName)
            put("coachName", state.coach.name)
            put("tone", raw(com.atala.app.core.model.CoachTone.serializer(), state.answers.tone))
            put("language", AppLanguage.current)
            put("today", dayString(today))
            put("weekday", weekdayName(today))
            put("workouts", workouts)
            put("recentRuns", recentRuns)
            val plan = state.plan
            if (plan != null) {
                put("goal", plan.goal.title)
                plan.raceDate?.let { put("raceDate", dayString(it)) }
                plan.targetSeconds?.let { put("targetSeconds", it) }
                put("paces", buildJsonObject {
                    put("easy", Fmt.pace(plan.paces.easy))
                    put("long", Fmt.pace(plan.paces.long))
                    put("tempo", Fmt.pace(plan.paces.tempo))
                    put("interval", Fmt.pace(plan.paces.interval))
                    put("race", Fmt.pace(plan.paces.race))
                    put("unit", "min/km")
                })
            } else {
                state.answers.goal?.let { put("goal", it.title) }
            }
        }
    }

    // Proposal parsing

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    /** Maps the server proposal to real workouts of the plan. Unknown ids and empty changes are dropped. */
    fun proposal(raw: JsonObject, state: AppState, keepUnknown: Boolean = false): PlanProposal? {
        val rawChanges = raw["changes"] as? JsonArray ?: return null
        val changes = mutableListOf<PlanChange>()
        for (element in rawChanges) {
            val item = element as? JsonObject ?: continue
            val workoutId = item.string("workoutId")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: continue
            val workout = state.workout(workoutId)
            if (workout != null) {
                if (workout.kind == WorkoutKind.Race) continue
            } else if (!keepUnknown) {
                // A fresh proposal only ever touches sessions of the current plan.
                continue
            }
            if ((item["remove"] as? JsonPrimitive)?.booleanOrNull == true) {
                changes.add(PlanChange(workoutId = workoutId, remove = true))
                continue
            }
            val kind = item.string("newKind")?.let { name ->
                WorkoutKind.entries.firstOrNull { raw(WorkoutKind.serializer(), it) == name }
            }?.takeIf { it != WorkoutKind.Race && it != WorkoutKind.Free }
            val change = PlanChange(
                workoutId = workoutId,
                newDate = item.string("newDate")?.let(::day),
                newKind = kind,
                newTitle = item.string("newTitle")?.takeIf { it.isNotEmpty() },
                newDistanceKm = item.number("newDistanceKm")?.takeIf { it in 0.0..60.0 },
                newDurationMin = item.number("newDurationMin")?.toInt()?.takeIf { it in 0..400 },
            )
            val changesSomething = change.newDate != null || change.newKind != null || change.newTitle != null ||
                change.newDistanceKm != null || change.newDurationMin != null
            if (changesSomething) changes.add(change)
        }
        if (changes.isEmpty()) return null
        return PlanProposal(summary = raw.string("summary")?.trim().orEmpty(), changes = changes)
    }

    // History kept in the account (CoachServiceHistory.swift)

    /**
     * Restores the last messages from the account when the conversation on the phone is empty.
     * Proposals come back with the answer that was given. Silent on failure.
     */
    suspend fun restoreHistory(store: AppStore) {
        val account = store.current.account?.takeIf { it.isReal } ?: return
        if (store.current.messages.isNotEmpty()) return
        val userId = account.userId
        val query = listOf(
            "select" to "id,role,body,proposal,proposal_status,created_at",
            "user_id" to "eq.$userId",
            "order" to "created_at.desc,seq.desc",
            "limit" to "60",
        )
        val rows = try {
            SyncRemote.rows("coach_messages", query, store)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        // The user may have written, or signed out, while the request was running.
        if (store.current.messages.isNotEmpty() || store.current.account?.userId != userId) return

        val hint = Regex("""\n\n\(Answer in [A-Za-z ]+\.\)$""")
        val restored = rows.reversed().mapNotNull { row ->
            val body = row.string("body")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val roleText = row.string("role") ?: return@mapNotNull null
            val role = if (roleText == "user") ChatRole.User else ChatRole.Coach
            // The request carries a hidden "(Answer in French.)" line after the user's words: not theirs.
            val text = if (role == ChatRole.User) body.replace(hint, "") else body
            val id = row.string("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
            val date = row.string("created_at")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: Instant.now()
            val proposal = if (role == ChatRole.Coach) {
                (row["proposal"] as? JsonObject)?.let { proposal(it, store.current, keepUnknown = true) }?.copy(
                    status = when (row.string("proposal_status")) {
                        "applied" -> ProposalStatus.Applied
                        "declined" -> ProposalStatus.Declined
                        else -> ProposalStatus.Pending
                    },
                )
            } else null
            ChatMessage(id = id, role = role, text = text, date = date, proposal = proposal)
        }
        if (restored.isEmpty()) return
        store.update { if (it.messages.isEmpty()) it.copy(messages = restored) else it }
    }

    /** [reportProposal] in the background, so it finishes even if the screen goes away. */
    fun report(messageId: UUID, applied: Boolean, store: AppStore) {
        scope.launch { reportProposal(messageId, applied, store) }
    }

    /**
     * Records in the account what the runner answered to a proposal, so it is not offered again on
     * another phone. Does nothing for replies written on the phone (they are not in the account).
     */
    suspend fun reportProposal(messageId: UUID, applied: Boolean, store: AppStore) {
        if (store.current.account?.isReal != true) return
        val values = buildJsonObject { put("proposal_status", if (applied) "applied" else "declined") }
        try {
            SyncRemote.update("coach_messages", listOf("id" to "eq.${messageId.toString().lowercase()}"), values, store)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Not recorded: the proposal may be offered again on another phone.
        }
    }

    /**
     * A request to the account as the signed-in user, through the account's client: the token is
     * refreshed when needed, and once more after a 401. Null when it could not be sent (guest,
     * signed out, offline).
     */
    private suspend fun send(
        store: AppStore,
        path: String,
        method: String,
        body: JsonElement? = null,
        timeoutSeconds: Long = 30,
    ): SupabaseResponse? = try {
        AuthService.authorizedSend(store, path, method, body = body?.toString(), timeoutSeconds = timeoutSeconds)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

}
