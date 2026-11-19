package com.atala.app.ui.theme

import androidx.annotation.DrawableRes
import com.atala.app.R
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose

/**
 * The pictures of the iPhone's asset catalog, by their catalog name, so ported screens use the same
 * names as the Swift code (`AtalaImages["illu_today"]`). The files are in res/drawable-nodpi, except the
 * two vector logos (logo_atala, logo_atala_mark), converted from their SVG into res/drawable.
 */
object AtalaImages {
    private val byName: Map<String, Int> = mapOf(
        // Coaches: avatars, full-body pictures and poses.
        "coach1_avatar" to R.drawable.coach1_avatar, "coach1_full" to R.drawable.coach1_full,
        "coach1_pose_cheer" to R.drawable.coach1_pose_cheer, "coach1_pose_crossed" to R.drawable.coach1_pose_crossed,
        "coach1_pose_present" to R.drawable.coach1_pose_present, "coach1_pose_run" to R.drawable.coach1_pose_run,
        "coach1_pose_thumbsup" to R.drawable.coach1_pose_thumbsup, "coach2_avatar" to R.drawable.coach2_avatar,
        "coach2_full" to R.drawable.coach2_full, "coach2_pose_cheer" to R.drawable.coach2_pose_cheer,
        "coach2_pose_crossed" to R.drawable.coach2_pose_crossed, "coach2_pose_present" to R.drawable.coach2_pose_present,
        "coach2_pose_run" to R.drawable.coach2_pose_run, "coach2_pose_thumbsup" to R.drawable.coach2_pose_thumbsup,
        "coach3_avatar" to R.drawable.coach3_avatar, "coach3_full" to R.drawable.coach3_full,
        "coach3_pose_cheer" to R.drawable.coach3_pose_cheer, "coach3_pose_crossed" to R.drawable.coach3_pose_crossed,
        "coach3_pose_present" to R.drawable.coach3_pose_present, "coach3_pose_run" to R.drawable.coach3_pose_run,
        "coach3_pose_thumbsup" to R.drawable.coach3_pose_thumbsup, "coach4_avatar" to R.drawable.coach4_avatar,
        "coach4_full" to R.drawable.coach4_full, "coach4_pose_cheer" to R.drawable.coach4_pose_cheer,
        "coach4_pose_crossed" to R.drawable.coach4_pose_crossed, "coach4_pose_present" to R.drawable.coach4_pose_present,
        "coach4_pose_run" to R.drawable.coach4_pose_run, "coach4_pose_thumbsup" to R.drawable.coach4_pose_thumbsup,
        "coach5_avatar" to R.drawable.coach5_avatar, "coach5_full" to R.drawable.coach5_full,
        "coach5_pose_cheer" to R.drawable.coach5_pose_cheer, "coach5_pose_crossed" to R.drawable.coach5_pose_crossed,
        "coach5_pose_present" to R.drawable.coach5_pose_present, "coach5_pose_run" to R.drawable.coach5_pose_run,
        "coach5_pose_thumbsup" to R.drawable.coach5_pose_thumbsup, "coach6_avatar" to R.drawable.coach6_avatar,
        "coach6_full" to R.drawable.coach6_full, "coach6_pose_cheer" to R.drawable.coach6_pose_cheer,
        "coach6_pose_crossed" to R.drawable.coach6_pose_crossed, "coach6_pose_present" to R.drawable.coach6_pose_present,
        "coach6_pose_run" to R.drawable.coach6_pose_run, "coach6_pose_thumbsup" to R.drawable.coach6_pose_thumbsup,
        // Guided exercises, coach 1 (plain names) and coaches 2 to 6 ("exercise_c<N>_...").
        "exercise_c2_calves_1" to R.drawable.exercise_c2_calves_1, "exercise_c2_calves_2" to R.drawable.exercise_c2_calves_2,
        "exercise_c2_glute_bridge_1" to R.drawable.exercise_c2_glute_bridge_1, "exercise_c2_glute_bridge_2" to R.drawable.exercise_c2_glute_bridge_2,
        "exercise_c2_glutes_1" to R.drawable.exercise_c2_glutes_1, "exercise_c2_glutes_2" to R.drawable.exercise_c2_glutes_2,
        "exercise_c2_hamstrings_1" to R.drawable.exercise_c2_hamstrings_1, "exercise_c2_hamstrings_2" to R.drawable.exercise_c2_hamstrings_2,
        "exercise_c2_hip_flexors_1" to R.drawable.exercise_c2_hip_flexors_1, "exercise_c2_hip_flexors_2" to R.drawable.exercise_c2_hip_flexors_2,
        "exercise_c2_lunge_1" to R.drawable.exercise_c2_lunge_1, "exercise_c2_lunge_2" to R.drawable.exercise_c2_lunge_2,
        "exercise_c2_plank_1" to R.drawable.exercise_c2_plank_1, "exercise_c2_plank_2" to R.drawable.exercise_c2_plank_2,
        "exercise_c2_quads_1" to R.drawable.exercise_c2_quads_1, "exercise_c2_quads_2" to R.drawable.exercise_c2_quads_2,
        "exercise_c2_squat_1" to R.drawable.exercise_c2_squat_1, "exercise_c2_squat_2" to R.drawable.exercise_c2_squat_2,
        "exercise_c3_calves_1" to R.drawable.exercise_c3_calves_1, "exercise_c3_calves_2" to R.drawable.exercise_c3_calves_2,
        "exercise_c3_glute_bridge_1" to R.drawable.exercise_c3_glute_bridge_1, "exercise_c3_glute_bridge_2" to R.drawable.exercise_c3_glute_bridge_2,
        "exercise_c3_glutes_1" to R.drawable.exercise_c3_glutes_1, "exercise_c3_glutes_2" to R.drawable.exercise_c3_glutes_2,
        "exercise_c3_hamstrings_1" to R.drawable.exercise_c3_hamstrings_1, "exercise_c3_hamstrings_2" to R.drawable.exercise_c3_hamstrings_2,
        "exercise_c3_hip_flexors_1" to R.drawable.exercise_c3_hip_flexors_1, "exercise_c3_hip_flexors_2" to R.drawable.exercise_c3_hip_flexors_2,
        "exercise_c3_lunge_1" to R.drawable.exercise_c3_lunge_1, "exercise_c3_lunge_2" to R.drawable.exercise_c3_lunge_2,
        "exercise_c3_plank_1" to R.drawable.exercise_c3_plank_1, "exercise_c3_plank_2" to R.drawable.exercise_c3_plank_2,
        "exercise_c3_quads_1" to R.drawable.exercise_c3_quads_1, "exercise_c3_quads_2" to R.drawable.exercise_c3_quads_2,
        "exercise_c3_squat_1" to R.drawable.exercise_c3_squat_1, "exercise_c3_squat_2" to R.drawable.exercise_c3_squat_2,
        "exercise_c4_calves_1" to R.drawable.exercise_c4_calves_1, "exercise_c4_calves_2" to R.drawable.exercise_c4_calves_2,
        "exercise_c4_glute_bridge_1" to R.drawable.exercise_c4_glute_bridge_1, "exercise_c4_glute_bridge_2" to R.drawable.exercise_c4_glute_bridge_2,
        "exercise_c4_glutes_1" to R.drawable.exercise_c4_glutes_1, "exercise_c4_glutes_2" to R.drawable.exercise_c4_glutes_2,
        "exercise_c4_hamstrings_1" to R.drawable.exercise_c4_hamstrings_1, "exercise_c4_hamstrings_2" to R.drawable.exercise_c4_hamstrings_2,
        "exercise_c4_hip_flexors_1" to R.drawable.exercise_c4_hip_flexors_1, "exercise_c4_hip_flexors_2" to R.drawable.exercise_c4_hip_flexors_2,
        "exercise_c4_lunge_1" to R.drawable.exercise_c4_lunge_1, "exercise_c4_lunge_2" to R.drawable.exercise_c4_lunge_2,
        "exercise_c4_plank_1" to R.drawable.exercise_c4_plank_1, "exercise_c4_plank_2" to R.drawable.exercise_c4_plank_2,
        "exercise_c4_quads_1" to R.drawable.exercise_c4_quads_1, "exercise_c4_quads_2" to R.drawable.exercise_c4_quads_2,
        "exercise_c4_squat_1" to R.drawable.exercise_c4_squat_1, "exercise_c4_squat_2" to R.drawable.exercise_c4_squat_2,
        "exercise_c5_calves_1" to R.drawable.exercise_c5_calves_1, "exercise_c5_calves_2" to R.drawable.exercise_c5_calves_2,
        "exercise_c5_glute_bridge_1" to R.drawable.exercise_c5_glute_bridge_1, "exercise_c5_glute_bridge_2" to R.drawable.exercise_c5_glute_bridge_2,
        "exercise_c5_glutes_1" to R.drawable.exercise_c5_glutes_1, "exercise_c5_glutes_2" to R.drawable.exercise_c5_glutes_2,
        "exercise_c5_hamstrings_1" to R.drawable.exercise_c5_hamstrings_1, "exercise_c5_hamstrings_2" to R.drawable.exercise_c5_hamstrings_2,
        "exercise_c5_hip_flexors_1" to R.drawable.exercise_c5_hip_flexors_1, "exercise_c5_hip_flexors_2" to R.drawable.exercise_c5_hip_flexors_2,
        "exercise_c5_lunge_1" to R.drawable.exercise_c5_lunge_1, "exercise_c5_lunge_2" to R.drawable.exercise_c5_lunge_2,
        "exercise_c5_plank_1" to R.drawable.exercise_c5_plank_1, "exercise_c5_plank_2" to R.drawable.exercise_c5_plank_2,
        "exercise_c5_quads_1" to R.drawable.exercise_c5_quads_1, "exercise_c5_quads_2" to R.drawable.exercise_c5_quads_2,
        "exercise_c5_squat_1" to R.drawable.exercise_c5_squat_1, "exercise_c5_squat_2" to R.drawable.exercise_c5_squat_2,
        "exercise_c6_calves_1" to R.drawable.exercise_c6_calves_1, "exercise_c6_calves_2" to R.drawable.exercise_c6_calves_2,
        "exercise_c6_glute_bridge_1" to R.drawable.exercise_c6_glute_bridge_1, "exercise_c6_glute_bridge_2" to R.drawable.exercise_c6_glute_bridge_2,
        "exercise_c6_glutes_1" to R.drawable.exercise_c6_glutes_1, "exercise_c6_glutes_2" to R.drawable.exercise_c6_glutes_2,
        "exercise_c6_hamstrings_1" to R.drawable.exercise_c6_hamstrings_1, "exercise_c6_hamstrings_2" to R.drawable.exercise_c6_hamstrings_2,
        "exercise_c6_hip_flexors_1" to R.drawable.exercise_c6_hip_flexors_1, "exercise_c6_hip_flexors_2" to R.drawable.exercise_c6_hip_flexors_2,
        "exercise_c6_lunge_1" to R.drawable.exercise_c6_lunge_1, "exercise_c6_lunge_2" to R.drawable.exercise_c6_lunge_2,
        "exercise_c6_plank_1" to R.drawable.exercise_c6_plank_1, "exercise_c6_plank_2" to R.drawable.exercise_c6_plank_2,
        "exercise_c6_quads_1" to R.drawable.exercise_c6_quads_1, "exercise_c6_quads_2" to R.drawable.exercise_c6_quads_2,
        "exercise_c6_squat_1" to R.drawable.exercise_c6_squat_1, "exercise_c6_squat_2" to R.drawable.exercise_c6_squat_2,
        "exercise_calves_1" to R.drawable.exercise_calves_1, "exercise_calves_2" to R.drawable.exercise_calves_2,
        "exercise_glute_bridge_1" to R.drawable.exercise_glute_bridge_1, "exercise_glute_bridge_2" to R.drawable.exercise_glute_bridge_2,
        "exercise_glutes_1" to R.drawable.exercise_glutes_1, "exercise_glutes_2" to R.drawable.exercise_glutes_2,
        "exercise_hamstrings_1" to R.drawable.exercise_hamstrings_1, "exercise_hamstrings_2" to R.drawable.exercise_hamstrings_2,
        "exercise_hip_flexors_1" to R.drawable.exercise_hip_flexors_1, "exercise_hip_flexors_2" to R.drawable.exercise_hip_flexors_2,
        "exercise_lunge_1" to R.drawable.exercise_lunge_1, "exercise_lunge_2" to R.drawable.exercise_lunge_2,
        "exercise_plank_1" to R.drawable.exercise_plank_1, "exercise_plank_2" to R.drawable.exercise_plank_2,
        "exercise_quads_1" to R.drawable.exercise_quads_1, "exercise_quads_2" to R.drawable.exercise_quads_2,
        "exercise_squat_1" to R.drawable.exercise_squat_1, "exercise_squat_2" to R.drawable.exercise_squat_2,
        // Photos, illustrations, vignettes and the launch logo.
        "bg_hello" to R.drawable.bg_hello, "hero_today_man" to R.drawable.hero_today_man,
        "hero_today_woman" to R.drawable.hero_today_woman, "illu_coach" to R.drawable.illu_coach,
        "illu_plan" to R.drawable.illu_plan, "illu_progress" to R.drawable.illu_progress,
        "illu_session" to R.drawable.illu_session, "illu_strength" to R.drawable.illu_strength,
        "illu_stretching" to R.drawable.illu_stretching, "illu_today" to R.drawable.illu_today,
        "launch_logo" to R.drawable.launch_logo, "logo_atala" to R.drawable.logo_atala,
        "logo_atala_mark" to R.drawable.logo_atala_mark, "photo_community" to R.drawable.photo_community,
        "photo_session_complete" to R.drawable.photo_session_complete, "photo_trio" to R.drawable.photo_trio,
        "vignette_constraint_injury" to R.drawable.vignette_constraint_injury, "vignette_constraint_sleep" to R.drawable.vignette_constraint_sleep,
        "vignette_constraint_strength" to R.drawable.vignette_constraint_strength, "vignette_constraint_travel" to R.drawable.vignette_constraint_travel,
        "vignette_constraint_treadmill" to R.drawable.vignette_constraint_treadmill, "vignette_exp_1" to R.drawable.vignette_exp_1,
        "vignette_exp_2" to R.drawable.vignette_exp_2, "vignette_exp_3" to R.drawable.vignette_exp_3,
        "vignette_exp_4" to R.drawable.vignette_exp_4, "vignette_goal_10k" to R.drawable.vignette_goal_10k,
        "vignette_goal_5k" to R.drawable.vignette_goal_5k, "vignette_goal_half" to R.drawable.vignette_goal_half,
        "vignette_goal_marathon" to R.drawable.vignette_goal_marathon, "vignette_goal_regular" to R.drawable.vignette_goal_regular,
        "vignette_goal_trail" to R.drawable.vignette_goal_trail, "vignette_race_date" to R.drawable.vignette_race_date,
        "vignette_race_none" to R.drawable.vignette_race_none, "vignette_volume_1" to R.drawable.vignette_volume_1,
        "vignette_volume_2" to R.drawable.vignette_volume_2, "vignette_volume_3" to R.drawable.vignette_volume_3,
        "vignette_why_athletic" to R.drawable.vignette_why_athletic, "vignette_why_clear_head" to R.drawable.vignette_why_clear_head,
        "vignette_why_health" to R.drawable.vignette_why_health, "vignette_why_prove" to R.drawable.vignette_why_prove,
        "vignette_why_record" to R.drawable.vignette_why_record, "vignette_why_together" to R.drawable.vignette_why_together,
        "welcome_1" to R.drawable.welcome_1, "welcome_2" to R.drawable.welcome_2,
        "welcome_3" to R.drawable.welcome_3, "welcome_photo_1" to R.drawable.welcome_photo_1,
        "welcome_photo_2" to R.drawable.welcome_photo_2, "welcome_photo_3" to R.drawable.welcome_photo_3,
    )

    /** The drawable of a catalog name, null when the catalog has no such picture. */
    @DrawableRes
    operator fun get(name: String): Int? = byName[name]

    /**
     * An exercise picture ("exercise_squat_1") in the chosen coach's version when there is one:
     * coach 1 owns the plain names, the others "exercise_c<N>_squat_1".
     */
    @DrawableRes
    fun exercise(name: String, coachId: Int): Int? {
        if (coachId != 1 && name.startsWith("exercise_")) {
            byName["exercise_c_" + name.removePrefix("exercise_")]?.let { return it }
        }
        return byName[name]
    }

    /** A coach's pose, or the full-body picture when that pose is not in the catalog. */
    @DrawableRes
    fun coachPose(coach: Coach, pose: CoachPose): Int =
        byName[coach.poseAsset(pose)] ?: byName.getValue(coach.fullAsset)

    @DrawableRes
    fun coachAvatar(coach: Coach): Int = byName.getValue(coach.avatarAsset)
}
