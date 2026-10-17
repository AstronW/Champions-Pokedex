package dev.champions.dex

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TeamTest {
    private val gameDex by lazy { parseDex(JSONObject(File("src/main/assets/dex.json").readText()), Mechanics()) }

    private fun pokemon(
        id: String,
        types: List<Int>,
        ability: Int,
        form: String = "",
        formEnglish: String = "",
    ) = Pokemon(
        id,
        id.take(4).toInt(),
        id.takeLast(3).toInt(),
        "测试",
        "Test",
        form,
        formEnglish,
        types,
        List(6) {
            80
        },
        List(3) { ability },
        100,
        0,
        "",
        "",
    )

    private fun item(
        id: Int,
        english: String,
        holders: List<String> = emptyList(),
    ) = Item(id, english, english, "", "", id, holders)

    private fun move(
        id: Int,
        type: Int,
        category: Int = 1,
        power: Int = 90,
    ) = Move(id, "招式$id", "Move $id", "", true, type, category, power, 100, 10, 0, false)

    private val electric = pokemon("0001000", listOf(12), 26) // Levitate
    private val skarmory = pokemon("0002000", listOf(2, 8), 5)
    private val grass = pokemon("0003000", listOf(11), 65)
    private val ice = pokemon("0004000", listOf(14), 47) // Thick Fat
    private val normal = pokemon("0005000", listOf(0), 174) // Refrigerate
    private val lizard = pokemon("0006000", listOf(9, 2), 66)
    private val megaLizard = pokemon("0006001", listOf(9, 15), 312, "超级测试Ｘ", "Mega Test X") // Dragonize
    private val ghost = pokemon("0007000", listOf(7), 26)
    private val stone = item(660, "Testite X", listOf(lizard.id))
    private val hyperVoice = move(304, 0)
    private val seismicToss = move(69, 1, 0, 1)
    private val freezeDry = move(573, 14)
    private val weatherBall = move(311, 0, power = 50)
    private val protect = move(182, 0, 2, 0)
    private val dex =
        Dex(
            listOf(electric, skarmory, grass, ice, normal, lizard, megaLizard, ghost),
            listOf(hyperVoice, seismicToss, freezeDry, weatherBall, protect),
            listOf(Ability(174, "冰冻皮肤", "Refrigerate", ""), Ability(312, "龙皮肤", "Dragonize", "")),
            listOf(normal, lizard, megaLizard, ghost).associate { it.id to listOf(304, 69, 573, 311, 182) },
            emptyMap(),
            8,
            "",
            "",
            "",
            Mechanics(
                relations =
                    listOf(
                        CategoryRelation("声音类招式", "move:304", "member", ""),
                        CategoryRelation("固定伤害类招式", "move:69", "member", ""),
                    ),
            ),
            listOf(stone, item(541, "Air Balloon"), item(278, "Iron Ball"), item(184, "Occa Berry"), item(200, "Chilan Berry")),
        )

    private fun set(
        p: Pokemon,
        ability: Int = p.abilities.first(),
        item: Int? = null,
        moves: List<Int> = emptyList(),
    ) = MemberSet(dex, TeamMember(p.id, ability, item, moves))

    @Test fun aMegaFormIsFoundInTheTeamThroughItsHolderAndStone() {
        val team = listOf(null, dex.newTeamMember(megaLizard.id), dex.newTeamMember(grass.id))
        assertEquals(TeamMember(lizard.id, lizard.abilities.first(), stone.id), team[1])
        assertEquals(1, dex.teamSlotOf(team, megaLizard.id))
        assertEquals(2, dex.teamSlotOf(team, grass.id))
        assertEquals(-1, dex.teamSlotOf(team, lizard.id)) // The holder without its stone is another form.
    }

    @Test fun everyMegaFormIsReachedFromItsHolderAndStone() {
        val megas =
            gameDex.pokemon
                .filter { it.formEnglish.startsWith("Mega ") }
                .map { it.id }
                .toSet()
        assertEquals(megas, gameDex.megaSources.keys)
        assertEquals(megas.size, gameDex.megaForms.size)
        assertEquals("0006001", gameDex.megaForms.getValue("0006000" to 660).id) // Charizardite X
        assertEquals("0006002", gameDex.megaForms.getValue("0006000" to 678).id) // Charizardite Y
        assertEquals("0359002", gameDex.megaForms.getValue("0359000" to 2638).id) // Absolite Z
        assertEquals("0678002", gameDex.megaForms.getValue("0678000" to 2643).id) // Meowsticite, male
        assertEquals("0678003", gameDex.megaForms.getValue("0678001" to 2643).id) // Meowsticite, female
        assertEquals("0670006", gameDex.megaForms.getValue("0670005" to 2579).id) // Floettite on the Eternal Flower
        assertTrue(gameDex.megaForms.keys.none { it.second == 236 }) // Light Ball is held, not a Mega Stone
    }

    @Test fun storedTeamsKeepSetsAndReadTheOldIdList() {
        val legacy = decodeTeam(null, "0003000,,0006000")
        assertEquals(listOf(TeamMember("0003000", 0), null, TeamMember("0006000", 0), null, null, null), legacy)
        val team = listOf(TeamMember("0006000", 66, 660, listOf(53, 89), "spa", "atk"), null, null, null, null, TeamMember("0003000", 65))
        assertEquals(team, decodeTeam(encodeTeam(team), "0001000"))
    }

    @Test fun abilitiesAndItemsChangeDamageTaken() {
        assertEquals(0.0, set(electric).taken(4), 0.0) // Levitate
        assertEquals(2.0, set(electric, item = 278).taken(4), 0.0) // Iron Ball grounds it
        assertEquals(0.0, set(electric, ability = 5, item = 541).taken(4), 0.0) // Air Balloon
        assertEquals(0.0, set(skarmory).taken(4), 0.0)
        assertEquals(2.0, set(skarmory, item = 278).taken(4), 0.0) // Grounded Flying type takes Ground as Steel
        assertEquals(1.0, set(grass, item = 184).taken(9), 0.0) // Occa Berry halves a super-effective Fire hit
        assertEquals(.5, set(normal, item = 200).taken(0), 0.0) // Chilan Berry halves any Normal hit
        assertEquals(1.0, set(ice).taken(9), 0.0) // Thick Fat
        assertEquals(1.5, set(grass, ability = 111).taken(9), 0.0) // Filter
        assertEquals(1.25, set(normal, ability = 87).taken(9), 0.0) // Dry Skin
        assertEquals(0.0, set(normal, ability = 87).taken(10), 0.0)
        assertEquals(.25, set(lizard).taken(11), 0.0)
        assertEquals(2.0, set(lizard, item = 660).taken(15), 0.0) // Holding the stone means the Mega form's types
    }

    @Test fun abilitiesChangeMoveTypesForCoverage() {
        val refrigerate = set(normal, moves = listOf(304, 311, 182))
        assertEquals(listOf(14, 0), refrigerate.attacks.map { it.type }) // Status moves never count; Weather Ball keeps its own rule
        assertEquals("冰冻皮肤", refrigerate.attacks.first().cause)
        assertEquals(2.0, refrigerate.hits(15), 0.0)
        assertEquals(0, set(normal, ability = 70, moves = listOf(304)).attacks.single().type) // Without a skin ability the move stays Normal
        assertEquals(9, set(normal, ability = 70, moves = listOf(311)).attacks.single().type) // Drought makes Weather Ball Fire
        assertEquals(10, set(normal, ability = 204, moves = listOf(304)).attacks.single().type) // Liquid Voice turns sound moves into Water
        assertEquals(15, set(lizard, item = 660, moves = listOf(304)).attacks.single().type) // Dragonize from the Mega Stone
        assertEquals(0.0, set(normal, ability = 70, moves = listOf(304)).hits(7), 0.0)
        assertEquals(1.0, set(normal, ability = 113, moves = listOf(304)).hits(7), 0.0) // Scrappy
        assertEquals(2.0, set(normal, moves = listOf(573)).hits(10), 0.0) // Freeze-Dry
        val toss = set(normal, moves = listOf(69))
        assertEquals(1.0, toss.hits(0), 0.0)
        assertEquals(0.0, toss.hits(7), 0.0) // Fixed damage only checks immunity
        assertEquals(0.0, set(normal).hits(0), 0.0)
    }

    @Test fun changingFormKeepsWhatTheNewFormAllowsAndLeavingAMegaDropsItsStone() {
        val mega = TeamMember(lizard.id, 66, stone.id, listOf(304, 69))
        assertEquals(TeamMember(lizard.id, 66, stone.id, listOf(304, 69)), dex.withForm(mega.copy(item = null), megaLizard.id))
        assertEquals(TeamMember(lizard.id, 66, null, listOf(304, 69)), dex.withForm(mega, lizard.id))
        assertEquals(TeamMember(normal.id, 174, 541, emptyList()), dex.withForm(TeamMember(lizard.id, 66, 541, listOf(999)), normal.id))
    }

    @Test fun storedMembersAreFittedToTheirFormOrDroppedWhenTheFormIsGone() {
        assertEquals(
            TeamMember(normal.id, 174, null, listOf(304, 69, 573, 311)),
            dex.normalized(TeamMember(normal.id, 0, stone.id, listOf(304, 304, 69, 999, 573, 311, 182))),
        )
        assertNull(dex.normalized(TeamMember("9999000", 0)))
    }

    @Test fun multipliersReadAsFractionsOrShortDecimals() {
        assertEquals(
            listOf("×0", "×¼", "×½", "×1", "×1.25", "×1.5", "×2", "×4"),
            listOf(0.0, .25, .5, 1.0, 1.25, 1.5, 2.0, 4.0).map(::multiplierText),
        )
    }
}
