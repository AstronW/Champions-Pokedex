package dev.champions.dex

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DexFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun search(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    private fun position() =
        compose
            .onNode(hasScrollToIndexAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    @Test fun searchFormsNavigateMovesAndBack() {
        compose.waitForCatalog()
        search("喷火龙")
        compose.onNodeWithText("超级喷火龙Ｘ").assertExists()
        compose.onAllNodes(hasText("喷火龙") and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.onNodeWithTag("detail:pokemon:0006000").assertExists()
        compose.onNodeWithText("534").performScrollTo().assertExists()
        compose.onNode(hasText("招式 · ", substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("筛选 · 排序").performClick()
        compose.onNodeWithText("含未开放").performClick()
        compose.onNodeWithText("显示", substring = true).performClick()
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
        val before = position()
        compose.navigate("招式")
        compose.navigate("宝可梦")
        org.junit.Assert.assertEquals(before, position(), 0.1f)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(before, position(), 0.1f)
    }
}
