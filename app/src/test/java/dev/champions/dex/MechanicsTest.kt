package dev.champions.dex

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MechanicsTest {
    private val mechanics by lazy { parseMechanics(JSONObject(File("src/main/assets/mechanics.json").readText())) }

    @Test fun flagsKeepFalseUnknownAndTemplateDefaultsSeparate() {
        val flags = mechanics.flags.values.flatten()
        assertTrue(flags.any { it.value == null })
        assertTrue(flags.any { it.value == false })
        assertTrue(flags.any { it.origin == "template_default" })
        assertTrue(flags.all { it.field in SpecialFlagLabels })
        assertTrue(flags.all { it.displayText.isNotBlank() && !it.displayText.startsWith("（") })
        assertEquals("否（百科默认）", mechanics.flags.getValue("ability:89").first { it.field == "once_per_entry" }.displayText)
        assertEquals(true, mechanics.flags.getValue("move:515").first { it.field == "contact" }.value)
        assertTrue(mechanics.issues.containsKey("move:515"))
        assertEquals("未知", mechanics.targets.getValue("move:47").text)
        assertEquals("", mechanics.targets.getValue("move:47").wikiCode)
    }

    @Test fun categoryMembersAndRelatedAbilitiesRemainDistinct() {
        val punching = mechanics.categories.first { it.name == "拳击类" }
        val relations = mechanics.relationsByCategory.getValue(punching.id)
        assertTrue(relations.any { it.entityKey == "move:7" && it.relation == "member" })
        assertTrue(relations.any { it.entityKey == "ability:89" && it.relation == "related_ability" })
        assertTrue(relations.any { it.relation == "section_reference" })
    }

    @Test fun statusAliasesAndGameDescriptionsAreRetained() {
        val poison = mechanics.statuses.first { it.name == "中毒（状态）" }
        assertTrue(SearchQuery("剧毒").matches(poison.searchText))
        assertTrue(poison.game.any { it.name == "剧毒" })
        val paralysis = mechanics.statuses.first { it.name == "麻痹（状态）" }
        assertTrue(paralysis.game.any { "12.5%" in it.description })
        assertEquals("影响对战", mechanics.statuses.first { it.name == "下雨（天气）" }.section)
    }

    @Test fun referencesResolveAndDoNotPromoteHistoricalMentions() {
        assertTrue(mechanics.references.all { it.bodyKey in mechanics.statusesByBody })
        assertFalse(StatusReference("a", "move:1", "page_original", "招式变更 / 第一世代").inDescription)
        assertTrue(StatusReference("a", "move:1", "game_description", "游戏说明").inDescription)
        assertTrue(mechanics.referencesByEntity.getValue("ability:1").any { it.inDescription && mechanics.statusesByBody[it.bodyKey]?.name == "畏缩（状态）" })
    }
}
