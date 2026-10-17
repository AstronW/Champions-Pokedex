package dev.champions.dex

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun model(): DexViewModel {
        lateinit var model: DexViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[DexViewModel::class.java] }
        return model
    }

    /** The team is stored with the app's preferences, so every test starts from an empty one. */
    private fun ready() {
        compose.waitForCatalog()
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[DexViewModel::class.java]
            repeat(TEAM_SIZE) { model.setTeamMember(it, null) }
        }
        compose.waitForIdle()
    }

    private fun search(text: String) = compose.onNode(hasSetTextAction()).performTextReplacement(text)

    private val megaX = "超级喷火龙Ｘ"

    @Test fun aMemberHoldingItsStoneIsAnalysedAsTheMegaFormAndKeptAfterRecreation() {
        ready()
        compose.navigate("队伍构建")
        compose.onAllNodes(hasText("添加") and hasClickAction())[0].performClick()
        search("喷火龙")
        compose.onAllNodes(hasText("喷火龙") and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.onNodeWithTag("team-slot").performClick()
        compose.onNodeWithTag("menu-sheet").assertExists()
        compose.onNodeWithTag("team-item").performClick()
        search("charizardite x")
        compose.onAllNodes(hasText("Charizardite X") and hasClickAction())[0].performClick()
        compose.onNodeWithText("携带超级进化石，按超级进化后的属性与特性分析。").assertExists()
        assertEquals(TeamMember("0006000", model().team[0]!!.ability, 660), model().team[0]!!.copy(moves = emptyList(), up = "", down = ""))

        // Recreation keeps the open menu, as rotation and theme changes do.
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("携带超级进化石，按超级进化后的属性与特性分析。").assertExists()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        compose.onNodeWithTag("team-slot").assert(hasText(megaX, substring = true))
        assertEquals(1, model().team.count { it != null })
    }

    @Test fun addingAMegaFormFromItsDetailReportsItsSlotAndNeverFillsASecondOne() {
        ready()
        search("喷火龙")
        compose.onAllNodes(hasText("喷火龙") and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.onNode(segment(megaX)).performClick()
        compose.waitForIdle()
        // The toast fades out after 1.6 s, so the clock is held while it is read.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("加入队伍").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("已加入队伍第 1 位").assertExists()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("加入队伍").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("${megaX}已在队伍第 1 位").assertExists()
        compose.mainClock.autoAdvance = true
        assertEquals(listOf(TeamMember("0006000", model().team[0]!!.ability, 660)), model().team.filterNotNull())
    }
}
