package dev.champions.dex.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.TypeNames
import dev.champions.dex.pal

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    filterCount: Int?,
    modifier: Modifier = Modifier,
    onFilter: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .background(pal.surface)
            .border(1.dp, pal.divider, PillShape),
    ) {
        Glyph(GlyphKind.SEARCH, Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(18.dp), pal.n(600))
        BasicTextField(
            value,
            onChange,
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(
                start = 42.dp,
                end =
                    if (filterCount !=
                        null
                    ) {
                        96.dp
                    } else {
                        48.dp
                    },
                top = 12.dp,
                bottom = 12.dp,
            ),
            singleLine = true,
            textStyle = TextStyle(color = pal.text, fontSize = 15.sp),
            cursorBrush = SolidColor(pal.accent),
            // Results already follow every keystroke, so the keyboard's search key only puts the keyboard away.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = pal.n(600), fontSize = 15.sp, maxLines = 1)
                    inner()
                }
            },
        )
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) {
                RoundIconButton(
                    "清空搜索",
                    { onChange("") },
                    size = 40.dp,
                ) { Glyph(GlyphKind.CLOSE, Modifier.size(16.dp), pal.n(700)) }
            }
            if (filterCount !=
                null
            ) {
                RoundIconButton(
                    "筛选",
                    onFilter,
                    Modifier
                        .background(if (filterCount > 0) pal.accent else pal.surface, CircleShape)
                        .semantics { stateDescription = if (filterCount > 0) "已启用 $filterCount 项" else "未启用" },
                    size = 40.dp,
                ) {
                    Glyph(GlyphKind.FILTER, Modifier.size(18.dp), if (filterCount > 0) pal.bg else pal.text)
                }
            }
        }
    }
}

/** Dragging a list puts the keyboard away, so the results it was covering come into view; the query stays. */
@Composable
fun HideKeyboardWhileScrolling(scrolling: Boolean) {
    val focus = LocalFocusManager.current
    LaunchedEffect(scrolling) { if (scrolling) focus.clearFocus() }
}

data class ActiveFilter(
    val label: String,
    val type: Int? = null,
    val clear: () -> Unit,
)

@Composable
fun ActiveFilters(
    caption: String?,
    filters: List<ActiveFilter>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (caption !=
            null
        ) {
            Text(caption, Modifier.padding(end = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.n(700))
        }
        filters.forEach { f ->
            val clear = Modifier.clip(PillShape).clickable(onClickLabel = "清除${f.label}", onClick = f.clear)
            if (f.type !=
                null
            ) {
                TypeTag(
                    f.type,
                    clear,
                    fontSize = 12.sp,
                    icon = 22.dp,
                    vertical = 5.dp,
                ) { fg -> Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), fg) }
            } else {
                Row(
                    clear.background(pal.a(200)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(f.label, color = pal.a(900), fontSize = 12.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                    Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), pal.a(900))
                }
            }
        }
    }
}

@DexPreviews
@LargeTextPreview
@Composable
private fun SearchPreview() =
    PreviewSurface {
        SearchField("", {}, "搜索名称、英文或编号", 0)
        SearchField("喷火", {}, "搜索名称、英文或编号", 2)
        SearchField("", {}, "搜索招式", null)
        ActiveFilters("共 12 个", listOf(ActiveFilter(TypeNames[9], 9) {}, ActiveFilter("按速度排序") {}, ActiveFilter("仅看收藏") {}))
    }
