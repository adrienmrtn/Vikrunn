@file:UseSerializers(InstantSerializer::class, UuidSerializer::class)

package com.atala.app.core.run

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.InstantSerializer
import com.atala.app.core.model.RoutePoint
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.RunSplit
import com.atala.app.core.model.UuidSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The run being recorded, as last written to `active_run.json`. A run lives in memory only: if
 * the process ends during it, this copy lets the next launch offer to save what was recorded.
 */
@Serializable
data class RunBackup(
    val startDate: Instant,
    /** Seconds of running at the time of the copy, pauses excluded. */
    val activeSeconds: Double,
    /** Metres. */
    val distance: Double,
    val route: List<RoutePoint> = emptyList(),
    val splits: List<RunSplit> = emptyList(),
    val stepIndex: Int = 0,
    val workoutId: UUID? = null,
    val title: String,
    val isPaused: Boolean = false,
    val savedAt: Instant = Instant.now(),
) {
    /** Long enough to be worth keeping (the run flow's "very short" limits). */
    val isWorthSaving: Boolean get() = activeSeconds >= 60 || distance >= 100

    /** The record saved when the runner keeps the interrupted run. */
    fun makeRecord(): RunRecord = RunRecord(
        workoutId = workoutId,
        title = title,
        date = startDate,
        source = RunSource.Gps,
        distanceM = distance,
        durationSec = activeSeconds.roundToInt(),
        avgHeartRate = null,
        route = route,
        splits = splits,
        feeling = null,
    )
}

/** Reads and writes `filesDir/active_run.json`. Writes and deletes run in order, off the main thread. */
object RunBackupStore {
    private const val FILE_NAME = "active_run.json"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** True once the launch has looked for an interrupted run (asked once per process). */
    var offered = false

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    fun write(context: Context, backup: RunBackup) {
        val file = file(context)
        io.launch {
            runCatching { file.writeText(AtalaJson.encodeToString(RunBackup.serializer(), backup)) }
        }
    }

    fun delete(context: Context) {
        val file = file(context)
        io.launch { runCatching { file.delete() } }
    }

    /** The copy left by a run that never ended, null when there is none (or it cannot be read). */
    fun read(context: Context): RunBackup? {
        val file = file(context)
        if (!file.baseFile.exists()) return null
        return runCatching { AtalaJson.decodeFromString(RunBackup.serializer(), file.readText()) }.getOrNull()
    }
}
