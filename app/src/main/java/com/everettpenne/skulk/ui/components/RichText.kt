package com.everettpenne.skulk.ui.components

import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.everettpenne.skulk.ui.theme.Skulk
import com.everettpenne.skulk.util.Markdown

private val linkStyles = TextLinkStyles(SpanStyle(color = Skulk.accent, textDecoration = TextDecoration.Underline))

/** Inline spans as styled text; links become real links (opened through the app's URI handler, which routes Reddit links inside the app). */
fun List<Markdown.Span>.toAnnotated(codeBackground: Color): AnnotatedString = buildAnnotatedString {
    for (s in this@toAnnotated) {
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null, fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (s.strike) TextDecoration.LineThrough else null,
            fontFamily = if (s.code) FontFamily.Monospace else null, background = if (s.code) codeBackground else Color.Unspecified)
        if (s.link != null) withLink(LinkAnnotation.Url(s.link, linkStyles)) { withStyle(style) { append(s.text) } } else withStyle(style) { append(s.text) }
    }
}

/** Reddit markdown as text. [compact] is the short plain preview in a feed; otherwise the full text with headings, quotes, lists and code. */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier, compact: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface, selectable: Boolean = false) {
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
    if (compact) {
        val text = remember(source, codeBg) { Markdown.inline(source.trim()).toAnnotated(codeBg) }
        Text(text, modifier, color = color.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        return
    }
    val blocks = remember(source) { Markdown.blocks(source) }
    val body: @Composable () -> Unit = {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in blocks) when (b) {
                is Markdown.Block.Paragraph -> Text(b.spans.toAnnotated(codeBg), color = color, style = MaterialTheme.typography.bodyLarge)
                is Markdown.Block.Heading -> Text(b.spans.toAnnotated(codeBg), color = color,
                    style = when (b.level) { 1 -> com.everettpenne.glass.IosType.title2; 2 -> com.everettpenne.glass.IosType.title3; else -> com.everettpenne.glass.IosType.headline }
                        .copy(fontWeight = FontWeight.Bold))
                is Markdown.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Skulk.accent.copy(alpha = .6f), RoundedCornerShape(2.dp)))
                    Text(b.spans.toAnnotated(codeBg), Modifier.padding(start = 10.dp), color = color.copy(alpha = .75f), style = MaterialTheme.typography.bodyLarge)
                }
                is Markdown.Block.ListItem -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(b.marker, Modifier.width(22.dp), color = color.copy(alpha = .7f), style = MaterialTheme.typography.bodyLarge)
                    Text(b.spans.toAnnotated(codeBg), color = color, style = MaterialTheme.typography.bodyLarge)
                }
                is Markdown.Block.CodeBlock -> Text(b.code, Modifier.fillMaxWidth().background(codeBg, RoundedCornerShape(8.dp)).padding(10.dp),
                    style = com.everettpenne.glass.IosType.footnote.copy(fontFamily = FontFamily.Monospace), color = color)
                Markdown.Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = .2f)))
            }
        }
    }
    if (selectable) SelectionContainer { body() } else body()
}
