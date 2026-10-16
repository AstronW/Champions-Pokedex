package dev.champions.dex

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CalculatorInputTest {
    private val ditto = Pokemon("0132000",132,0,"百变怪","Ditto","","",listOf(0),List(6){48},listOf(7,7,150),40,2,"","")
    private val charizard = ditto.copy(id="0006000",no=6,name="喷火龙",english="Charizard")
    private val flame = Move(53,"喷射火焰","Flamethrower","",true,9,1,90,100,15,0,false)
    private val transform = flame.copy(id=144,name="变身",category=2)
    private val closed = flame.copy(id=999,available=false)
    private val dex = Dex(listOf(charizard,ditto),listOf(flame,transform,closed),emptyList(),
        mapOf(ditto.id to listOf(transform.id),charizard.id to listOf(closed.id,flame.id)),emptyMap(),2,"","","")

    @Test fun dittoEntryKeepsThePokemonWithoutInventingAnAttack() {
        val request = JSONObject(defaultCalculation(dex,ditto.id))
        assertEquals(ditto.id, request.getJSONObject("attacker").getString("id"))
        assertTrue(request.isNull("move"))
        assertNull(calculatorMoveFor(dex,ditto.id,flame.id))
    }

    @Test fun formSelectionKeepsTheSpreadAndConditionsButRefreshesFormSpecificFields() {
        val mega = charizard.copy(id="0006001", formId=1, abilities=listOf(181), sex=3)
        val withMega = dex.copy(pokemon=dex.pokemon + mega, learnsets=dex.learnsets + (mega.id to listOf(flame.id)))
        val request = JSONObject(defaultCalculation(withMega, charizard.id)).put("hits", 3)
        val side = request.getJSONObject("attacker")
            .put("item", "Life Orb").put("hp", "75").put("status", "brn")
            .put("up", "spa").put("down", "atk").put("abilityOn", true).put("alliesFainted", "2")
        side.getJSONArray("boosts").put(3, 2)
        val preserved = listOf("sp", "boosts", "nature", "up", "down", "item", "hp", "status", "alliesFainted")
            .associateWith { side.get(it).toString() }

        selectCalculatorPokemon(request, withMega, "attacker", mega.id, preserveSpread=true, allMoves=false)

        preserved.forEach { (key, value) -> assertEquals(key, value, side.get(key).toString()) }
        assertEquals(mega.id, side.getString("id"))
        assertEquals(181, side.getInt("ability"))
        assertEquals("F", side.getString("gender"))
        assertFalse(side.getBoolean("abilityOn"))
        assertEquals(flame.id, request.getInt("move"))
        assertEquals(3, request.getInt("hits"))
    }

    @Test fun swappingTwiceRestoresPokemonAndTheirSideConditions() {
        val request = JSONObject(defaultCalculation(dex, charizard.id))
        val field = request.getJSONObject("field").put("weather", "Rain")
            .put("attackerSide", JSONObject().put("isHelpingHand", true))
            .put("defenderSide", JSONObject().put("isLightScreen", true))
        val before = request.toString()
        swapCalculatorSides(request)
        assertEquals(ditto.id, request.getJSONObject("attacker").getString("id"))
        assertEquals(charizard.id, request.getJSONObject("defender").getString("id"))
        assertTrue(field.getJSONObject("attackerSide").getBoolean("isLightScreen"))
        assertTrue(field.getJSONObject("defenderSide").getBoolean("isHelpingHand"))
        assertEquals("Rain", field.getString("weather"))
        swapCalculatorSides(request)
        assertEquals(before, request.toString())
    }
}
