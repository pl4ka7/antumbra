package app.antumbra.ui.ask

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.antumbra.ui.theme.MonoMedium
import app.antumbra.ui.theme.AppMono

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val marker: String, val text: String, val indent: Int) : Block
    data class Code(val text: String) : Block
    data class Quote(val text: String) : Block
}

private fun parse(source: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    val lines = source.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                blocks += Block.Code(code.toString().trimEnd())
            }
            trimmed.startsWith("#") -> {
                flush()
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(3)
                blocks += Block.Heading(level, trimmed.drop(level).trim())
            }
            Regex("^([-*+]|\\d+[.)])\\s+").containsMatchIn(trimmed) -> {
                flush()
                val marker = trimmed.substringBefore(' ')
                blocks += Block.Bullet(
                    marker = if (marker.first().isDigit()) marker else "•",
                    text = trimmed.substringAfter(' ').trim(),
                    indent = (line.length - trimmed.length) / 2,
                )
            }
            trimmed.startsWith(">") -> {
                flush()
                blocks += Block.Quote(trimmed.removePrefix(">").trim())
            }
            trimmed.isEmpty() -> flush()
            else -> paragraph.append(trimmed).append(' ')
        }
        i++
    }
    flush()
    return blocks
}

private val inlinePattern = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+]\([^)\s]+\)|(?<![*\w])\*[^*\s][^*]*\*(?!\w)|(?<![_\w])_[^_\s][^_]*_(?!\w))""")

private fun inline(text: String, codeBackground: Color, link: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in inlinePattern.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(token.removeSurrounding("**")) }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = AppMono, background = codeBackground)) { append(token.removeSurrounding("`")) }
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore("](")
                val url = token.substringAfter("](").removeSuffix(")")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = url,
                        styles = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { onLink(url) },
                    )
                ) { append(label) }
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.substring(1, token.length - 1)) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/** Renders the subset of Markdown that chat models use most. Links open in a new private tab. */
@Composable
fun Markdown(source: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parse(source) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val link = MaterialTheme.colorScheme.primary
    val typography = MaterialTheme.typography
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) {
                when (block) {
                    is Block.Paragraph -> Text(inline(block.text, codeBackground, link, onLink), style = typography.bodyLarge)
                    is Block.Heading -> Text(
                        inline(block.text, codeBackground, link, onLink),
                        style = when (block.level) {
                            1 -> typography.titleLarge
                            2 -> typography.titleMedium
                            else -> typography.titleSmall
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    is Block.Bullet -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                        Text(block.marker, style = typography.bodyLarge, modifier = Modifier.width(24.dp))
                        Text(inline(block.text, codeBackground, link, onLink), style = typography.bodyLarge)
                    }
                    is Block.Quote -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            inline(block.text, codeBackground, link, onLink),
                            style = typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    is Block.Code -> Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            block.text,
                            style = MonoMedium,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
