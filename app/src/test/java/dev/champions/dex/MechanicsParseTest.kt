package dev.champions.dex

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Fixes what the bundled mechanics.json parses into, so its readers can be refactored safely. */
class MechanicsParseTest {
    private val raw = JSONObject(File("src/main/assets/mechanics.json").readText())
    private val mechanics = parseMechanics(raw)

    @Test fun everyRecordIsReadAndOnlyExactDuplicateReferencesAreMerged() {
        assertEquals(raw.getJSONArray("categories").length(), mechanics.categories.size)
        assertEquals(raw.getJSONArray("relations").length(), mechanics.relations.size)
        assertEquals(raw.getJSONArray("flags").length(), mechanics.flags.values.sumOf { it.size })
        assertEquals(raw.getJSONArray("targets").length(), mechanics.targets.size)
        assertEquals(raw.getJSONArray("statuses").length(), mechanics.statuses.size)
        assertEquals(raw.getJSONArray("issues").length(), mechanics.issues.values.sumOf { it.size })
        // The export lists some references once per paragraph; identical ones collapse to one.
        assertTrue(mechanics.references.size < raw.getJSONArray("references").length())
        assertEquals(mechanics.references.distinct(), mechanics.references)
    }

    @Test fun unspecifiedFlagsStayUnknownInsteadOfFalse() {
        val moldBreaker = mechanics.flags.getValue("ability:1").single { it.field == "mold_breaker" }
        assertNull(moldBreaker.value)
        assertEquals("百科未标明", moldBreaker.displayText)
        assertEquals(true, mechanics.flags.getValue("ability:1").single { it.field == "skill_swap" }.value)
    }

    @Test fun statusesKeepTheirAliasesAndGameTexts() {
        val paralysis = mechanics.statusesByKey.getValue("status:wiki-10660")
        assertEquals("麻痹", paralysis.displayName)
        assertEquals(listOf("麻痹", "麻痹（状态）"), paralysis.aliases)
        assertEquals(listOf("Paralyzed"), paralysis.game.map { it.english })
        assertSame(paralysis, mechanics.statusesByBody.getValue("wiki-page:10660"))
    }

    @Test fun categoriesAndTargetsKeepTheirSources() {
        val selfFaint = mechanics.categoriesById.getValue("造成自身陷入濒死状态的招式")
        assertEquals("自爆类", selfFaint.name)
        assertEquals("2562235", selfFaint.revision)
        assertEquals("https://wiki.52poke.com/index.php?oldid=2562235", selfFaint.url)
        assertEquals(MoveTarget("除自身以外场上一只可以攻击到的宝可梦", "2", "0"), mechanics.targets.getValue("move:1"))
        assertEquals(listOf("未识别 Wiki 目标："), mechanics.issues.getValue("move:47"))
    }
}
