@file:UseSerializers(InstantSerializer::class, UuidSerializer::class)

package com.atala.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

@Serializable
enum class ChatRole {
    @SerialName("user") User,
    @SerialName("coach") Coach,
}

/** A single change the coach proposes on the plan. Applied only after the user accepts. */
@Serializable
data class PlanChange(
    val id: UUID = UUID.randomUUID(),
    @SerialName("workoutID") val workoutId: UUID,
    val newDate: Instant? = null,
    val newKind: WorkoutKind? = null,
    val newTitle: String? = null,
    val newDistanceKm: Double? = null,
    val newDurationMin: Int? = null,
    val remove: Boolean = false,
    /** Snapshot of the session taken when the change is applied, so the chat can still show what changed. */
    val previousDate: Instant? = null,
    val previousDetail: String? = null,
)

@Serializable
enum class ProposalStatus {
    @SerialName("pending") Pending,
    @SerialName("applied") Applied,
    @SerialName("declined") Declined,
}

@Serializable
data class PlanProposal(
    val id: UUID = UUID.randomUUID(),
    val summary: String,
    val changes: List<PlanChange>,
    val status: ProposalStatus = ProposalStatus.Pending,
)

@Serializable
data class ChatMessage(
    val id: UUID = UUID.randomUUID(),
    val role: ChatRole,
    val text: String,
    val date: Instant = Instant.now(),
    val proposal: PlanProposal? = null,
)
