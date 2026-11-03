package com.everettpenne.skulk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.GlassSkeleton

/**
 * Cards the shape of posts while the first page loads: a title in two lines, a picture, and the row of numbers under it, each in the soft shimmering
 * grey the suite uses for loading. It replaces a lone spinner, so the page already looks like a feed when the posts arrive.
 */
@Composable
fun PostSkeleton(count: Int = 3) {
    val card = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSkeleton(count = 1, rowHeight = 44.dp)
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)))
                GlassSkeleton(count = 1, rowHeight = 32.dp)
            }
        }
    }
}
