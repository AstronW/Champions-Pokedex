package dev.champions.dex

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule

typealias DexRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

fun DexRule.waitForCatalog() = waitUntil(15000) { onAllNodesWithText("全国图鉴", substring = true).fetchSemanticsNodes().isNotEmpty() }

/** Opens a drawer destination by its label. */
fun DexRule.navigate(label: String) {
    onNodeWithContentDescription("导航").performClick()
    onNode(hasText(label) and hasClickAction()).performClick()
    waitForIdle()
}

fun DexRule.back() {
    activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

fun hasTagPrefix(prefix: String) =
    SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

fun segment(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
