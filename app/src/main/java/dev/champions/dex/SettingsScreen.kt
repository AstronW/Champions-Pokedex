package dev.champions.dex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.champions.dex.components.AppHeader
import dev.champions.dex.components.DexCard
import dev.champions.dex.components.GhostButton
import dev.champions.dex.components.Kicker
import dev.champions.dex.components.LocalToast
import dev.champions.dex.components.PillSegment
import dev.champions.dex.components.ToggleTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatBytes(bytes: Long) =
    when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

@Composable
fun SettingsScreen(
    model: DexViewModel,
    dex: Dex,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        AppHeader("偏好与关于", "设置", false, onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("让图鉴更合你意", style = MaterialTheme.typography.headlineMedium)
                Text("按自己的习惯，探索与收藏。", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = pal.n(700))
            }
            item {
                DexCard {
                    Kicker("外观")
                    PillSegment(
                        Appearance.entries.map {
                            it.label
                        },
                        model.appearance.ordinal,
                        { model.changeAppearance(Appearance.entries[it]) },
                        container = pal.bg,
                        height = 40.dp,
                    )
                    Kicker("动态效果", modifier = Modifier.padding(top = 4.dp))
                    ToggleTile("减少动态效果", model.reduceMotion, { model.changeMotion(!model.reduceMotion) }, Modifier.fillMaxWidth())
                    Text("简化页面切换与数值动画", fontSize = 12.sp, color = pal.n(700))
                }
            }
            item {
                DexCard {
                    Kicker("这本图鉴")
                    Text(
                        "${dex.speciesCount} 种宝可梦 · ${dex.pokemon.size} 种形态\n${dex.availableMoves.size} 个可用招式 · ${dex.abilities.size} 个特性\n${model.favorites.size} 条收藏",
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    )
                    Text("数据与图片均已内置，日常查询无需网络。", fontSize = 12.sp, color = pal.n(700))
                }
            }
            item {
                val scope = rememberCoroutineScope()
                val toast = LocalToast.current
                var refresh by remember { mutableIntStateOf(0) }
                val size by produceState<Long?>(null, refresh) { value = AnimatedSprites.cacheSize(context) }
                DexCard {
                    Kicker("缓存")
                    Text(
                        "宝可梦动图 · ${size?.let(::formatBytes) ?: "计算中…"}",
                        fontSize = 15.sp,
                    )
                    Text(
                        "详情页联网时下载的宝可梦动图，保存在本机缓存，再次打开无需重新下载；超过 256 MB 时自动删除最久未看的。清除只删除这些动图，内置图片、收藏、队伍与设置不受影响。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = pal.n(700),
                    )
                    GhostButton("清除宝可梦动图缓存") {
                        scope.launch {
                            AnimatedSprites.clearCache(context)
                            refresh++
                            toast("已清除宝可梦动图缓存")
                        }
                    }
                }
            }
            item {
                DexCard {
                    Kicker("数据来源")
                    Text(
                        "游戏数据：champout 提取的 Pokémon Champions 数据。\n百科详解：52poke 神奇宝贝百科，2026-09-13 离线存档，各页附修订链接。文字已转为简体和阅读格式，保留原文的版本说明。\n图片：PokeAPI sprites / generation-ix / champions。缺少的形态图片显示占位；详情页动图联网时下载并缓存。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = pal.n(800),
                    )
                    GhostButton("52poke 版权与使用条款 ↗") { openWeb(context, "https://wiki.52poke.com/wiki/神奇宝贝百科:版权声明") }
                    GhostButton("PokeAPI 图片来源与声明 ↗") { openWeb(context, "https://github.com/PokeAPI/sprites/blob/master/LICENCE.txt") }
                }
            }
            item {
                var fonts by rememberSaveable { mutableStateOf(false) }
                val license by produceState("", fonts) {
                    if (fonts) {
                        value =
                            withContext(Dispatchers.IO) {
                                listOf(
                                    "OFL-Caprasimo.txt",
                                    "OFL-Figtree.txt",
                                ).joinToString("\n\n") { f ->
                                    context.assets.open("licenses/$f").bufferedReader().use {
                                        it.readText()
                                    }
                                }
                            }
                    }
                }
                DexCard {
                    Kicker("字体")
                    Text(
                        "标题 Caprasimo、正文 Figtree，均为 SIL Open Font License 1.1。中文使用系统字体。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = pal.n(800),
                    )
                    GhostButton(if (fonts) "收起许可原文" else "阅读许可原文") { fonts = !fonts }
                    if (fonts) Text(license, fontSize = 11.sp, lineHeight = 16.sp, color = pal.n(700))
                }
            }
            item {
                DexCard {
                    Kicker("非官方爱好者图鉴")
                    Text(
                        "Pokémon 及相关角色、游戏文本与图像归其权利人所有。本应用与 The Pokémon Company、Nintendo、Game Freak 无隶属关系。百科文字归 52poke 条目贡献者所有，沿用 CC BY-NC-SA 3.0 许可。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = pal.n(800),
                    )
                }
            }
            item {
                Text(
                    "CHAMPIONS DEX  0.1.0\n数据 ${dex.fingerprint.take(12)}\n图片 ${dex.spriteCommit.take(12)}",
                    fontSize = 11.sp,
                    color = pal.n(700),
                )
            }
        }
    }
}
