package com.everettpenne.skulk.util

/**
 * Reddit's markdown, reduced to what a phone needs to read it: paragraphs, headings, quotes, bullet and numbered lists, code blocks,
 * rules and the inline marks (bold, italic, strike-through, code, links, bare links). Pure data (no Compose types), so it is unit-tested;
 * the UI turns [Span]s into styled text.
 */
object Markdown {
    data class Span(
        val text: String,
        val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false, val code: Boolean = false,
        val link: String? = null,
    )

    sealed interface Block {
        data class Paragraph(val spans: List<Span>) : Block
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class Quote(val spans: List<Span>) : Block
        data class ListItem(val marker: String, val spans: List<Span>, val indent: Int) : Block
        data class CodeBlock(val code: String) : Block
        data object Rule : Block
    }

    private val inlinePattern = Regex(
        """`([^`\n]+)`""" +                                   // 1 code
        """|\[([^\]\n]+)]\(([^)\s]+)\)""" +                   // 2,3 link
        """|(https?://[^\s<>()\[\]]+)""" +                     // 4 bare link
        """|\*\*([^\n]+?)\*\*(?!\*)|__([^\n]+?)__(?!_)""" +                       // 5,6 bold
        """|~~([^\n]+?)~~""" +                                     // 7 strike
        """|\*(?![\s*])([^\n]+?)(?<![\s*])\*""" +                  // 8 italic
        """|(?<![A-Za-z0-9])_(?![\s_])([^\n]+?)(?<![\s_])_(?![A-Za-z0-9])""",   // 9 italic
        RegexOption.DOT_MATCHES_ALL)

    fun inline(text: String): List<Span> = parseInline(text, Span(""))

    /** Beyond this much text in one go, marks are left as typed: a pathological comment must not be able to stall the screen. */
    private const val MAX_INLINE = 20_000

    private fun parseInline(text: String, base: Span): List<Span> {
        if (text.length > MAX_INLINE) return listOf(base.copy(text = text))
        val out = ArrayList<Span>()
        fun plain(s: String) { if (s.isNotEmpty()) out += base.copy(text = s) }
        var last = 0
        for (m in inlinePattern.findAll(text)) {
            plain(text.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> out += base.copy(text = g[1], code = true)
                g[2].isNotEmpty() -> out += parseInline(g[2], base.copy(link = g[3]))
                g[4].isNotEmpty() -> {
                    // Trailing punctuation belongs to the sentence, not the link.
                    val url = g[4].trimEnd('.', ',', ';', ':', '!', '?')
                    out += base.copy(text = url, link = url); plain(g[4].substring(url.length))
                }
                g[5].isNotEmpty() -> out += parseInline(g[5], base.copy(bold = true))
                g[6].isNotEmpty() -> out += parseInline(g[6], base.copy(bold = true))
                g[7].isNotEmpty() -> out += parseInline(g[7], base.copy(strike = true))
                g[8].isNotEmpty() -> out += parseInline(g[8], base.copy(italic = true))
                g[9].isNotEmpty() -> out += parseInline(g[9], base.copy(italic = true))
            }
            last = m.range.last + 1
        }
        plain(text.substring(last))
        return out
    }

    private val heading = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val numbered = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    fun blocks(source: String): List<Block> {
        val blocks = ArrayList<Block>()
        val paragraph = ArrayList<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) { blocks += Block.Paragraph(inline(paragraph.joinToString("\n"))); paragraph.clear() }
        }
        val lines = source.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trimStart().startsWith("```") -> {
                    flush()
                    val code = ArrayList<String>(); i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code += lines[i]; i++ }
                    blocks += Block.CodeBlock(code.joinToString("\n"))
                }
                line.isBlank() -> flush()
                rule.matches(line) -> { flush(); blocks += Block.Rule }
                heading.matches(line) -> { flush(); val m = heading.matchEntire(line)!!; blocks += Block.Heading(m.groupValues[1].length, inline(m.groupValues[2])) }
                line.startsWith(">") -> { flush(); blocks += Block.Quote(inline(line.removePrefix(">").removePrefix(" "))) }
                bullet.matches(line) -> { flush(); val m = bullet.matchEntire(line)!!; blocks += Block.ListItem("•", inline(m.groupValues[2]), m.groupValues[1].length / 2) }
                numbered.matches(line) -> { flush(); val m = numbered.matchEntire(line)!!; blocks += Block.ListItem("${m.groupValues[2]}.", inline(m.groupValues[3]), m.groupValues[1].length / 2) }
                line.startsWith("    ") && paragraph.isEmpty() -> {
                    val code = ArrayList<String>()
                    while (i < lines.size && (lines[i].startsWith("    ") || lines[i].isBlank())) { code += lines[i].removePrefix("    "); i++ }
                    i--; blocks += Block.CodeBlock(code.joinToString("\n").trimEnd())
                }
                else -> paragraph += line
            }
            i++
        }
        flush()
        return blocks
    }
}
