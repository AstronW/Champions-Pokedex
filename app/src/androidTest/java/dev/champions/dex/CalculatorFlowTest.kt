package dev.champions.dex

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun invalidPointsCannotLeaveStaleResult() {
        ready()
        open("config-attacker"); compose.onNode(segment("能力点")).performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("特攻能力点")).performTextReplacement("33")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("calc-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calc-damage").assertDoesNotExist()
        compose.onNodeWithTag("calc-error").assertTextContains("能力点须为 0～32 的整数")
    }
}
