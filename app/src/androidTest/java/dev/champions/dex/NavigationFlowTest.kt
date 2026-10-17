package dev.champions.dex

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
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
}
