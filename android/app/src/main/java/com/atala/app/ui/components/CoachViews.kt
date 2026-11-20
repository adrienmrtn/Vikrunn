package com.atala.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme

@Composable
fun CoachAvatar(coach: Coach, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Image(
        painterResource(AtalaImages.coachAvatar(coach)),
        contentDescription = coach.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}

/** Full-body coach, as a still picture in the given pose (`CoachPoseView`). */
@Composable
fun CoachPoseImage(coach: Coach, modifier: Modifier = Modifier, pose: CoachPose = CoachPose.Wave) {
    Image(
        painterResource(AtalaImages.coachPose(coach, pose)),
        contentDescription = coach.name,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Grey note with the coach avatar, used for the coach's short comments. */
@Composable
fun CoachNote(coach: Coach, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Theme.surface, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoachAvatar(coach, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(coach.name, style = Theme.font(13, FontWeight.SemiBold), color = Theme.green)
            Text(text, style = Theme.font(15).copy(lineHeight = 21.sp), color = Theme.ink)
        }
    }
}
