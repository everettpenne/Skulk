package com.everettpenne.skulk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.frostedGlass
import com.everettpenne.glass.glassPalette

/**
 * A fire-and-forget message the app root shows as a liquid-glass capsule: used for things like a vote or save Reddit refused, so an
 * action that was applied optimistically and taken back is never undone in silence. Null in previews and tests.
 */
val LocalToast = compositionLocalOf<((String) -> Unit)?> { null }

/** The capsule itself; [ToastOverlay] animates it in and out. */
@Composable
fun GlassToast(message: String, modifier: Modifier = Modifier) {
    val ink = glassPalette().ink
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(message, color = ink, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
            modifier = Modifier.frostedGlass(RoundedCornerShape(percent = 50), lensHeight = 10f, lensAmount = 20f)
                .padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

/** Springs [message] in from the bottom of the screen and away again when it is cleared. */
@Composable
fun ToastOverlay(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(spring(stiffness = 380f)) + slideInVertically(spring(stiffness = 380f)) { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        message?.let { GlassToast(it) }
    }
}
