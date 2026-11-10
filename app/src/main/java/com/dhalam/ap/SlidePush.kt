package com.dhalam.ap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Sliding panels resize their layout so following content is pushed, rather than overlaid. */
@Composable
internal fun SlidePushVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 5 } +
            expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
        exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 5 } +
            shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
    ) { content() }
}
