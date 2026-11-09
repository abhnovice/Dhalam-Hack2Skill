package com.dhalam.ap

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
private fun motionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Reserve the final text's layout and expose the complete sentence to accessibility services. */
@Composable
internal fun TerminalChatText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start
) {
    val animate = motionEnabled()
    var count by rememberSaveable(value) { mutableIntStateOf(if (animate) 0 else value.length) }
    LaunchedEffect(value, animate) {
        if (!animate) count = value.length
        while (count < value.length) {
            delay(18)
            // Advance by a code point, never split an emoji's surrogate pair.
            count = value.offsetByCodePoints(count, 1)
        }
    }
    Box(modifier.clearAndSetSemantics { text = AnnotatedString(value) }) {
        Text(value, color = Color.Transparent, style = style, textAlign = textAlign)
        Text(value.take(count), color = color, style = style, textAlign = textAlign,
            modifier = Modifier.matchParentSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatWelcome(title: String, subtitle: String, onSuggestion: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val animate = motionEnabled()
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (appeared || !animate) reveal.snapTo(1f)
        else {
            appeared = true
            reveal.animateTo(1f, tween(1100))
        }
    }
    // Stable noise tiles dissolve once, rather than continuously animating or allocating per frame.
    val noise = remember { val random = Random(27); List(24 * 24) { random.nextFloat() } }
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = .3f + .7f * reveal.value
            translationY = (1f - reveal.value) * 18.dp.toPx()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painterResource(R.drawable.dhalam_logo_transparent),
            contentDescription = "Dhalam logo",
            modifier = Modifier.size(132.dp).drawWithContent {
                drawContent()
                val progress = reveal.value
                if (progress < 1f) {
                    val tileWidth = size.width / 24
                    val tileHeight = size.height / 24
                    noise.forEachIndexed { index, threshold ->
                        if (threshold > progress) {
                            val tint = if (index % 5 == 0) colors.primaryContainer else colors.background
                            drawRect(tint, Offset(index % 24 * tileWidth, index / 24 * tileHeight),
                                Size(tileWidth + 1f, tileHeight + 1f))
                        }
                    }
                }
            }
        )
        TerminalChatText(title, style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center)
        TerminalChatText(subtitle, modifier = Modifier.fillMaxWidth(),
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            SuggestionChip(onClick = { onSuggestion("Someone is asking me to send money. ") },
                label = { Text(trText("Someone wants money")) })
            SuggestionChip(onClick = { onSuggestion("Help me check this message. ") },
                label = { Text(trText("Check a message")) })
        }
        Text(trText("No need to decide in a hurry."), color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
