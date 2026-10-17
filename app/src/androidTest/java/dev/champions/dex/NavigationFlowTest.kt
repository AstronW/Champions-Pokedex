package dev.champions.dex

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun motion(reduced: Boolean) =
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[DexViewModel::class.java].changeMotion(reduced)
        }

    private fun openVenusaur() {
        compose.onAllNodes(hasText("妙蛙花") and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.onNodeWithTag("detail:pokemon:0003000").assertExists()
    }

    private fun ready() {
        compose.waitForCatalog()
        motion(false)
        compose.onNode(hasSetTextAction()).performTextReplacement("0003")
        openVenusaur()
    }

    private val abilityRow = hasText("茂盛") and hasClickAction()

    private fun ability() = compose.onNode(abilityRow)

    private fun scrollToAbility() = compose.onNodeWithTag("pokemon-detail-list").performScrollToNode(abilityRow)

    @Test fun formSwitchStaysOnTheSameEntryAndReturnsToCatalogOnce() {
        ready()
        compose.onNodeWithText("☆ 异色").performClick()
        repeat(4) {
            compose.onNode(segment("超级妙蛙花")).performClick()
            compose.onNode(segment("妙蛙花")).performClick()
            compose.onNodeWithText("★ 异色").assertExists()
            compose.onNodeWithTag("detail:pokemon:0003000").assertExists()
        }
        compose.onNode(segment("超级妙蛙花")).performClick()
        compose.onNodeWithText("+100").performScrollTo().assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("★ 异色").assertExists()
        compose.onNodeWithText("+100").assertExists()
        compose.back()
        compose.onNodeWithText("全国图鉴", substring = true).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("0003")
    }

    @Test fun returningDuringEntryAnimationKeepsTheOriginalState() {
        ready()
        compose.onNodeWithText("☆ 异色").performClick()
        scrollToAbility()
        compose.mainClock.autoAdvance = false
        try {
            ability().performClick()
            compose.mainClock.advanceTimeBy(48)
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(500)
            compose.onNode(hasTagPrefix("detail:ability:")).assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithTag("pokemon-detail-list").performScrollToIndex(0)
        compose.onNodeWithText("★ 异色").assertExists()
        compose.back()
        compose.onNodeWithText("全国图鉴", substring = true).assertIsDisplayed()
    }

    /** Taps the scrim at the right edge; its centre lies under the drawer sheet. */
    private fun tapScrim() = compose.onNodeWithContentDescription("关闭导航").performTouchInput { click(centerRight - Offset(24f, 0f)) }

    private fun drawerLeft() = compose.onNodeWithText("对战图鉴").getBoundsInRoot().left

    private fun sheetTop() = compose.onNodeWithText("筛选宝可梦").getBoundsInRoot().top

    @Test fun scrimClosesDrawerWhileItIsStillOpening() {
        compose.waitForCatalog()
        motion(false)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("导航").performClick()
            compose.mainClock.advanceTimeBy(80)
            assert(drawerLeft() < 0.dp) { "drawer should still be sliding in" }
            tapScrim()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription("关闭导航").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("全国图鉴", substring = true).assertIsDisplayed()
    }

    @Test fun reducedMotionShowsDrawerAndSheetWithoutSliding() {
        compose.waitForCatalog()
        motion(true)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("导航").performClick()
            compose.mainClock.advanceTimeBy(48)
            val opened = drawerLeft()
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(drawerLeft(), opened)
            tapScrim()
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithContentDescription("关闭导航").assertDoesNotExist()
            compose.onNodeWithContentDescription("筛选").performClick()
            compose.mainClock.advanceTimeBy(48)
            val early = sheetTop()
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(sheetTop(), early)
        } finally {
            compose.mainClock.autoAdvance = true
            motion(false)
        }
        compose.back()
        compose.onNodeWithText("筛选宝可梦").assertDoesNotExist()
    }
}
