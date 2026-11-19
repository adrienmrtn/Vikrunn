package com.atala.app.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Atala Pro pieces shown by other screens. The monetisation owner builds them; these placeholders
 * keep the signatures stable so the Today, Plan, Coach, Progress and Profile screens can use them now.
 */

/** "PRO" badge for a subscriber (header of Today, profile). Shows nothing for a free user. */
@Composable
fun ProChip(modifier: Modifier = Modifier) {}

/** Small card for free users on Today and Plan: what Atala Pro adds, one button to the paywall. */
@Composable
fun ProUpsellCard(onOpenPaywall: () -> Unit, modifier: Modifier = Modifier) {}

/** Round gift chip with a live countdown at the top of Today; reopens the gift offer. */
@Composable
fun GiftOfferChip(modifier: Modifier = Modifier) {}

/**
 * A screen that needs Atala Pro: [content] blurred under a padlock card with a button to the paywall,
 * or shown as it is for a subscriber.
 */
@Composable
fun ProLocked(
    title: String,
    message: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    content()
}

/** Full-screen gift offer and Pro welcome, presented over the main app when due. */
@Composable
fun MainAppOverlays() {}
