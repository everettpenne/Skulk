package com.everettpenne.skulk.ui.screens

import com.everettpenne.updater.collectStatus
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import coil3.SingletonImageLoader
import com.everettpenne.skulk.data.AppIcon
import com.everettpenne.skulk.data.AppIcons
import com.everettpenne.skulk.data.ColorSchemeSetting
import com.everettpenne.skulk.data.Settings
import com.everettpenne.skulk.BuildConfig
import com.everettpenne.skulk.graph
import com.everettpenne.glass.*
import com.everettpenne.skulk.ui.theme.Skulk

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.graph.settings
    val persistFeed by settings.persistFeed.collectAsState()
    val persistSort by settings.persistSort.collectAsState()
    val hideSwitcher by settings.hideFeedSwitcher.collectAsState()
    val autoplay by settings.autoplay.collectAsState()
    val mute by settings.muteOnPlay.collectAsState()
    val blur by settings.blurNsfw.collectAsState()
    val linksInApp by settings.linksInApp.collectAsState()
    val colorCoded by settings.colorCodedComments.collectAsState()
    val commentLimit by settings.commentLimit.collectAsState()
    val colorScheme by settings.colorScheme.collectAsState()
    var cacheBytes by remember { mutableStateOf(SingletonImageLoader.get(context).diskCache?.size ?: 0L) }

    GlassScreen(bars = { GlassCenteredTopBar("Settings", onBack = onBack, modifier = Modifier.align(Alignment.TopCenter)) }) { contentPadding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group("Media Settings") {
                SwitchRow(Icons.Rounded.PlayArrow, "Autoplay media", autoplay, settings::setAutoplay)
                SwitchRow(Icons.AutoMirrored.Rounded.VolumeOff, "Start media muted", mute, settings::setMuteOnPlay)
                SwitchRow(Icons.Rounded.VisibilityOff, "Blur NSFW media", blur, settings::setBlurNsfw)
            }
            Group("Comment Settings") {
                SwitchRow(Icons.Rounded.FormatPaint, "Color coded comments", colorCoded, settings::setColorCodedComments)
                PickerRow(Icons.Rounded.FormatListNumbered, "# of comments to load", commentLimit.toString(), Settings.commentLimitOptions.map { it.toString() }) {
                    settings.setCommentLimit(it.toInt())
                }
            }
            Group("Color Scheme") {
                ColorSchemeSetting.entries.forEach { option ->
                    CheckRow(option.label, option == colorScheme) { settings.setColorScheme(option) }
                }
            }
            Group("Feeds") {
                SwitchRow(Icons.Rounded.Layers, "Remember selected feed", persistFeed, settings::setPersistFeed)
                SwitchRow(Icons.Rounded.SwapVert, "Remember sort order", persistSort, settings::setPersistSort)
                SwitchRow(Icons.Rounded.ExpandCircleDown, "Hide feed switcher", hideSwitcher, settings::setHideFeedSwitcher)
            }
            Group("Links", footer = if (linksInApp) "Links outside Reddit open in Skulk's own browser, over the app. On GrapheneOS it is Vanadium's WebView that draws them." else
                "Links outside Reddit open in " + (if (com.everettpenne.browser.Browsers.hasVanadium(context)) "Vanadium." else "your browser.")) {
                SwitchRow(Icons.Rounded.Language, "Open links in Skulk", linksInApp, settings::setLinksInApp)
            }
            var icon by remember { mutableStateOf(AppIcons.current(context)) }
            Group("App Icon", footer = "Your launcher may take a moment to update, and some show a brief flicker.") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (option in AppIcon.entries) IconChoice(option, selected = option == icon) { AppIcons.select(context, option); icon = option }
                }
            }
            Group("Cache") {
                NavRow(Icons.Rounded.DeleteSweep, "Clear media cache", "%.1f MB".format(cacheBytes / 1_048_576.0)) {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear(); loader.diskCache?.clear(); cacheBytes = loader.diskCache?.size ?: 0L
                }
            }
            Group("About") {
                com.everettpenne.updater.LocalUpdater.current?.let { u -> NavRow(androidx.compose.material.icons.Icons.Rounded.SystemUpdate, "Check for Updates", u.collectStatus()) { u.onSettingsRowClick() } }
                NavRow(Icons.Rounded.Info, "Skulk ${BuildConfig.VERSION_NAME}", "Anonymous Reddit reading, with iOS-style liquid glass") {}
                NavRow(Icons.Rounded.Code, "Open Source", "MIT licensed. UI ported from SwiftDdit (Lurker) by Zabir Raihan") {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SilverMarcs/SwiftDdit")))
                }
            }
            Text("Skulk reads Reddit's public pages without an account and without the official API. Reddit can refuse or change them at any time; when it does, Skulk says so.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun Group(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(26.dp)), content = content)
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun RowShell(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Skulk.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) =
    RowShell(icon, title, subtitle, onClick) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowShell(icon, title, null, { onChange(!checked) }) { LiquidSwitch(checked, onChange) }

/** A row that opens a glass menu of [options], showing the current choice, as Lurkur's "# of comments to load" does. */
@Composable
private fun PickerRow(icon: ImageVector, title: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RowShell(icon, title, null, { open = true }) {
            Text(current, style = MaterialTheme.typography.bodyLarge, color = Skulk.accent)
        }
        GlassDropdownMenu(open, { open = false }) {
            options.forEach { o -> GlassMenuItem(o, { open = false; onPick(o) }, selected = o == current) }
        }
    }
}

/** A color-scheme choice row with a tick on the current one, as Lurkur's list does. */
@Composable
private fun CheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = Skulk.accent)
    }
}

@Composable
private fun IconChoice(option: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AndroidView(
            factory = { ctx -> android.widget.ImageView(ctx).apply { setImageDrawable(androidx.core.content.ContextCompat.getDrawable(ctx, option.mipmap)) } },
            modifier = Modifier.size(60.dp).then(if (selected) Modifier.border(2.5.dp, Skulk.accent, RoundedCornerShape(18.dp)).padding(3.dp) else Modifier.padding(3.dp)).clip(RoundedCornerShape(15.dp)),
        )
        Text(option.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = if (selected) Skulk.accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
