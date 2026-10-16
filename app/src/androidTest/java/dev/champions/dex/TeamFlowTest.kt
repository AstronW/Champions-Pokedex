package dev.champions.dex

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** Search the open option sheet and choose an entry. */
    private fun choose(query: String, option: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(query)
        Espresso.closeSoftKeyboard()
        compose.onAllNodes(hasText(option) and hasClickAction() and !hasSetTextAction())[0].performClick()
        compose.waitForIdle()
    }

    /** Team images this app saved to Pictures/ChampionsDex. */
    private fun savedImages(): List<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return compose.activity.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("champions-team-%", "Pictures/ChampionsDex%"), null)!!.use { c ->
            buildList { while (c.moveToNext()) add(ContentUris.withAppendedId(collection, c.getLong(0))) }
        }
    }

    private fun clearTeam() {
        while (compose.onAllNodesWithContentDescription("移除").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithContentDescription("移除")[0].performClick(); compose.waitForIdle()
        }
    }

    /** Adds [form] to the next empty slot (the [slot]-th member) and gives it [move] as its first move. */
    private fun addWithMove(query: String, form: String, move: String, slot: Int) {
        compose.onAllNodes(hasText("添加") and hasClickAction())[0].performClick(); choose(query, form)
        compose.onAllNodesWithTag("team-slot")[slot].performClick()
        compose.onNodeWithTag("team-move-0").performClick(); choose(move, move)
        compose.onNodeWithText("完成").performClick()
    }

    @Test fun shareImageShowsEveryMoveAtOneSizeWithoutCuttingTheLongestNames() {
        compose.waitForCatalog()
        compose.navigate("队伍构建"); clearTeam()
        addWithMove("西狮海壬", "西狮海壬", "泡影的咏叹调", 0)
        addWithMove("大剑鬼", "大剑鬼 · 洗翠的样子", "秘剑・千重涛", 1)
        compose.onNode(hasText("分享") and hasClickAction()).performClick()
        val layouts = listOf("泡影的咏叹调", "秘剑・千重涛").map { name ->
            val node = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("team-share-image")), useUnmergedTree = true).fetchSemanticsNode()
            mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        }
        // A one-line ellipsis layout reports width "overflow" whenever it is narrower than its cell, so check what is shown instead.
        layouts.forEach {
            val name = it.layoutInput.text.text
            assertTrue("$name: ${it.multiParagraph.getLineWidth(0)} px in ${it.layoutInput.constraints.maxWidth} px",
                it.lineCount == 1 && !it.isLineEllipsized(0) && it.getLineEnd(0, visibleEnd = true) == name.length)
        }
        assertEquals(1, layouts.map { it.layoutInput.style.fontSize }.distinct().size)
        compose.back(); clearTeam()
    }

    @Test fun memberSetDrivesMegaFormAndMoveTypeAndMenuClosesAfterLeaving() {
        compose.waitForCatalog()
        compose.navigate("队伍构建"); clearTeam()
        compose.onAllNodes(hasText("添加") and hasClickAction())[0].performClick()
        choose("冰鬼护", "冰鬼护")
        compose.onNodeWithTag("team-slot").performClick()
        compose.onNodeWithTag("menu-sheet").assertExists()
        compose.onNodeWithTag("team-item").performClick(); choose("冰鬼护进化石", "冰鬼护进化石")
        compose.onNodeWithText("携带超级进化石，按超级进化后的属性与特性分析。").assertExists()
        compose.onNodeWithTag("team-move-0").performClick(); choose("泰山压顶", "泰山压顶")
        compose.onNodeWithText("→").assertExists()                               // Refrigerate turns the Normal move into Ice
        compose.onAllNodesWithText("冰冻皮肤").assertCountEquals(2)
        compose.onNodeWithText("完成").performClick()

        compose.onNode(hasText("分享") and hasClickAction()).performClick()
        compose.onNodeWithTag("team-share-image").assertExists()
        compose.onNodeWithText("保存到本地").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("已保存到相册", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val saved = savedImages()
        assertTrue(saved.isNotEmpty())
        saved.forEach { compose.activity.contentResolver.delete(it, null, null) }
        compose.back()

        compose.navigate("速度线"); compose.navigate("队伍构建")
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        compose.onNodeWithTag("team-slot").assertTextContains("超级冰鬼护")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("team-slot").performClick()
        compose.onNodeWithTag("team-move-0").assertTextContains("泰山压顶")
        compose.back()
        compose.onNodeWithTag("menu-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("移除").performClick()
    }
}
