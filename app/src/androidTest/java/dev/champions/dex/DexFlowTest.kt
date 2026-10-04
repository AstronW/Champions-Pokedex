package dev.champions.dex

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DexFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun search(text: String) { compose.onNode(hasSetTextAction()).performTextReplacement(text) }
    private fun position() = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun searchFormsNavigateMovesAndBack() {
        compose.waitForCatalog()
        search("喷火龙")
        compose.onNodeWithText("超级喷火龙Ｘ").assertExists()
        compose.onAllNodes(hasText("喷火龙") and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.onNodeWithTag("detail:pokemon:0006000").assertExists()
        compose.onNodeWithText("534").performScrollTo().assertExists()
        compose.onNode(hasText("招式 · ",substring=true) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("筛选 · 排序").performClick()
        compose.onNodeWithText("含未开放").performClick()
        compose.onNodeWithText("显示",substring=true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("火焰拳").performScrollTo().performClick()
        compose.onNodeWithText("招式详情").assertExists()
        compose.back()
        compose.onNodeWithText("火焰拳").assertIsDisplayed()
        compose.onNode(hasText("含未开放") and hasClickAction()).assertExists()
        compose.back()
        compose.onNode(hasSetTextAction()).assertTextContains("喷火龙")
    }

    @Test fun catalogScrollSurvivesDestinationSwitchAndRotation() {
        compose.waitForCatalog()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        val before=position()
        compose.navigate("招式"); compose.navigate("宝可梦")
        org.junit.Assert.assertEquals(before,position(),0.1f)
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        org.junit.Assert.assertEquals(before,position(),0.1f)
    }

    @Test fun landscapeKeepsCatalogVisible() {
        compose.waitForCatalog()
        compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithText("妙蛙花").assertIsDisplayed()
        compose.onNodeWithText("喷火龙").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test fun filteredCatalogRestoresScrollAndListFiltersReturnToTop() {
        compose.waitForCatalog()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("全部形态").performClick()
        compose.onNodeWithText("显示",substring=true).performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        val before=position()
        compose.navigate("招式"); compose.navigate("宝可梦")
        org.junit.Assert.assertEquals(before,position(),0.1f)
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        org.junit.Assert.assertEquals(before,position(),0.1f)
        for (destination in listOf("招式","特性")) {
            compose.navigate(destination)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
            search("a");compose.waitForIdle()
            org.junit.Assert.assertEquals(0f,position(),0.1f)
            search("不存在的查询")
            compose.onNodeWithText("暂时没有匹配结果").assertExists()
            compose.onNodeWithContentDescription("清空搜索").performClick()
            compose.waitForIdle()
            org.junit.Assert.assertEquals(0f,position(),0.1f)
        }
    }

    @Test fun favoriteAndThemeSurviveActivityRecreation() {
        compose.waitForCatalog();search("0006")
        compose.onAllNodesWithContentDescription("收藏")[0].performClick()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertTextContains("0006")
        compose.onAllNodesWithContentDescription("取消收藏")[0].assertExists()
        compose.navigate("设置与关于")
        compose.onNode(segment("暗色") and hasAnyAncestor(hasTestTag("detail:settings"))).performClick()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithText("让图鉴更合你意").assertExists()
        compose.back()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("仅看收藏").performClick()
        compose.onNodeWithText("显示",substring=true).performClick()
        compose.onNodeWithText("1 只").assertExists()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithText("没有找到符合条件的宝可梦").assertExists()
        compose.onNodeWithText("清除筛选").performClick()
    }

    @Test fun moveAndAbilityDetailsReadOffline() {
        compose.waitForCatalog()
        compose.navigate("招式")
        search("喷射火焰")
        compose.onNode(hasText("喷射火焰") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("90").assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("百科详解 · 52POKE"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("来源：52poke 神奇宝贝百科及条目贡献者"))
        compose.onNodeWithText("CC BY-NC-SA 3.0 ↗").assertExists()
        compose.back()
        compose.navigate("特性")
        search("茂盛")
        compose.onNode(hasText("茂盛") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("游戏说明 · CHAMPIONS").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("百科详解 · 52POKE"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("拥有此特性的宝可梦",substring=true))
        compose.onNodeWithText("隐藏特性").assertDoesNotExist()
    }
}
