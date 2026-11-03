package com.everettpenne.skulk.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.everettpenne.glass.GlassTabSpec

/** Skulk's top-level destinations. Feed and Saved sit in the glass tab capsule; Search is the round button beside it. */
enum class Tab(override val route: String, override val label: String, override val icon: ImageVector) : GlassTabSpec {
    Feed("feed", "Feed", Icons.Rounded.Article),
    Saved("saved", "Saved", Icons.Rounded.Bookmark),
    Search("search", "Search", Icons.Rounded.Search);

    companion object { val bar = listOf(Feed, Saved) }
}
