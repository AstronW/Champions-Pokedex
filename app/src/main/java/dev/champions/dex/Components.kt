package dev.champions.dex

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PillShape = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(32.dp)

/** Sends a short message to the app-wide toast. */
val LocalToast = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun BallMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val radius = size.minDimension * .43f
        val width = size.minDimension * .085f
        drawCircle(color, radius, style = Stroke(width))
        drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), width)
        drawCircle(color, radius * .29f, style = Stroke(width))
    }
}

// —— Line glyphs drawn on a 24-unit grid, matching the design's stroke icons. ——

enum class GlyphKind { MENU, BACK, STAR, FILTER, SWAP, CLOSE, PLUS, CHEVRON, SEARCH }

@Composable
fun Glyph(kind: GlyphKind, modifier: Modifier = Modifier.size(22.dp), color: Color = LocalContentColor.current, filled: Boolean = false) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = Stroke(2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        fun poly(vararg pts: Float, close: Boolean = false, fill: Boolean = false) {
            val path = Path().apply { moveTo(pts[0] * u, pts[1] * u); for (i in 2 until pts.size step 2) lineTo(pts[i] * u, pts[i + 1] * u); if (close) close() }
            if (fill) drawPath(path, color, style = Fill)
            drawPath(path, color, style = stroke)
        }
        when (kind) {
            GlyphKind.MENU -> { line(4f, 6f, 20f, 6f); line(4f, 12f, 14f, 12f); line(4f, 18f, 20f, 18f) }
            GlyphKind.BACK -> poly(15f, 18f, 9f, 12f, 15f, 6f)
            GlyphKind.CHEVRON -> poly(9f, 18f, 15f, 12f, 9f, 6f)
            GlyphKind.STAR -> poly(12f, 2f, 15.09f, 8.26f, 22f, 9.27f, 17f, 14.14f, 18.18f, 21.02f, 12f, 17.77f, 5.82f, 21.02f, 7f, 14.14f, 2f, 9.27f, 8.91f, 8.26f, close = true, fill = filled)
            GlyphKind.FILTER -> { line(21f, 4f, 14f, 4f); line(10f, 4f, 3f, 4f); line(21f, 12f, 12f, 12f); line(8f, 12f, 3f, 12f)
                line(21f, 20f, 16f, 20f); line(12f, 20f, 3f, 20f); line(14f, 2f, 14f, 6f); line(8f, 10f, 8f, 14f); line(16f, 18f, 16f, 22f) }
            GlyphKind.SWAP -> { poly(8f, 3f, 4f, 7f, 8f, 11f); line(4f, 7f, 20f, 7f); poly(16f, 21f, 20f, 17f, 16f, 13f); line(20f, 17f, 4f, 17f) }
            GlyphKind.CLOSE -> { line(18f, 6f, 6f, 18f); line(6f, 6f, 18f, 18f) }
            GlyphKind.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            GlyphKind.SEARCH -> { drawCircle(color, 8f * u, p(11f, 11f), style = stroke); line(21f, 21f, 16.7f, 16.7f) }
        }
    }
}

@Composable
fun RoundIconButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size).clip(CircleShape).clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) { content() }
}

@Composable
fun FavoriteButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    RoundIconButton(if (selected) "取消收藏" else "收藏", onClick, modifier, size) {
        Glyph(GlyphKind.STAR, Modifier.size(if (size < 40.dp) 18.dp else 22.dp), if (selected) pal.accent else pal.n(500), filled = selected)
    }
}

// —— Images ——

@Composable
fun AssetImage(path: String, description: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = if (path.isBlank()) null else SpriteCache.load(context, path)
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) {
        BallMark(Modifier.fillMaxSize(.6f), pal.text.copy(alpha = .13f))
    }
}

@Composable
fun PokemonArt(pokemon: Pokemon, modifier: Modifier = Modifier, shiny: Boolean = false) {
    val file = if (shiny && pokemon.shiny.isNotBlank()) pokemon.shiny else pokemon.sprite
    if (file.isNotBlank()) AssetImage("pokemon/$file", pokemon.form.ifBlank { pokemon.name }, modifier)
    else Box(modifier.semantics { contentDescription = "暂无形态图片" }, contentAlignment = Alignment.Center) {
        BallMark(Modifier.fillMaxSize(.5f), pal.text.copy(alpha = .16f))
    }
}

/** Sprite on a soft circle tinted by the first type. */
@Composable
fun Blob(p: Pokemon, size: Dp, modifier: Modifier = Modifier, imageScale: Float = .9f) {
    Box(modifier.size(size).clip(CircleShape).background(typeTone(p.types.first()).blob), contentAlignment = Alignment.Center) {
        PokemonArt(p, Modifier.size(size * imageScale))
    }
}

