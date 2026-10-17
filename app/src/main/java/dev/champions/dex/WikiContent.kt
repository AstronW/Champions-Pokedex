package dev.champions.dex

import android.content.Context
import android.content.Intent
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.champions.dex.components.AssetImage
import dev.champions.dex.components.Kicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun openWeb(
    context: Context,
    url: String,
) {
    val uri = url.toUri()
    if (uri.scheme != "https") return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
        Toast.makeText(context, "没有可打开链接的浏览器", Toast.LENGTH_SHORT).show()
    }
}

// —— Model: the importer emits a small, fixed subset of HTML (text, a, b, i, sup, sub, small, span, div, br, h2/h3, tables). ——

data class WikiRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val small: Boolean = false,
    val href: String? = null,
    val item: String? = null,
)

sealed interface WikiBlock {
    data class Text(
        val runs: List<WikiRun>,
    ) : WikiBlock

    data class Heading(
        val runs: List<WikiRun>,
    ) : WikiBlock

    data class Table(
        val rows: List<List<Cell>>,
    ) : WikiBlock

    data class Cell(
        val header: Boolean,
        val span: Int,
        val blocks: List<WikiBlock>,
    )
}

private val token = Regex("""<(/?)([a-zA-Z0-9]+)([^>]*)>|([^<]+)""")
private val colspan = Regex("""colspan="(\d+)"""")
private val hrefAttr = Regex("""href="([^"]*)"""")
private val itemAttr = Regex("""data-item="([^"]*)"""")

internal fun decodeEntities(s: String) =
    if ('&' !in s) {
        s
    } else {
        s.replace(Regex("&(#x?[0-9a-fA-F]+|[a-z]+);")) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")[e]
            } ?: m.value
        }
    }

/** Parses the importer's HTML into blocks. Unknown tags keep their text; nothing is dropped. */
fun parseWiki(html: String): List<WikiBlock> {
    val tokens = token.findAll(html).toList()
    var i = 0
    // Tables and cells recurse into blocks(); declared up front because local functions cannot be forward-referenced.
    lateinit var table: () -> WikiBlock.Table

    fun blocks(until: Set<String>): List<WikiBlock> {
        val out = mutableListOf<WikiBlock>()
        val runs = mutableListOf<WikiRun>()
        val styles = ArrayList<Triple<String, String?, String?>>()

        fun run(text: String) =
            WikiRun(
                text,
                styles.any { it.first == "b" || it.first == "strong" },
                styles.any {
                    it.first == "i" ||
                        it.first == "em"
                },
                styles.any { it.first == "sup" },
                styles.any { it.first == "sub" },
                styles.any { it.first == "small" },
                styles
                    .lastOrNull {
                        it.second !=
                            null
                    }?.second,
                styles.lastOrNull { it.second != null }?.third,
            )

        fun flush(heading: Boolean = false) {
            val trimmed = runs.toMutableList()
            while (trimmed.isNotEmpty() && trimmed.first().text.isBlank()) trimmed.removeAt(0)
            while (trimmed.isNotEmpty() && trimmed.last().text.isBlank()) trimmed.removeAt(trimmed.lastIndex)
            if (trimmed.isNotEmpty()) {
                trimmed[0] = trimmed[0].copy(text = trimmed[0].text.trimStart())
                trimmed[trimmed.lastIndex] = trimmed.last().copy(text = trimmed.last().text.trimEnd())
                out += if (heading) WikiBlock.Heading(trimmed) else WikiBlock.Text(trimmed)
            }
            runs.clear()
        }
        while (i < tokens.size) {
            val t = tokens[i++]
            val text = t.groups[4]?.value
            if (text != null) {
                runs += run(decodeEntities(text))
                continue
            }
            val closing = t.groupValues[1] == "/"
            val tag = t.groupValues[2].lowercase()
            val attrs = t.groupValues[3]
            when {
                closing && tag in until -> {
                    flush()
                    return out
                }

                !closing && tag == "br" -> {
                    runs += run("\n")
                }

                !closing && (tag == "h2" || tag == "h3") -> {
                    flush()
                    styles += Triple("b", null, null)
                }

                closing && (tag == "h2" || tag == "h3") -> {
                    styles.removeAll { it.first == "b" && it.second == null }
                    flush(heading = true)
                }

                // A one-cell table is only a layout wrapper (usually around another table); keep its content, not the frame.
                !closing && tag == "table" -> {
                    flush()
                    val t = table()
                    out += t.rows
                        .singleOrNull()
                        ?.singleOrNull()
                        ?.blocks ?: listOf(t)
                }

                tag == "div" || tag == "p" -> {
                    if (runs.isNotEmpty()) runs += run("\n")
                }

                !closing -> {
                    styles +=
                        Triple(
                            tag,
                            if (tag == "a") {
                                hrefAttr
                                    .find(attrs)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.let(::decodeEntities)
                            } else {
                                null
                            },
                            if (tag == "a") itemAttr.find(attrs)?.groupValues?.get(1) else null,
                        )
                }

                else -> {
                    styles.indexOfLast { it.first == tag }.takeIf { it >= 0 }?.let { styles.removeAt(it) }
                }
            }
        }
        flush()
        return out
    }

    fun cell(
        header: Boolean,
        attrs: String,
    ) = WikiBlock.Cell(
        header,
        colspan
            .find(attrs)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.coerceIn(1, 8) ?: 1,
        blocks(setOf(if (header) "th" else "td", "tr", "table")).also {
            if (i > 0 &&
                tokens[i - 1].groupValues[2].lowercase() in setOf("tr", "table")
            ) {
                i--
            }
        },
    )
    table =

        fun(): WikiBlock.Table {
            val rows = mutableListOf<List<WikiBlock.Cell>>()
            var row = mutableListOf<WikiBlock.Cell>()
            while (i < tokens.size) {
                val t = tokens[i++]
                if (t.groups[4] != null) continue
                val closing = t.groupValues[1] == "/"
                val tag = t.groupValues[2].lowercase()
                when {
                    closing && tag == "table" -> {
                        break
                    }

                    !closing && tag == "tr" -> {
                        if (row.isNotEmpty()) rows += row
                        row = mutableListOf()
                    }

                    closing && tag == "tr" -> {
                        if (row.isNotEmpty()) rows += row
                        row = mutableListOf()
                    }

                    !closing && (tag == "td" || tag == "th") -> {
                        row += cell(tag == "th", t.groupValues[3])
                    }
                }
            }
            if (row.isNotEmpty()) rows += row
            return WikiBlock.Table(rows)
        }
    return blocks(emptySet())
}

private object WikiCache {
    val pages = LruCache<String, List<WikiBlock>>(48)

    @Volatile private var bodies: JSONObject? = null

    /** All article bodies live in one asset; it is read once, on first use. */
    fun html(
        context: Context,
        key: String,
    ): String =
        (
            bodies
                ?: context.applicationContext.assets
                    .open("wiki.json")
                    .bufferedReader()
                    .use { JSONObject(it.readText()) }
                    .also { bodies = it }
        ).getString(key)
}

/** Loads and parses one page off the main thread; null while loading, empty list on failure. Failures are not cached, so the next visit retries. */
@Composable
fun rememberWiki(page: WikiPage?): List<WikiBlock>? {
    val context = LocalContext.current
    val cached = page?.let { WikiCache.pages.get(it.key) }
    val state by produceState(cached, page?.key) {
        if (page == null || value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { parseWiki(WikiCache.html(context, page.key)) }.getOrNull()
        }?.also { WikiCache.pages.put(page.key, it) } ?: emptyList()
    }
    return state
}

// —— Rendering ——

/** Wiki titles disambiguate by kind, e.g. "灼伤（状态）"; the page itself already says what it is. */
internal val EntitySuffix = Regex("[（(](招式|特性|道具|状态)[）)]$")

/** Links the app can open itself (entities, items it ships an icon for) use the accent colour; links that only lead to the web are muted. */
private fun inApp(r: WikiRun) = r.href != null && (r.item != null || r.href.startsWith("dex://"))

@Composable
private fun annotated(
    runs: List<WikiRun>,
    onLink: (String) -> Unit,
): AnnotatedString {
    val local = pal.a(700)
    val web = pal.n(700)
    return remember(runs, local, web) {
        buildAnnotatedString {
            runs.forEach { r ->
                val style =
                    SpanStyle(
                        fontWeight = if (r.bold) FontWeight.Bold else null,
                        fontStyle = if (r.italic) FontStyle.Italic else null,
                        fontSize = if (r.sup || r.sub || r.small) .75.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                        baselineShift =
                            if (r.sup) {
                                BaselineShift.Superscript
                            } else if (r.sub) {
                                BaselineShift.Subscript
                            } else {
                                null
                            },
                    )
                if (r.href != null) {
                    val color = if (inApp(r)) local else web
                    withLink(
                        LinkAnnotation.Clickable(
                            r.href,
                            TextLinkStyles(SpanStyle(color = color, textDecoration = if (inApp(r)) null else TextDecoration.Underline)),
                        ) {
                            onLink(r.href)
                        },
                    ) {
                        if (r.item != null) appendInlineContent("item:" + r.item, r.text)
                        withStyle(style) { append(r.text) }
                    }
                } else {
                    withStyle(style) { append(r.text) }
                }
            }
        }
    }
}

/** Inline icons for item links; sized to the surrounding text so line height is unchanged. */
@Composable
private fun itemIcons(
    runs: List<WikiRun>,
    fontSize: Float,
): Map<String, InlineTextContent> =
    remember(runs, fontSize) {
        runs.mapNotNull { it.item }.distinct().associate { item ->
            "item:$item" to
                InlineTextContent(Placeholder((fontSize * 1.4f).sp, (fontSize * 1.4f).sp, PlaceholderVerticalAlign.TextCenter)) {
                    AssetImage("items/$item.png", null, Modifier.fillMaxSize())
                }
        }
    }

@Composable
private fun WikiBlockView(
    block: WikiBlock,
    onLink: (String) -> Unit,
    fontSize: Float = 14.5f,
) {
    when (block) {
        is WikiBlock.Heading -> {
            Text(
                annotated(block.runs, onLink),
                Modifier.padding(top = 6.dp),
                inlineContent = itemIcons(block.runs, 18f),
                fontFamily = HeadingFont,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
        }

        is WikiBlock.Text -> {
            Text(
                annotated(block.runs, onLink),
                inlineContent = itemIcons(block.runs, fontSize),
                fontSize = fontSize.sp,
                lineHeight =
                    (
                        fontSize *
                            1.65f
                    ).sp,
            )
        }

        is WikiBlock.Table -> {
            val columns = block.rows.maxOfOrNull { r -> r.sumOf { it.span } } ?: 1
            val width = if (columns <= 2) 150.dp else 112.dp
            Column(
                Modifier
                    .horizontalScroll(
                        rememberScrollState(),
                    ).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, pal.divider, RoundedCornerShape(16.dp)),
            ) {
                block.rows.forEachIndexed { r, row ->
                    Row(
                        Modifier.height(IntrinsicSize.Min).background(
                            if (row.all { it.header }) {
                                pal.n(200)
                            } else if (r % 2 ==
                                0
                            ) {
                                pal.bg
                            } else {
                                pal.surface
                            },
                        ),
                    ) {
                        row.forEach { cell ->
                            Column(
                                Modifier.width(width * cell.span).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                cell.blocks.forEach { b ->
                                    if (cell.header &&
                                        b is WikiBlock.Text
                                    ) {
                                        Text(
                                            annotated(b.runs, onLink),
                                            inlineContent = itemIcons(b.runs, 12.5f),
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else {
                                        WikiBlockView(b, onLink, 12.5f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Appends the 52poke article for [key] (with source, revision and licence) to a detail list. */
fun LazyListScope.wikiSection(
    key: String,
    dex: Dex,
    onOpen: (String) -> Unit,
) {
    val page = dex.wiki[key] ?: return
    item(key = "wikiHead") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Kicker("百科详解 · 52POKE", color = pal.a(700))
            Text(page.title.replace(EntitySuffix, ""), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
    }
    item(key = "wikiBody") {
        val context = LocalContext.current
        val blocks = rememberWiki(page)
        val onLink: (String) -> Unit = { url ->
            val uri = url.toUri()
            if (uri.scheme == "dex" && uri.host == "entity") {
                val target = uri.path.orEmpty().removePrefix("/")
                val exists =
                    when (target.substringBefore(':')) {
                        "move" -> dex.movesById.containsKey(target.substringAfter(':').toIntOrNull())
                        "ability" -> dex.abilitiesById.containsKey(target.substringAfter(':').toIntOrNull())
                        "item" -> dex.itemsById.containsKey(target.substringAfter(':').toIntOrNull())
                        "pokemon" -> dex.pokemonById.containsKey(target.substringAfter(':'))
                        "status" -> dex.mechanics.statusesByKey.containsKey(target)
                        "category" -> dex.mechanics.categoriesById.containsKey(target.substringAfter(':'))
                        else -> false
                    }
                if (exists) onOpen(target)
            } else if (uri.scheme == "https") {
                openWeb(context, url)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                blocks == null -> Text("正在载入…", fontSize = 13.sp, color = pal.n(700))
                blocks.isEmpty() -> Text("此篇离线内容未能加载。", fontSize = 13.sp, color = pal.n(700))
                else -> blocks.forEach { WikiBlockView(it, onLink) }
            }
            // CC BY-NC-SA attribution: author, licence and the exact source revision. Conversion notes live in Settings.
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("来源：52poke 神奇宝贝百科及条目贡献者", fontSize = 11.sp, color = pal.n(700))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "修订 ${page.revision} ↗",
                        Modifier
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                openWeb(context, page.url)
                            }.padding(vertical = 12.dp),
                        fontSize = 11.sp,
                        color = pal.a(700),
                    )
                    Text(
                        "CC BY-NC-SA 3.0 ↗",
                        Modifier
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                openWeb(context, "https://creativecommons.org/licenses/by-nc-sa/3.0/")
                            }.padding(vertical = 12.dp),
                        fontSize = 11.sp,
                        color = pal.a(700),
                    )
                }
            }
        }
    }
}
