package dev.champions.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.*

@Composable
fun FavoritesScreen(dex: Dex, model: DexViewModel, onMenu: () -> Unit, onOpen: (String) -> Unit) {
    val favPokemon = dex.pokemon.filter { it.key in model.favorites }
    val favMoves = dex.moves.filter { it.key in model.favorites }
    val favAbilities = dex.abilities.filter { it.key in model.favorites }
    val favItems = dex.items.filter { it.key in model.favorites }
    var left by rememberSaveable { mutableStateOf(favPokemon.getOrNull(0)?.id ?: "0003001") }
    var right by rememberSaveable { mutableStateOf(favPokemon.getOrNull(1)?.id ?: "0006000") }
    var picking by rememberSaveable { mutableIntStateOf(-1) }
    val a = dex.pokemonById[left] ?: dex.pokemon.first()
    val b = dex.pokemonById[right] ?: dex.pokemon.last()
    Column(Modifier.fillMaxSize()) {
        AppHeader("我的", DexRoot.FAVORITES.title, true, onMenu)
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "favHead") { SectionHeading("收藏 · ${favPokemon.size}") }
            item(key = "favRow") {
                if (favPokemon.isEmpty()) Text("在图鉴中点击星标收藏", fontSize = 13.sp, color = pal.n(700))
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favPokemon, key = { it.id }) { p ->
                        Column(Modifier.width(86.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen(p.key) }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Blob(p, 76.dp)
                            Text(p.displayName, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
            item(key = "cmpHead") { SectionHeading("种族值对比") }
            item(key = "cmpPick") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparePick(a, pal.accent, pal.a(100), pal.a(900), Modifier.weight(1f)) { picking = 0 }
                    ComparePick(b, pal.accent2, pal.g(100), pal.g(900), Modifier.weight(1f)) { picking = 1 }
                }
            }
            item(key = "cmp") {
                DexCard(padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
                    StatLabels.forEachIndexed { i, label ->
                        val x = a.stats[i]; val y = b.stats[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$x", Modifier.width(30.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (x > y) pal.a(700) else pal.n(700))
                            Bar(x / 160f, if (x >= y) pal.accent else pal.n(400), Modifier.weight(1f), fromEnd = true)
                            Text(label, Modifier.width(44.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), textAlign = TextAlign.Center)
                            Bar(y / 160f, if (y >= x) pal.g(600) else pal.n(400), Modifier.weight(1f))
                            Text("$y", Modifier.width(30.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (y > x) pal.g(700) else pal.n(700), textAlign = TextAlign.End)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${a.total}", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 20.sp, color = pal.a(700))
                        Text("总和", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pal.n(700), textAlign = TextAlign.Center)
                        Text("${b.total}", fontFamily = HeadingFont, fontWeight = HeadingWeight, fontSize = 20.sp, color = pal.g(700))
                    }
                }
            }
            if (favMoves.isNotEmpty()) {
                item(key = "movesHead") { SectionHeading("收藏的招式 · ${favMoves.size}") }
                items(favMoves, key = { "m${it.id}" }) { m -> MoveRow(m, true) { onOpen(m.key) } }
            }
            if (favAbilities.isNotEmpty()) {
                item(key = "abHead") { SectionHeading("收藏的特性 · ${favAbilities.size}") }
                items(favAbilities, key = { "a${it.id}" }) { x -> AbilityRow(x.name, x.description, "${dex.abilityUsers[x.id].orEmpty().size} 只", onClick = { onOpen(x.key) }) }
            }
            if (favItems.isNotEmpty()) {
                item(key = "itemHead") { SectionHeading("收藏的道具 · ${favItems.size}") }
                items(favItems, key = { "i${it.id}" }) { x -> AbilityRow(x.name, x.description, null, onClick = { onOpen(x.key) }, chevron = true) }
            }
        }
    }
    if (picking >= 0) PokemonPickerSheet("选择对比对象", dex, { if (picking == 0) left = it.id else right = it.id; picking = -1 }) { picking = -1 }
}

@Composable
private fun ComparePick(p: Pokemon, border: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(PillShape).border(2.dp, border, PillShape).background(bg).clickable(onClickLabel = "更换对比对象", onClick = onClick)
        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(pal.bg), contentAlignment = Alignment.Center) { PokemonArt(p, Modifier.size(40.dp)) }
        Text(p.displayName, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 2)
    }
}