// —— Text helpers ——

@Composable
fun Kicker(text: String, color: Color = pal.n(700), modifier: Modifier = Modifier, spacing: TextUnit = 1.sp) =
    Text(text, modifier, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = spacing, color = color)

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium)

// —— Tags, pills and toggles ——

@Composable
fun TypeTag(type: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp, horizontal: Dp = 9.dp, vertical: Dp = 2.dp,
            onTint: Boolean = false, suffix: String = "") {
    val tone = typeTone(type)
    // On a type-tinted surface the tag uses the page colour so it stays readable.
    Text(TypeNames[type] + suffix, modifier.clip(PillShape).background(if (onTint) pal.bg.copy(alpha = .75f) else tone.bg).padding(horizontal = horizontal, vertical = vertical),
        color = tone.fg, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp, weight: FontWeight = FontWeight.Bold) =
    Text(text, modifier.clip(PillShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), color = fg,
        fontSize = fontSize, fontWeight = weight, maxLines = 1)

/** Segmented pill control. Scrolls horizontally when there are many options. */
@Composable
fun PillSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
                container: Color = pal.surface, height: Dp = 38.dp, fontSize: TextUnit = 13.sp, heading: Boolean = false,
                scrollable: Boolean = options.size > 5) {
    val scroll = scrollable
    Row((if (scroll) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth())
        .clip(PillShape).background(container).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box((if (scroll) Modifier else Modifier.weight(1f)).heightIn(min = height).clip(PillShape)
                .background(if (on) pal.accent else Color.Transparent)
                .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) pal.bg else pal.n(800), fontSize = fontSize, maxLines = 1,
                    fontWeight = if (heading) HeadingWeight else FontWeight.Bold, fontFamily = if (heading) HeadingFont else BodyFont)
            }
        }
    }
}

