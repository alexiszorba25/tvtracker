package com.alexis.tvtracker.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun ChromeVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = chromeEnterTransition(),
        exit = chromeExitTransition(),
        content = { content() },
    )
}

private fun chromeEnterTransition(): EnterTransition {
    return slideInVertically(
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        initialOffsetY = { -it / 2 },
    ) + expandVertically(
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top,
    ) + fadeIn(
        animationSpec = tween(durationMillis = 160, delayMillis = 40),
    )
}

private fun chromeExitTransition(): ExitTransition {
    return slideOutVertically(
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        targetOffsetY = { -it / 2 },
    ) + shrinkVertically(
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top,
    ) + fadeOut(
        animationSpec = tween(durationMillis = 120),
    )
}
