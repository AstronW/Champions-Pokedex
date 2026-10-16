package dev.champions.dex

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun damageText() = compose.onAllNodesWithTag("calc-damage").fetchSemanticsNodes().firstOrNull()
        ?.config?.get(SemanticsProperties.Text)?.joinToString()
    private fun waitForDamage(predicate: (String) -> Boolean = { true }): String {
        compose.waitUntil(20000) { damageText()?.let(predicate) == true }
        return damageText()!!
    }
    private fun ready(): String { compose.waitForCatalog(); compose.navigate("伤害计算"); return waitForDamage() }
    /** Open a second-level menu from its summary on the calculator page. */
    private fun open(tag: String) {
        compose.onNodeWithTag("calculator-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("menu-sheet").assertExists()
    }
    private fun done() { compose.onNodeWithText("完成").performClick(); compose.onNodeWithTag("menu-sheet").assertDoesNotExist() }
    /**
     * Search the open picker menu and choose an entry; choosing closes the menu. The keyboard opens a moment after typing and
     * scrolls the field back into view, so it is closed first and the entry scrolled to.
     */
    private fun choose(query: String, option: String) {
        compose.onNode(hasSetTextAction() and hasText("搜索名称、英文或编号")).performTextReplacement(query)
        Espresso.closeSoftKeyboard()
        val entry = hasText(option) and hasClickAction() and !hasSetTextAction()
        compose.onNodeWithTag("menu-list").performScrollToNode(entry)
        compose.onNode(entry).performClick()
        compose.waitForIdle()
    }
    private fun pick(tag: String, query: String, option: String) { open(tag); choose(query, option) }
    private fun title(text: String) = compose.onNodeWithText(text, substring = true)

    @Test fun weatherChangesDamageAndSurvivesNavigationRecreationAndRotation() {
        val sunny = ready()
        open("open-field"); compose.onNode(segment("下雨")).performClick()
        val rain = waitForDamage { it != sunny }
        done()
        compose.navigate("速度线"); compose.navigate("伤害计算")
        waitForDamage { it == rain }
        compose.activityRule.scenario.recreate()
        waitForDamage { it == rain }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        waitForDamage { it == rain }
        compose.onNodeWithTag("calc-damage").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test fun openMenuSurvivesRecreationAndBackClosesIt() {
        ready()
        open("config-attacker")
        // The menu rises over the result card instead of ending behind it.
        assertTrue(compose.onNodeWithTag("menu-sheet").getUnclippedBoundsInRoot().bottom >= compose.onNodeWithTag("calc-damage").getUnclippedBoundsInRoot().bottom)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("menu-sheet").assertExists()
        compose.back()
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        compose.onNodeWithTag("calculator-list").assertIsDisplayed()
        waitForDamage(); compose.onNodeWithTag("calc-damage").assertIsDisplayed()
    }

    @Test fun menuClosesAfterLeavingThePageAndWhenDraggedDown() {
        ready()
        open("open-field")
        compose.navigate("速度线"); compose.navigate("伤害计算")
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        compose.onNodeWithText("伤害分布").performClick()
        compose.onNodeWithText("计算内核与开源许可").performScrollTo().performClick()
        compose.onNodeWithTag("detail:calculator-license").assertExists()
        compose.back()
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        open("pick-move")
        compose.onNodeWithContentDescription("下拉关闭").performTouchInput { swipeDown(endY = centerY + 400f) }
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        open("pick-move")
        compose.onNodeWithContentDescription("下拉关闭").performTouchInput { swipeDown(endY = centerY + 20f, durationMillis = 800) }
        compose.onNodeWithTag("menu-sheet").assertExists()
    }

    @Test fun formSegmentAndDetailReturnKeepCalculation() {
        ready()
        open("pick-attacker"); compose.onNode(segment("超级喷火龙Ｘ")).performClick(); done()
        title("超级喷火龙Ｘ → 妙蛙花").assertExists()
        open("pick-attacker"); compose.onNodeWithText("查看图鉴").performClick()
        compose.onNodeWithTag("detail:pokemon:0006001").assertExists()
        compose.onNodeWithText("用于伤害计算").performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("calc-damage").fetchSemanticsNodes().isNotEmpty() }
        compose.back(); compose.back()
        title("超级喷火龙Ｘ → 妙蛙花").assertExists()
        compose.activityRule.scenario.recreate()
        title("超级喷火龙Ｘ → 妙蛙花").assertExists()
    }

    @Test fun pointsRespectTheSixtySixPointTotal() {
        ready()
        open("config-attacker"); compose.onNode(segment("能力点")).performClick()
        compose.onNodeWithText("合计 66 / 66").assertExists()
        compose.onNodeWithContentDescription("增加特攻能力点").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasContentDescription("特攻能力点")).performTextReplacement("0")
        // The keyboard stays open and shrinks the menu, as it would for a user: scroll to the total and to the button.
        compose.onNodeWithTag("menu-list").performScrollToNode(hasText("合计 34 / 66"))
        compose.onNodeWithContentDescription("增加特攻能力点").performScrollTo().performClick()
        compose.onNodeWithTag("menu-list").performScrollToNode(hasText("合计 35 / 66"))
    }

    @Test fun invalidPointsCannotLeaveStaleResult() {
        ready()
        open("config-attacker"); compose.onNode(segment("能力点")).performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("特攻能力点")).performTextReplacement("33")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("calc-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calc-damage").assertDoesNotExist()
        compose.onNodeWithTag("calc-error").assertTextContains("能力点须为 0～32 的整数")
    }

    @Test fun dittoSelectionAndDetailEntryDoNotRequireAnAttack() {
        ready()
        pick("pick-attacker", "百变怪", "百变怪")
        compose.onNodeWithTag("calc-no-move").assertExists()
        compose.onNodeWithTag("calc-damage").assertDoesNotExist()
        open("pick-attacker"); compose.onNodeWithText("查看图鉴").performClick()
        compose.onNodeWithText("用于伤害计算").performClick()
        compose.onNodeWithTag("calc-no-move").assertExists()
        open("pick-move"); compose.onNodeWithText("全部开放攻击招式").performClick()
        choose("喷射火焰", "喷射火焰")
        waitForDamage()
        compose.onNodeWithTag("calc-no-move").assertDoesNotExist()
        open("pick-move"); compose.onNodeWithText("全部开放攻击招式").performClick(); done()
        compose.onNodeWithTag("calc-no-move").assertExists()
        compose.onNodeWithTag("calc-damage").assertDoesNotExist()
    }

    @Test fun chineseSpeciesSearchIncludesRegionalForms() {
        val before = ready()
        pick("pick-attacker", "雷丘", "雷丘 · 阿罗拉的样子")
        title("雷丘 · 阿罗拉的样子 → 妙蛙花").assertExists()
        assertNotEquals(before, waitForDamage { it != before })
    }
}