@Composable
fun ToggleTile(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, off: Color = pal.bg) {
    Box(modifier.heightIn(min = 38.dp).clip(PillShape).background(if (on) pal.g(600) else off)
        .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (on) pal.n(100) else pal.n(800), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun DexCard(modifier: Modifier = Modifier, color: Color = pal.surface, padding: PaddingValues = PaddingValues(16.dp),
            spacing: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(color).padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    Box(modifier.heightIn(min = height).clip(PillShape).background(pal.accent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center) { Text(text, color = pal.bg, fontSize = 15.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp, leading: (@Composable () -> Unit)? = null) {
    Row(modifier.heightIn(min = height).clip(PillShape).border(1.dp, pal.divider, PillShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        leading?.invoke()
        Text(text, color = pal.text, fontSize = 14.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        color = pal.accent, fontSize = 14.sp, fontFamily = HeadingFont, fontWeight = HeadingWeight)
}

// —— Header ——

@Composable
fun AppHeader(kicker: String, title: String, isRoot: Boolean, onNav: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(pal.bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoundIconButton(if (isRoot) "导航" else "返回", onNav) { Glyph(if (isRoot) GlyphKind.MENU else GlyphKind.BACK, color = pal.text) }
        Column(Modifier.weight(1f)) {
            if (kicker.isNotEmpty()) Text(kicker, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = pal.a(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

// —— Search and filters ——

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, filterCount: Int?, modifier: Modifier = Modifier, onFilter: () -> Unit = {}) {
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(PillShape).background(pal.surface).border(1.dp, pal.divider, PillShape)) {
        Glyph(GlyphKind.SEARCH, Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(18.dp), pal.n(600))
        BasicTextField(value, onChange, Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 42.dp, end = if (filterCount != null) 96.dp else 48.dp, top = 12.dp, bottom = 12.dp),
            singleLine = true, textStyle = TextStyle(color = pal.text, fontSize = 15.sp), cursorBrush = SolidColor(pal.accent),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = pal.n(600), fontSize = 15.sp, maxLines = 1); inner() } })
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) RoundIconButton("清空搜索", { onChange("") }, size = 40.dp) { Glyph(GlyphKind.CLOSE, Modifier.size(16.dp), pal.n(700)) }
            if (filterCount != null) RoundIconButton("筛选", onFilter, Modifier.background(if (filterCount > 0) pal.accent else pal.surface, CircleShape), size = 40.dp) {
                Glyph(GlyphKind.FILTER, Modifier.size(18.dp), if (filterCount > 0) pal.bg else pal.text)
            }
        }
    }
}

data class ActiveFilter(val label: String, val type: Int? = null, val clear: () -> Unit)

@Composable
fun ActiveFilters(caption: String?, filters: List<ActiveFilter>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (caption != null) Text(caption, Modifier.padding(end = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.n(700))
        filters.forEach { f ->
            val tone = f.type?.let { typeTone(it) }
            Row(Modifier.clip(PillShape).background(tone?.bg ?: pal.a(200)).clickable(onClickLabel = "清除${f.label}", onClick = f.clear)
                .padding(start = 12.dp, end = 9.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val fg = tone?.fg ?: pal.a(900)
                Text(f.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Glyph(GlyphKind.CLOSE, Modifier.size(12.dp), fg)
            }
        }
    }
}

/** Grid of 19 type buttons ("全部" + 18 types), five per row. */
@Composable
fun TypeGrid(selected: Int?, onSelect: (Int?) -> Unit, columns: Int = 5) {
    val all = listOf<Int?>(null) + TypeNames.indices
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    val on = selected == t; val tone = t?.let { typeTone(it) }
                    val bg = if (on) tone?.fg ?: pal.text else tone?.bg ?: pal.surface
                    val fg = if (on) pal.bg else tone?.fg ?: pal.text
                    Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(PillShape).background(bg).clickable(role = Role.RadioButton) { onSelect(t) },
                        contentAlignment = Alignment.Center) { Text(t?.let { TypeNames[it] } ?: "全部", color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SheetLabel(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), containerColor = pal.bg, contentColor = pal.text,
        scrimColor = pal.n(900).copy(alpha = .45f), dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(44.dp, 5.dp).clip(PillShape).background(pal.n(400)))
        }, content = content)
}

/** Filter sheet: category (moves only), type, sort and extra switches. */
@Composable
fun FilterSheet(title: String, categories: List<String>?, category: Int, onCategory: (Int) -> Unit, type: Int?, onType: (Int?) -> Unit,
                sorts: List<String>, sort: Int, onSort: (Int) -> Unit, toggles: List<Triple<String, Boolean, () -> Unit>>,
                doneLabel: String, onReset: () -> Unit, onDismiss: () -> Unit) {
    DexSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                GhostButton("重置", onReset)
            }
            if (categories != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetLabel("分类"); PillSegment(categories, category, onCategory)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SheetLabel("属性"); TypeGrid(type, onType) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SheetLabel("排序"); PillSegment(sorts, sort, onSort) }
            if (toggles.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                toggles.forEach { (label, on, flip) -> ToggleTile(label, on, flip, Modifier.weight(1f), off = pal.surface) }
            }
            PrimaryButton(doneLabel, onDismiss, Modifier.fillMaxWidth(), 50.dp)
        }
    }
}

/** Searchable Pokémon list in a bottom sheet. */
@Composable
fun PokemonPickerSheet(title: String, dex: Dex, onPick: (Pokemon) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(dex, query) {
        val search = SearchQuery(query)
        dex.pokemon.filter { search.matches(it.searchText) }
    }
    DexSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); GhostButton("取消", onDismiss)
            }
            SearchField(query, { query = it }, "搜索名称、英文或编号", null)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clip(PillShape).background(pal.surface).clickable { onPick(p) }.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Blob(p, 46.dp)
                        Text(p.displayName, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { p.types.distinct().forEach { TypeTag(it, horizontal = 8.dp) } }
                    }
                }
            }
        }
    }
}

data class SelectOption(val id: String, val label: String, val detail: String = "") {
    val searchText = "$label $detail $id".lowercase()
}

/** Pill select field; opens a sheet with all options, searchable when long. */
@Composable
fun SelectField(label: String?, value: String, options: List<SelectOption>, modifier: Modifier = Modifier, container: Color = pal.bg,
                enabled: Boolean = true, bold: Boolean = false, tag: String? = null, onChoose: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        Row(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).heightIn(min = 40.dp).clip(PillShape).background(container).border(1.dp, pal.divider, PillShape)
            .clickable(enabled = enabled, onClickLabel = label ?: value) { open = true }.padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) pal.text else pal.n(600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", color = pal.n(700), fontSize = 14.sp)
        }
    }
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val shown = remember(query, options) {
            val search = SearchQuery(query)
            options.filter { search.matches(it.searchText) }
        }
        DexSheet({ open = false }) {
            Column(Modifier.fillMaxWidth().then(if (options.size > 12) Modifier.fillMaxHeight(.85f) else Modifier).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label ?: "选择", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); GhostButton("取消") { open = false }
                }
                if (options.size > 12) SearchField(query, { query = it }, "搜索名称、英文或编号", null)
                LazyColumn(if (options.size > 12) Modifier.weight(1f) else Modifier, contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.id }) { o ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.surface).clickable { onChoose(o.id); open = false }
                            .padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Text(o.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (o.detail.isNotBlank()) Text(o.detail, fontSize = 12.sp, color = pal.n(700))
                        }
                    }
                }
            }
        }
    }
}

// —— List rows ——

private val DexRowShape = RoundedCornerShape(24.dp)

fun Pokemon.number() = "#" + no.toString().padStart(4, '0')

