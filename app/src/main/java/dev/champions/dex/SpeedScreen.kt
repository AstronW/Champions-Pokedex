package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import dev.champions.dex.components.*

private fun speedOf(p: Pokemon, mode: Int) = when (mode) { 0 -> actualStat(p.stats[5], 0, 5); 1 -> actualStat(p.stats[5], 32, 5); else -> actualStat(p.stats[5], 32, 5, 110) }

@Composable
fun SpeedScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    var me by rememberSaveable { mutableStateOf(model.team.firstNotNullOfOrNull { it }?.let { dex.battleForm(it).id } ?: dex.species.first().id) }
    var mode by rememberSaveable { mutableIntStateOf(2) }
    var scarf by rememberSaveable { mutableStateOf(false) }
    var tailwind by rememberSaveable { mutableStateOf(false) }
    val mine = dex.pokemonById[me] ?: dex.pokemon.first()
    val myValue = speedOf(mine, mode).let { if (scarf) it * 3 / 2 else it }.let { if (tailwind) it * 2 else it }
    val rows = remember(dex, me, mode, myValue) {
        dex.pokemon.map { it to if (it.id == mine.id) myValue else speedOf(it, mode) }.sortedByDescending { it.second }
    }
    val fastest = rows.first().second.coerceAtLeast(1)
    val faster = rows.count { it.first.id != mine.id && it.second < myValue }
    val tie = rows.count { it.first.id != mine.id && it.second == myValue }
    val options = remember(dex) { dex.pokemon.map { SelectOption(it.id, it.displayName, "${it.number()} · ${it.english} · 速度 ${it.stats[5]}") } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    // Item 0 is the controls card; three faster rows stay visible above mine for context.
    fun locate() = scope.launch {
        val target = (rows.indexOfFirst { it.first.id == mine.id } + 1 - 3).coerceAtLeast(1)
        if (reduceMotion) list.scrollToItem(target) else list.animateScrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Lv.50 实数值", DexRoot.SPEED.title, true, onMenu)
        LazyColumn(state = list, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "controls") {
                DexCard(Modifier.padding(bottom = 6.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    SelectField("我的宝可梦", mine.displayName, options, bold = true) { me = it }
                    PillSegment(listOf("无振", "准速 32", "最速 32+"), mode, { mode = it }, container = pal.bg)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleTile("讲究围巾 ×1.5", scarf, { scarf = !scarf }, Modifier.weight(1f))
                        ToggleTile("顺风 ×2", tailwind, { tailwind = !tailwind }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Value and ranking on separate lines, so large text wraps the ranking rather than splitting the number.
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("实数值 ", fontSize = 13.sp, color = pal.n(800))
                                Text("$myValue", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 22.sp)
                            }
                            Text("快于 $faster 只${if (tie > 0) "，同速 $tie 只" else ""}", fontSize = 13.sp, color = pal.n(800))
                        }
                        GhostButton("定位 ↓") { locate() }
                    }
                }
            }
            items(rows, key = { it.first.id }) { (p, v) ->
                val isMe = p.id == mine.id
                Row(Modifier.fillMaxWidth().clip(PillShape).background(if (isMe) pal.a(200) else pal.bg).clickable { onOpen(p.key) }.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Blob(p, 40.dp)
                    Text(p.displayName, Modifier.width(92.dp), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Bar(v / fastest.toFloat(), when { isMe -> pal.accent; v > myValue -> pal.n(400); v == myValue -> pal.a(300); else -> pal.g(400) }, Modifier.weight(1f))
                    Text("$v", Modifier.width(40.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
        }
    }
}
