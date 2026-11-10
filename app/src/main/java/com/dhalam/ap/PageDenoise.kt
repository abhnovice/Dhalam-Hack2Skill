package com.dhalam.ap

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random

/** A finite canvas reveal over the whole page; switches and fresh scans do not restart it. */
@Composable
internal fun Modifier.pageDenoiseReveal(active: Boolean, pageKey: Any = active): Modifier {
    val context = LocalContext.current
    val motion = remember(context, active) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(false)
    }
    val reveal = remember(active, motion, pageKey) { Animatable(if (active && motion) 0f else 1f) }
    val tiles = remember { val random = Random(83); FloatArray(36 * 64) { random.nextFloat() } }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(active, motion, pageKey) {
        if (active && motion) reveal.animateTo(1f, tween(950))
    }
    return this.drawWithContent {
        drawContent()
        val progress = reveal.value
        if (progress < 1f) {
            val width = size.width / 36
            val height = size.height / 64
            tiles.forEachIndexed { index, threshold ->
                if (threshold > progress) {
                    val tint = when (index % 13) {
                        0 -> colors.primaryContainer
                        1 -> colors.surfaceVariant
                        else -> colors.background
                    }
                    drawRect(tint.copy(alpha = (1f - progress * .7f)),
                        Offset(index % 36 * width, index / 36 * height),
                        Size(width + 1f, height + 1f))
                }
            }
        }
    }
}