@Composable
fun PokemonRow(p: Pokemon, badge: String?, metricLabel: String, metric: String, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(DexRowShape).background(typeTone(p.types.first()).blob).clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PokemonArt(p, Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.number(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
                if (badge != null) Pill(badge, pal.bg.copy(alpha = .75f), pal.a(800), fontSize = 10.sp)
            }
            Text(p.displayName, fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 19.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { p.types.distinct().forEach { TypeTag(it, onTint = true) } }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FavoriteButton(favorite, onFavorite, size = 36.dp)
            Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.End) {
                Text(metricLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = pal.n(700))
                Text(metric, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun categoryColors(category: Int): Pair<Color, Color> = when (category) {
    0 -> pal.a(200) to pal.a(800); 1 -> pal.g(200) to pal.g(800); else -> pal.n(300) to pal.n(800)
}

@Composable
fun MoveRow(move: Move, showAccuracy: Boolean, favorite: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PillShape).background(pal.surface).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeTag(move.type, Modifier.width(52.dp), horizontal = 0.dp, vertical = 3.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(move.name, Modifier.weight(1f, false), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (move.available) pal.text else pal.n(600))
            if (favorite) Glyph(GlyphKind.STAR, Modifier.size(12.dp), pal.accent, filled = true)
            if (!move.available) Text("未开放", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.a(700))
        }
        val (bg, fg) = categoryColors(move.category)
        Text(move.categoryLabel, Modifier.width(40.dp).clip(PillShape).background(bg).padding(vertical = 2.dp), color = fg, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(move.powerLabel, Modifier.width(if (showAccuracy) 36.dp else 44.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        if (showAccuracy) Text(move.accuracyLabel.removeSuffix("%"), Modifier.width(36.dp), fontSize = 13.sp, color = pal.n(700), textAlign = TextAlign.End)
    }
}

@Composable
fun MoveHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        @Composable fun h(t: String, m: Modifier, a: TextAlign = TextAlign.Start) = Text(t, m, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = pal.n(700), textAlign = a)
        h("属性", Modifier.width(52.dp)); h("招式", Modifier.weight(1f)); h("分类", Modifier.width(40.dp), TextAlign.Center)
        h("威力", Modifier.width(36.dp), TextAlign.End); h("命中", Modifier.width(36.dp), TextAlign.End)
    }
}

@Composable
fun AbilityRow(name: String, description: String, trailing: String?, favorite: Boolean = false, onClick: () -> Unit, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(pal.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (favorite) Glyph(GlyphKind.STAR, Modifier.size(12.dp), pal.accent, filled = true)
            }
            Text(description.replace('\n', ' ').ifBlank { "游戏未提供此条目的说明。" }, fontSize = 12.5.sp, lineHeight = 18.sp, color = pal.n(800),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.n(700))
        if (chevron) Glyph(GlyphKind.CHEVRON, Modifier.size(18.dp), pal.n(600))
    }
}

/** Animated horizontal bar. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, track: Color = pal.n(200), fromEnd: Boolean = false) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(if (LocalReduceMotion.current) 0 else 450), label = "bar")
    Box(modifier.height(height).clip(PillShape).background(track), contentAlignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(PillShape).background(color))
    }
}

@Composable
fun statColor(v: Int) = when { v >= 120 -> pal.g(600); v >= 100 -> pal.g(400); v >= 80 -> pal.a(400); else -> pal.a(300) }

@Composable
fun EmptyResult(text: String = "暂时没有匹配结果", onClear: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BallMark(Modifier.size(48.dp), pal.n(400))
        Spacer(Modifier.height(16.dp))
        Text(text, fontSize = 14.sp, color = pal.n(700))
        if (onClear != null) Box(Modifier.padding(top = 8.dp)) { GhostButton("清除筛选", onClear) }
    }
}


/** A labelled group of type tags; [suffix] adds e.g. a member count after the type name. */
data class TypeGroup(val label: String, val color: Color, val types: List<Int>, val suffix: (Int) -> String = { "" })

/** Shared layout for type-matchup summaries (Pokémon detail and team analysis). */
@Composable
fun TypeGroupsCard(title: String, groups: List<TypeGroup>, note: String, empty: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(title)
        DexCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), spacing = 12.dp) {
            val shown = groups.filter { it.types.isNotEmpty() }
            if (shown.isEmpty() && empty != null) Text(empty, fontSize = 12.sp, color = pal.n(700))
            shown.forEach { g ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.label, Modifier.width(62.dp).padding(top = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = g.color)
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        g.types.forEach { TypeTag(it, horizontal = 10.dp, vertical = 3.dp, suffix = g.suffix(it)) }
                    }
                }
            }
            Text(note, fontSize = 11.sp, color = pal.n(700))
        }
    }
}

