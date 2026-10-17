package dev.champions.dex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WikiParserTest {
    private fun text(blocks: List<WikiBlock>): String =
        blocks.joinToString("") { b ->
            when (b) {
                is WikiBlock.Text -> b.runs.joinToString("") { it.text }
                is WikiBlock.Heading -> b.runs.joinToString("") { it.text }
                is WikiBlock.Table -> b.rows.joinToString("") { r -> r.joinToString("") { text(it.blocks) } }
            }
        }

    // Both ordinary spaces and nonbreaking spaces can be trimmed at table-cell boundaries.
    private fun squash(s: String) = s.filterNot { it.isWhitespace() }

    @Test fun parsesTablesLinksAndInlineStyles() {
        val blocks =
            parseWiki(
                "<h2>效果</h2>\n<b>粗</b>与<a href=\"dex://entity/move:1\">拍击</a><sup>[1]</sup>&lt;注&gt;\n<table><tr><th colspan=\"2\">表头\n</th></tr><tr><td>甲\n</td><td>乙<table><tr><td>内</td><td>外</td></tr></table></td></tr></table><table><tr><td>仅包装</td></tr></table>尾",
            )
        assertEquals("效果", (blocks[0] as WikiBlock.Heading).runs.joinToString("") { it.text })
        val body = (blocks[1] as WikiBlock.Text).runs
        assertTrue(body.first { it.text == "粗" }.bold)
        assertEquals("dex://entity/move:1", body.first { it.text == "拍击" }.href)
        assertTrue(body.first { it.text == "[1]" }.sup)
        assertTrue(body.any { it.text.contains("<注>") })
        val table = blocks[2] as WikiBlock.Table
        assertEquals(2, table.rows.size)
        assertEquals(2, table.rows[0][0].span)
        assertTrue(table.rows[0][0].header)
        assertTrue(table.rows[1][1].blocks.any { it is WikiBlock.Table })
        // A one-cell layout table is unwrapped to its content.
        assertEquals("仅包装", (blocks[3] as WikiBlock.Text).runs.single().text)
        assertEquals("尾", (blocks[4] as WikiBlock.Text).runs.single().text)
    }

    /** Every bundled article keeps all of its text after parsing. */
    @Test fun everyBundledArticleKeepsItsText() {
        val bodies = JSONObject(File("src/main/assets/wiki.json").readText())
        assertTrue(bodies.length() > 700)
        bodies.keys().forEach { key ->
            val html = bodies.getString(key)
            val expected = squash(decodeEntities(html.replace(Regex("<[^>]+>"), "")))
            assertEquals(key, expected, squash(text(parseWiki(html))))
        }
    }
}
