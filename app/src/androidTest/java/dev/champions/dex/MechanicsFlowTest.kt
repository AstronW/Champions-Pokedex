package dev.champions.dex

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MechanicsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun search(text: String) = compose.onNode(hasSetTextAction()).performTextReplacement(text)
    private fun openRow(name: String) = compose.onNode(hasText(name) and hasClickAction() and !hasSetTextAction()).performClick()
    private fun scrollTo(matcher: SemanticsMatcher) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)

    @Test fun moveCategoryRelatedAbilityAndFlagsSurviveReturnAndRecreation() {
        compose.waitForCatalog(); compose.navigate("招式"); search("火焰拳"); openRow("火焰拳")
        scrollTo(hasText("拳击类")); openRow("拳击类")
        compose.onNodeWithText("成员招式 · 16").assertExists()
        scrollTo(hasText("铁拳")); openRow("铁拳")
        scrollTo(hasTestTag("special-flags")); compose.onNodeWithTag("special-flags").performClick()
        scrollTo(hasText("可以被交换（百科默认）")); compose.onNodeWithText("可以被交换（百科默认）").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithText("可以被交换（百科默认）").assertIsDisplayed()
        compose.back(); compose.onNodeWithText("铁拳").assertIsDisplayed()
        compose.back(); compose.onNodeWithText("拳击类").assertIsDisplayed()
    }

    @Test fun statusLinkOpensOfflineAndReturnsToSameMovePosition() {
        compose.waitForCatalog(); compose.navigate("招式"); search("火焰拳"); openRow("火焰拳")
        scrollTo(hasText("灼伤") and hasClickAction()); openRow("灼伤")
        compose.onNodeWithTag("status-detail-list").assertExists()
        compose.onNodeWithText("游戏说明 · CHAMPIONS").assertExists()
        scrollTo(hasText("百科详解 · 52POKE"))
        scrollTo(hasText("来源：52poke 神奇宝贝百科及条目贡献者"))
        compose.back(); compose.onNodeWithText("灼伤").assertIsDisplayed()
        compose.back(); compose.onNode(hasSetTextAction()).assertTextContains("火焰拳")
    }

    @Test fun statusAliasSearchAndDarkLandscapePreserveGameText() {
        compose.waitForCatalog(); compose.navigate("设置与关于")
        compose.onNode(segment("暗色") and hasAnyAncestor(hasTestTag("detail:settings"))).performClick()
        compose.back(); compose.navigate("状态说明"); search("剧毒")
        compose.onNodeWithText("中毒").assertExists()
        search("麻痹"); openRow("麻痹")
        compose.onNodeWithText("12.5%", substring = true).assertExists()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        scrollTo(hasText("12.5%", substring = true))
        compose.onNodeWithText("12.5%", substring = true).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.waitForIdle(); compose.back()
        compose.onNode(hasSetTextAction()).assertTextContains("麻痹")
    }

    @Test fun categoryIndexSearchKeepsItsQueryOnBack() {
        compose.waitForCatalog(); compose.navigate("招式"); compose.onNodeWithText("详细分类").performClick()
        search("声音"); openRow("声音类")
        compose.onNodeWithText("成员招式 · 25").assertExists()
        compose.back(); compose.onNode(hasSetTextAction()).assertTextContains("声音")
    }
}
