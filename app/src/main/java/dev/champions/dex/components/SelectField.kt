package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.*

data class SelectOption(
    val id: String,
    val label: String,
    val detail: String = "",
) {
    val searchText = "$label $detail $id".lowercase()
}

/** Pill select field; opens a sheet with all options, searchable when long. */
@Composable
fun SelectField(
    label: String?,
    value: String,
    options: List<SelectOption>,
    modifier: Modifier = Modifier,
    container: Color = pal.bg,
    enabled: Boolean = true,
    bold: Boolean = false,
    tag: String? = null,
    onChoose: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (tag !=
                        null
                    ) {
                        Modifier.testTag(tag)
                    } else {
                        Modifier
                    },
                ).heightIn(min = 40.dp)
                .clip(PillShape)
                .background(container)
                .border(1.dp, pal.divider, PillShape)
                .clickable(enabled = enabled, onClickLabel = label ?: value) { open = true }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) pal.text else pal.n(600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Glyph(GlyphKind.CHEVRON, Modifier.size(16.dp).rotate(90f), if (enabled) pal.n(700) else pal.n(500))
        }
    }
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val shown =
            remember(query, options) {
                val search = SearchQuery(query)
                options.filter { search.matches(it.searchText) }
            }
        DexSheet({ open = false }) {
            SelectOptionsContent(label ?: "选择", value, options, shown, query, { query = it }, {
                onChoose(it)
                open = false
            }, { open = false })
        }
    }
}

/** The sheet [SelectField] opens: [shown] are the [options] matching [query]; a search field appears once the list is long. */
@Composable
fun SelectOptionsContent(
    title: String,
    value: String,
    options: List<SelectOption>,
    shown: List<SelectOption>,
    query: String,
    onQuery: (String) -> Unit,
    onChoose: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val long = options.size > 12
    Column(
        Modifier.fillMaxWidth().then(if (long) Modifier.fillMaxHeight(.85f) else Modifier).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            GhostButton("取消", onCancel)
        }
        if (long) SearchField(query, onQuery, "搜索名称、英文或编号", null)
        // A long list opens at the current choice, two rows down so the rows above show there is more.
        val list = rememberLazyListState((shown.indexOfFirst { it.label == value } - 2).coerceAtLeast(0))
        HideKeyboardWhileScrolling(list.isScrollInProgress)
        LazyColumn(
            if (long) Modifier.weight(1f) else Modifier,
            list,
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shown, key = { it.id }) { o ->
                val current = o.label == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (current) pal.a(100) else pal.surface)
                        .selectable(current) { onChoose(o.id) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (o.detail.isNotBlank()) Text(o.detail, fontSize = 12.sp, color = pal.n(700))
                    }
                    if (current) Text("当前", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.a(800))
                }
            }
        }
    }
}

private val PreviewOptions =
    listOf(
        SelectOption("", "无道具"),
        SelectOption("1", "讲究围巾", "Choice Scarf"),
        SelectOption("2", "生命宝珠", "Life Orb"),
        SelectOption("3", "气势披带", "Focus Sash"),
    )

@DexPreviews
@Composable
private fun SelectFieldPreview() =
    PreviewSurface {
        SelectField("道具", "生命宝珠", PreviewOptions) {}
        SelectField(null, "猛火", emptyList(), container = pal.surface, bold = true) {}
        SelectField("特性", "无法选择", emptyList(), enabled = false) {}
    }

@DexPreviews
@Composable
private fun SelectOptionsPreview() =
    PreviewSurface(padding = PaddingValues(top = 12.dp)) {
        SelectOptionsContent("道具", "生命宝珠", PreviewOptions, PreviewOptions, "", {}, {}, {})
    }
