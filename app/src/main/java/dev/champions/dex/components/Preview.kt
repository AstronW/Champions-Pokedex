package dev.champions.dex.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.champions.dex.*

/** Light and dark previews at phone width; the theme follows the preview's night mode like [Appearance.SYSTEM]. */
@Preview(name = "亮色", widthDp = 360, locale = "zh-rCN")
@Preview(name = "暗色", widthDp = 360, locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class DexPreviews

/** The light theme at 1.3× font scale, the large-text check every screen must pass. */
@Preview(name = "大字体", widthDp = 360, locale = "zh-rCN", fontScale = 1.3f)
annotation class LargeTextPreview

/** App theme on the page background, with the components stacked like on a screen. */
@Composable
internal fun PreviewSurface(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    DexTheme(Appearance.SYSTEM, reduceMotion = false) {
        Column(modifier.fillMaxWidth().background(pal.bg).padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Real entries from dex.json, so previews show the same sprites and labels as the app. */
internal object PreviewData {
    val venusaur = Pokemon("0003000", 3, 0, "妙蛙花", "Venusaur", "", "", listOf(11, 3), listOf(80, 82, 83, 100, 100, 80), listOf(65, 65, 34), 1000, 0, "3.png", "shiny_3.png")
    val charizard = Pokemon("0006000", 6, 0, "喷火龙", "Charizard", "", "", listOf(9, 2), listOf(78, 84, 78, 109, 85, 100), listOf(66, 66, 94), 905, 0, "6.png", "shiny_6.png")
    val megaCharizardX = Pokemon("0006001", 6, 1, "喷火龙", "Charizard", "超级喷火龙Ｘ", "Mega Charizard X", listOf(9, 15), listOf(78, 130, 111, 130, 85, 100),
        listOf(181, 181, 181), 1105, 0, "10034.png", "shiny_10034.png")
    val pikachu = Pokemon("0025000", 25, 0, "皮卡丘", "Pikachu", "", "", listOf(12), listOf(35, 55, 40, 50, 50, 90), listOf(9, 9, 31), 60, 0, "25.png", "shiny_25.png")
    val garchomp = Pokemon("0445000", 445, 0, "烈咬陆鲨", "Garchomp", "", "", listOf(15, 4), listOf(108, 130, 95, 80, 85, 102), listOf(8, 8, 24), 950, 0, "445.png", "shiny_445.png")
    val pokemon = listOf(venusaur, charizard, megaCharizardX, pikachu, garchomp)

    val flamethrower = Move(53, "喷射火焰", "Flamethrower", "有10%的机率会让对手陷入灼伤状态。", true, 9, 1, 90, 100, 16, 0, false)
    val earthquake = Move(89, "地震", "Earthquake", "对地下状态的对手的威力会变为2倍。\n青草场地状态时，\n威力会变为1/2。", true, 4, 0, 100, 100, 12, 0, false)
    /** Power 1 marks power the game works out in battle; it must not read as 1. */
    val seismicToss = Move(69, "地球上投", "Seismic Toss", "会给予对手的HP50伤害。", true, 1, 0, 1, 100, 20, 0, true)
    /** Accuracy 101 skips the accuracy check; it must not read as 101%. */
    val swordsDance = Move(14, "剑舞", "Swords Dance", "会让自己的攻击提高2阶。", true, 0, 2, 0, 101, 20, 0, false)
    val vineWhip = Move(22, "藤鞭", "Vine Whip", "", false, 11, 0, 45, 100, 20, 0, true)
    val moves = listOf(flamethrower, earthquake, seismicToss, swordsDance, vineWhip)

    val blaze = Ability(66, "猛火", "Blaze", "HP变为最大HP的1/3以下时，\n火属性招式的威力会变为1.5倍。")
    val solarPower = Ability(94, "太阳之力", "Solar Power", "大晴天状态时，特攻会变为1.5倍，\n但在回合结束时会减少最大HP的1/8。")
}
