package com.atala.app.core.sync

import android.app.Application
import android.os.Looper
import com.atala.app.core.account.SupabaseClient
import com.atala.app.core.model.Account
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.Constraint
import com.atala.app.core.model.Experience
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.Gender
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.RoutePoint
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.RunSplit
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.SyncState
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.VoiceSettings
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppStore
import com.atala.app.testing.TestSupport
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * What the sync sends is what the iPhone sends: the models' JSON has the Swift property names,
 * types and enum raw values (read from Atala/Core/Models.swift), and the rows pushed to `profiles`,
 * `plans` and `runs` have the columns of SyncService.swift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncJsonTest {
    private val swiftModels: String by lazy { File("../../Atala/Core/Models.swift").readText() }
    private val swiftSync: String by lazy { File("../../Atala/Services/SyncService.swift").readText() }

    @Before
    fun setUp() = TestSupport.reset()

    // Swift source

    private class SwiftProperty(val name: String, val type: String?, val optional: Boolean)

    /** The text of a declaration's body at its own level (nested bodies left out, their `{` kept). */
    private fun body(header: Regex): List<String> {
        val match = header.find(swiftModels) ?: error("not found: $header")
        var depth = 1
        var index = match.range.last + 1
        val text = StringBuilder()
        while (depth > 0) {
            val c = swiftModels[index++]
            if (c == '{') depth++
            if (c == '}') depth--
            if (depth == 1 || (depth == 2 && c == '{')) text.append(c)
        }
        return text.lines()
    }

    /** Stored properties of a Swift struct (computed ones and functions left out). */
    private fun swiftStruct(name: String): Map<String, SwiftProperty> {
        val property = Regex("""^\s*(?:private\(set\)\s+)?(?:var|let)\s+(\w+)\s*(?::\s*([^={]+?))?\s*(?:=\s*(.+))?$""")
        return body(Regex("""struct $name\b[^{]*\{""")).mapNotNull { line ->
            if ('{' in line || line.trim().startsWith("static") || line.trim().startsWith("//")) return@mapNotNull null
            val m = property.find(line) ?: return@mapNotNull null
            val default = m.groupValues[3].trim()
            // A type inferred from the default value: `var id = UUID()`, `var enabled = true`.
            val type = m.groupValues[2].trim().ifEmpty {
                when {
                    default == "true" || default == "false" -> "Bool"
                    Regex("""^\w+\(\)$""").matches(default) -> default.removeSuffix("()")
                    else -> ""
                }
            }.ifEmpty { null }
            SwiftProperty(m.groupValues[1], type, type?.endsWith("?") == true)
        }.associateBy { it.name }
    }

    /** Raw values of a Swift `enum X: String, Codable` (case names). */
    private fun swiftEnum(name: String): List<String> =
        body(Regex("""enum $name: String, Codable[^{]*\{"""))
            .filter { it.trim().startsWith("case ") }
            .flatMap { it.trim().removePrefix("case ").split(",").map { case -> case.trim() } }

    // JSON checks

    private val structs = listOf(
        "OnboardingAnswers", "RecentResult", "TrainingPlan", "PlanWeek", "Paces", "Workout", "WorkoutBlock", "WorkoutStep",
        "RunRecord", "RoutePoint", "RunSplit", "AppSettings", "VoiceSettings",
    )
    private val enums = listOf(
        "Gender", "GoalType", "Experience", "WeeklyVolume", "Motivation", "Constraint", "CoachTone", "WorkoutKind",
        "StepKind", "WorkoutStatus", "PlanPhase", "RunSource", "Feeling",
    )
    private val isoSecond = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""")
    private val upperUuid = Regex("""^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$""")

    /** Checks [json] against the Swift struct [name]: names, required keys, value types, nested structs. */
    private fun check(name: String, json: JsonElement, path: String, problems: MutableList<String>) {
        val obj = json as? JsonObject ?: return run { problems += "$path: not an object" }
        val swift = swiftStruct(name)
        for (key in obj.keys) if (key !in swift) problems += "$path.$key: not a property of $name in Swift"
        for (property in swift.values) {
            val value = obj[property.name]
            if (value == null || value is JsonNull) {
                if (!property.optional) problems += "$path.${property.name}: required by Swift, missing"
                continue
            }
            checkValue(property.type?.removeSuffix("?"), value, "$path.${property.name}", problems)
        }
    }

    private fun checkValue(type: String?, value: JsonElement, path: String, problems: MutableList<String>) {
        when {
            type == null -> Unit // inferred from a default value: checked by the round trip
            type.startsWith("[") -> value.jsonArray.forEachIndexed { i, item -> checkValue(type.removeSurrounding("[", "]"), item, "$path[$i]", problems) }
            type == "Int" -> if (value.jsonPrimitive.isString || value.jsonPrimitive.content.contains('.')) problems += "$path: Int expected, got $value"
            type == "Double" -> if (value.jsonPrimitive.isString || value.jsonPrimitive.content.toDoubleOrNull() == null) problems += "$path: Double expected"
            type == "Bool" -> if (value.jsonPrimitive.booleanOrNull == null) problems += "$path: Bool expected"
            type == "String" -> if (!value.jsonPrimitive.isString) problems += "$path: String expected"
            type == "Date" -> if (!isoSecond.matches(value.jsonPrimitive.content)) problems += "$path: ISO 8601 date to the second expected, got $value"
            type == "UUID" -> if (!upperUuid.matches(value.jsonPrimitive.content)) problems += "$path: upper-case UUID expected, got $value"
            type in structs -> check(type, value, path, problems)
            type in enums -> if (value.jsonPrimitive.content !in swiftEnum(type)) problems += "$path: $value is not a case of $type"
            else -> problems += "$path: unknown Swift type $type"
        }
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonElement = SyncCoding.json(serializer, value)

    // Samples with every optional field filled

    private val answers = OnboardingAnswers(
        firstName = "Léa", coachId = 2, gender = Gender.Woman, experience = Experience.SixMonthsToTwoYears,
        weeklyVolume = WeeklyVolume.From10To25, recentResult = RecentResult(10.0, 2950), healthFlags = listOf("break"),
        goal = GoalType.Half, hasRace = true, raceDate = Instant.parse("2027-01-10T08:30:00.250Z"), targetSeconds = 6600,
        motivation = Motivation.Record, runDays = listOf(2, 4, 7), longRunDay = 7,
        constraints = listOf(Constraint.Travel, Constraint.IrregularSleep), tone = CoachTone.Direct,
    )
    private val settings = AppSettings(voice = VoiceSettings(enabled = true, announceSteps = false, announcePace = true, announceEveryKm = false), healthImportEnabled = true)
    private val plan: TrainingPlan by lazy {
        val generated = PlanEngine.generate(answers, start = Instant.parse("2026-11-18T10:15:30.123Z"))
        // A session done, linked to a run and with a feeling.
        val first = generated.allWorkouts.first()
        generated.copy(weeks = generated.weeks.map { week ->
            week.copy(workouts = week.workouts.map { if (it.id == first.id) it.copy(status = WorkoutStatus.Done, runId = UUID.randomUUID(), feeling = Feeling.Hard) else it })
        })
    }
    private val run = RunRecord(
        workoutId = UUID.randomUUID(), title = "Long run", date = Instant.parse("2026-11-19T07:02:03.987Z"), source = RunSource.Gps,
        distanceM = 10234.5, durationSec = 3600, avgHeartRate = 150, maxHeartRate = 171, feeling = Feeling.Good,
        route = listOf(
            RoutePoint(48.85, 2.35, Instant.parse("2026-11-19T07:02:03.500Z"), alt = 35.2),
            RoutePoint(48.851, 2.351, Instant.parse("2026-11-19T07:02:07Z")),
        ),
        splits = listOf(RunSplit(label = "400 m", kind = StepKind.Work, distanceM = 402.3, durationSec = 98, paceFast = 240, paceSlow = 250)),
    )

    @Test
    fun modelsEncodeAsTheSwiftCodableModels() {
        val problems = mutableListOf<String>()
        check("OnboardingAnswers", encode(OnboardingAnswers.serializer(), answers), "answers", problems)
        check("OnboardingAnswers", encode(OnboardingAnswers.serializer(), OnboardingAnswers()), "emptyAnswers", problems)
        check("AppSettings", encode(AppSettings.serializer(), settings), "settings", problems)
        check("TrainingPlan", encode(TrainingPlan.serializer(), plan), "plan", problems)
        check("RunRecord", encode(RunRecord.serializer(), run), "run", problems)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())

        // Same property sets both ways (a Kotlin field Swift does not know would be lost, and the reverse).
        val kotlin = mapOf(
            "OnboardingAnswers" to OnboardingAnswers.serializer().descriptor, "RecentResult" to RecentResult.serializer().descriptor,
            "TrainingPlan" to TrainingPlan.serializer().descriptor, "RunRecord" to RunRecord.serializer().descriptor,
            "RoutePoint" to RoutePoint.serializer().descriptor, "RunSplit" to RunSplit.serializer().descriptor,
            "AppSettings" to AppSettings.serializer().descriptor, "VoiceSettings" to VoiceSettings.serializer().descriptor,
            "Workout" to com.atala.app.core.model.Workout.serializer().descriptor,
            "WorkoutStep" to com.atala.app.core.model.WorkoutStep.serializer().descriptor,
            "WorkoutBlock" to com.atala.app.core.model.WorkoutBlock.serializer().descriptor,
            "PlanWeek" to com.atala.app.core.model.PlanWeek.serializer().descriptor,
            "Paces" to com.atala.app.core.model.Paces.serializer().descriptor,
        )
        for ((name, descriptor) in kotlin) {
            assertEquals(name, swiftStruct(name).keys.sorted(), descriptor.elementNames.toList().sorted())
        }

        // The JSON reads back into the same values (dates to the second).
        fun Instant.whole() = Instant.ofEpochSecond(epochSecond)
        assertEquals(answers.copy(raceDate = answers.raceDate!!.whole()), SyncCoding.decode(OnboardingAnswers.serializer(), encode(OnboardingAnswers.serializer(), answers)))
        assertEquals(settings, SyncCoding.decode(AppSettings.serializer(), encode(AppSettings.serializer(), settings)))
        assertEquals(plan.copy(createdAt = plan.createdAt.whole()), SyncCoding.decode(TrainingPlan.serializer(), encode(TrainingPlan.serializer(), plan)))
        assertEquals(
            run.copy(date = run.date.whole(), route = run.route.map { it.copy(time = it.time.whole()) }),
            SyncCoding.decode(RunRecord.serializer(), encode(RunRecord.serializer(), run)),
        )
        // A plan written by the iPhone (Swift key order, a key Android does not know) reads too.
        val iphone = """{"id":"3F2504E0-4F89-11D3-9A0C-0305E82C3301","goal":"tenK","createdAt":"2026-11-18T10:15:30Z",
            "startDate":"2026-11-16T00:00:00Z","vmaKmh":12.5,"paces":{"easy":440,"long":420,"tempo":340,"interval":300,"race":330},
            "weeks":[{"id":"3F2504E0-4F89-11D3-9A0C-0305E82C3302","index":0,"phase":"base","startDate":"2026-11-16T00:00:00Z","workouts":[]}],
            "futureField":true}"""
        val decoded = AtalaJson.decodeFromString(TrainingPlan.serializer(), iphone)
        assertEquals(GoalType.TenK, decoded.goal)
        assertEquals(PlanPhase.Base, decoded.weeks.single().phase)
    }

    @Test
    fun enumsUseTheSwiftRawValues() {
        val kotlin = mapOf(
            "Gender" to Gender.serializer(), "GoalType" to GoalType.serializer(), "Experience" to Experience.serializer(),
            "WeeklyVolume" to WeeklyVolume.serializer(), "Motivation" to Motivation.serializer(), "Constraint" to Constraint.serializer(),
            "CoachTone" to CoachTone.serializer(), "WorkoutKind" to WorkoutKind.serializer(), "StepKind" to StepKind.serializer(),
            "WorkoutStatus" to WorkoutStatus.serializer(), "PlanPhase" to PlanPhase.serializer(), "RunSource" to RunSource.serializer(),
            "Feeling" to Feeling.serializer(),
        )
        for ((name, serializer) in kotlin) {
            assertEquals(name, swiftEnum(name).sorted(), serializer.descriptor.elementNames.toList().sorted())
        }
    }

    // Rows sent to the account

    private class Sent(val method: String, val path: String, val query: Map<String, String>, val prefer: String?, val body: JsonArray?)

    /** Replaces the HTTP client of SupabaseClient by one that records the requests and answers like PostgREST. */
    private var savedClient: Any? = null

    /** The lazy holding SupabaseClient's OkHttpClient, and its value field. */
    private fun clientSlot(): Pair<Any, java.lang.reflect.Field> {
        val lazy = SupabaseClient::class.java.getDeclaredField("client\$delegate").apply { isAccessible = true }.get(null)!!
        return lazy to lazy.javaClass.getDeclaredField("_value").apply { isAccessible = true }
    }

    private fun interceptRequests(sent: MutableList<Sent>) {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val text = request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }
            val rows = text?.let { AtalaJson.parseToJsonElement(it).jsonArray }
            synchronized(sent) {
                sent += Sent(request.method, request.url.encodedPath, request.url.queryParameterNames.associateWith { request.url.queryParameter(it)!! }, request.header("Prefer"), rows)
            }
            // The server's answer: each row with its id and a time stamp.
            val answer = JsonArray(rows.orEmpty().map { row ->
                JsonObject(buildMap {
                    row.jsonObject["id"]?.let { put("id", JsonPrimitive(it.jsonPrimitive.content.uppercase())) }
                    row.jsonObject["user_id"]?.let { put("user_id", it) }
                    put("updated_at", JsonPrimitive("2026-11-20T10:00:00.123456+00:00"))
                })
            })
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(201).message("Created")
                .body(answer.toString().toResponseBody("application/json".toMediaType())).build()
        }
        val (lazy, value) = clientSlot()
        savedClient = value.get(lazy)
        value.set(lazy, OkHttpClient.Builder().addInterceptor(interceptor).build())
    }

    @After
    fun restoreClient() {
        val saved = savedClient ?: return
        val (lazy, value) = clientSlot()
        value.set(lazy, saved)
    }

    /** The column names of the row literals of SyncService.swift (`let row: [String: Any] = [...]`, `rows.append([...])`). */
    private val swiftRows: List<Set<String>> by lazy {
        Regex("""let row: \[String: Any] = \[|rows\.append\(\[""").findAll(swiftSync).map { start ->
            var depth = 1
            var index = start.range.last + 1
            val literal = StringBuilder()
            while (depth > 0) {
                val c = swiftSync[index++]
                if (c == '[') depth++
                if (c == ']') depth--
                if (depth > 0) literal.append(c)
            }
            Regex(""""([a-z_]+)":""").findAll(literal).map { it.groupValues[1] }.toSet()
        }.toList()
    }

    private fun swiftRowKeys(vararg mustContain: String): Set<String> =
        swiftRows.first { keys -> mustContain.all { it in keys } }

    @Test
    fun pushSendsTheRowsOfTheIphone() {
        val sent = mutableListOf<Sent>()
        interceptRequests(sent)
        val store = AppStore(null, MainScope())
        SyncService.register(store)
        val deleted = UUID.randomUUID().toString().lowercase()
        store.update {
            it.copy(
                answers = answers, settings = settings, plan = plan, runs = listOf(run),
                account = Account(userId = "user-1", email = "lea@example.com", isGuest = false, accessToken = "token",
                    refreshToken = "refresh", expiresAt = Instant.now().plus(Duration.ofHours(1))),
                syncState = SyncState(
                    reconciled = true, profileDirtyAt = Instant.now(), planDirtyAt = Instant.now(),
                    runsDirtyAt = mapOf(SyncCoding.key(run.id) to Instant.now()), deletedRunIds = listOf(deleted),
                ),
            )
        }
        var done = false
        MainScope().launch {
            SyncService.pushAll(store)
            done = true
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (!done && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("push finished", done)

        val byTable = sent.groupBy { it.path.removePrefix("/rest/v1/") }
        assertEquals(setOf("profiles", "plans", "runs"), byTable.keys)
        sent.forEach {
            assertEquals("POST", it.method)
            assertEquals("resolution=merge-duplicates,return=representation", it.prefer)
        }
        val profile = byTable.getValue("profiles").single()
        assertEquals(mapOf("on_conflict" to "user_id", "select" to "user_id,updated_at"), profile.query)
        val profileRow = profile.body!!.single().jsonObject
        assertEquals(swiftRowKeys("first_name"), profileRow.keys)
        assertEquals("user-1", profileRow["user_id"]!!.jsonPrimitive.content)
        assertEquals("Léa", profileRow["first_name"]!!.jsonPrimitive.content)
        assertEquals(2, profileRow["coach_id"]!!.jsonPrimitive.content.toInt())
        val problems = mutableListOf<String>()
        check("OnboardingAnswers", profileRow.getValue("answers"), "profiles.answers", problems)
        check("AppSettings", profileRow.getValue("settings"), "profiles.settings", problems)

        val planRow = byTable.getValue("plans").single().body!!.single().jsonObject
        assertEquals(swiftRowKeys("plan"), planRow.keys)
        check("TrainingPlan", planRow.getValue("plan"), "plans.plan", problems)

        val runRequests = byTable.getValue("runs")
        assertEquals(2, runRequests.size)
        runRequests.forEach { assertEquals(mapOf("on_conflict" to "id", "select" to "id,updated_at"), it.query) }
        val runRow = runRequests[0].body!!.single().jsonObject
        assertEquals(swiftRowKeys("run_date", "run"), runRow.keys)
        assertEquals(run.id.toString().lowercase(), runRow["id"]!!.jsonPrimitive.content)
        assertEquals("2026-11-19T07:02:03Z", runRow["run_date"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, runRow["deleted_at"])
        check("RunRecord", runRow.getValue("run"), "runs.run", problems)
        val tombstone = runRequests[1].body!!.single().jsonObject
        assertEquals(runRow.keys, tombstone.keys)
        assertEquals(deleted, tombstone["id"]!!.jsonPrimitive.content)
        assertEquals(JsonObject(emptyMap()), tombstone["run"])
        assertEquals(JsonNull, tombstone["run_date"])
        assertTrue(isoSecond.matches(tombstone["deleted_at"]!!.jsonPrimitive.content))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())

        // Everything went out: nothing left to send, the server's stamps are kept.
        val state = store.current.syncState
        assertTrue(!state.hasPendingChanges)
        assertEquals("2026-11-20T10:00:00.123456+00:00", state.planStamp)
        assertEquals("2026-11-20T10:00:00.123456+00:00", state.runStamps[SyncCoding.key(run.id)])
    }
}
