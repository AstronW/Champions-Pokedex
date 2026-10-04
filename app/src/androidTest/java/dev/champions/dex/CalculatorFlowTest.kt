package dev.champions.dex

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
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
    private fun scrollTo(matcher: SemanticsMatcher) = compose.onNodeWithTag("calculator-list").performScrollToNode(matcher)
    private fun top() = compose.onNodeWithTag("calculator-list").performScrollToIndex(0)
    /** Pick an option from a select sheet by searching for it. */
    private fun pick(tag: String, query: String, option: String) {
        scrollTo(hasTestTag(tag)); compose.onNodeWithTag(tag).performClick()
        compose.onNode(hasSetTextAction() and hasText("搜索名称、英文或编号")).performTextReplacement(query)
        compose.onNode(hasText(option) and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitForIdle()
    }

    @Test fun weatherChangesDamageAndSurvivesNavigationRecreationAndRotation() {
        val sunny = ready()
        scrollTo(hasText("下雨")); compose.onNode(segment("下雨")).performClick()
        val rain = waitForDamage { it != sunny }
        compose.navigate("招式"); compose.navigate("伤害计算")
        waitForDamage { it == rain }
        compose.activityRule.scenario.recreate()
        waitForDamage { it == rain }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        waitForDamage { it == rain }
        compose.onNodeWithTag("calc-damage").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test fun formSegmentAndDetailReturnKeepCalculation() {
        ready()
        compose.onNode(segment("超级喷火龙Ｘ")).performClick()
        compose.onNodeWithText("超级喷火龙Ｘ → 妙蛙花").assertExists()
        top(); compose.onAllNodesWithContentDescription("查看图鉴")[0].performClick()
        compose.onNodeWithTag("detail:pokemon:0006001").assertExists()
        compose.onNodeWithText("用于伤害计算").performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("calc-damage").fetchSemanticsNodes().isNotEmpty() }
        compose.back(); compose.back()
        compose.onNodeWithText("超级喷火龙Ｘ → 妙蛙花").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("超级喷火龙Ｘ → 妙蛙花").assertExists()
    }

    @Test fun pointSliderRespectsTheSixtySixPointTotal() {
        ready()
        compose.onNodeWithText("合计 66 / 66").assertExists()
        compose.onNodeWithContentDescription("特攻 能力点").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("合计 34 / 66").assertExists()
    }

    @Test fun invalidPointsCannotLeaveStaleResult() {
        ready()
        scrollTo(hasText("更多条件")); compose.onNodeWithText("更多条件").performClick()
        scrollTo(hasContentDescription("特攻能力点"))
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
        top(); compose.onAllNodesWithContentDescription("查看图鉴")[0].performClick()
        compose.onNodeWithText("用于伤害计算").performClick()
        compose.onNodeWithTag("calc-no-move").assertExists()
        scrollTo(hasText("全部开放攻击招式")); compose.onNodeWithText("全部开放攻击招式").performClick()
        pick("pick-move", "喷射火焰", "喷射火焰")
        waitForDamage()
        compose.onNodeWithTag("calc-no-move").assertDoesNotExist()
        scrollTo(hasText("全部开放攻击招式")); compose.onNodeWithText("全部开放攻击招式").performClick()
        compose.onNodeWithTag("calc-no-move").assertExists()
        compose.onNodeWithTag("calc-damage").assertDoesNotExist()
    }

    @Test fun chineseSpeciesSearchIncludesRegionalForms() {
        val before = ready()
        pick("pick-attacker", "雷丘", "雷丘 · 阿罗拉的样子")
        compose.onNodeWithText("雷丘 · 阿罗拉的样子 → 妙蛙花").assertExists()
        assertNotEquals(before, waitForDamage { it != before })
    }
}
