package com.atala.app.core.model

import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L

data class Coach(val id: Int, val name: String, val trait: String) {
    val avatarAsset: String get() = "coach${id}_avatar"
    val fullAsset: String get() = "coach${id}_full"

    /** Name of the catalog image of a pose: "coachN_full" for `Wave`, "coachN_pose_..." otherwise. */
    fun poseAsset(pose: CoachPose): String = when (pose) {
        CoachPose.Wave -> fullAsset
        CoachPose.ThumbsUp -> "coach${id}_pose_thumbsup"
        CoachPose.Present -> "coach${id}_pose_present"
        CoachPose.Cheer -> "coach${id}_pose_cheer"
        CoachPose.Crossed -> "coach${id}_pose_crossed"
        CoachPose.Run -> "coach${id}_pose_run"
    }

    companion object {
        /** The coaches offered for now. Identifiers are kept as they were, so saved choices still match. */
        val all: List<Coach>
            get() = listOf(
                Coach(3, localName(3, "Mei"), L("Lively and playful")),
                Coach(2, localName(2, "Thomas"), L("Calm and precise")),
                Coach(1, localName(1, "Awa"), L("Sets the pace")),
            )

        /**
         * Coaches set aside for now (their pictures stay in the catalog). Someone who had chosen one
         * of them gets the first available coach. Move a line back into `all` to bring a coach back.
         */
        val retired: List<Coach>
            get() = listOf(
                Coach(4, "Karim", L("Steady and reassuring")),
                Coach(5, "Claire", L("Thirty years of running")),
                Coach(6, "Idriss", L("Direct and motivating")),
            )

        /**
         * First names by language, in the order Awa (1), Thomas (2), Mei (3): each country meets
         * coaches with names from home. English and French keep the original names.
         */
        private val localNames: Map<String, List<String>> = mapOf(
            "es" to listOf("Lucía", "Mateo", "Carmen"), "de" to listOf("Lena", "Jonas", "Mia"),
            "it" to listOf("Giulia", "Luca", "Sofia"), "pt-BR" to listOf("Ana", "Lucas", "Júlia"),
            "pt-PT" to listOf("Inês", "Tiago", "Marta"), "nl" to listOf("Emma", "Daan", "Sanne"),
            "sv" to listOf("Elsa", "Erik", "Maja"), "da" to listOf("Freja", "Mads", "Ida"),
            "no" to listOf("Ingrid", "Lars", "Nora"), "fi" to listOf("Aino", "Mikko", "Emma"),
            "pl" to listOf("Zuzanna", "Jakub", "Maja"), "cs" to listOf("Tereza", "Jakub", "Eliška"),
            "sk" to listOf("Zuzana", "Martin", "Nina"), "hu" to listOf("Anna", "Bence", "Zsófia"),
            "ro" to listOf("Ioana", "Andrei", "Maria"), "hr" to listOf("Ana", "Luka", "Petra"),
            "sl" to listOf("Ana", "Luka", "Nika"), "el" to listOf("Ελένη", "Νίκος", "Μαρία"),
            "tr" to listOf("Elif", "Emre", "Zeynep"), "ru" to listOf("Аня", "Максим", "Маша"),
            "ar" to listOf("ليلى", "كريم", "نور"), "he" to listOf("נועה", "יונתן", "מאיה"),
            "ja" to listOf("さくら", "はると", "ゆい"),
        )

        /** The coach's first name in the app's language, `fallback` where the original is kept. */
        private fun localName(id: Int, fallback: String): String =
            localNames[AppLanguage.current]?.getOrNull(id - 1) ?: fallback

        /** The men, offered or set aside: they share the recorded male voice. */
        val maleIds: Set<Int> = setOf(2, 4, 6)

        /** A coach set aside is replaced by an offered one of the same voice, else by the first. */
        fun byId(id: Int): Coach {
            val offered = all
            offered.firstOrNull { it.id == id }?.let { return it }
            val male = id in maleIds
            return offered.firstOrNull { (it.id in maleIds) == male } ?: offered.first()
        }
    }
}

/** The static poses a coach can take. `Wave` is the original full-body picture. */
enum class CoachPose { Wave, ThumbsUp, Present, Cheer, Crossed, Run }
