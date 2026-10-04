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

    @Test fun changingBackToAnAttackerSelectsAnAvailableLearnedMove() {
        assertEquals(flame.id, calculatorMoveFor(dex,charizard.id,null))
        assertEquals(flame.id, calculatorMoveFor(dex,charizard.id,closed.id))
        assertEquals(flame.id, JSONObject(defaultCalculation(dex,null)).getInt("move"))
    }

    @Test fun regionalAndMegaLabelsKeepChineseSpeciesSearchable() {
        val regional = ditto.copy(name="雷丘",form="阿罗拉的样子")
        val mega = ditto.copy(name="妙蛙花",form="超级妙蛙花")
        assertEquals("雷丘 · 阿罗拉的样子",regional.displayName)
        assertTrue(regional.displayName.matchesQuery("雷丘"))
        assertEquals("超级妙蛙花",mega.displayName)
        assertEquals("百变怪",ditto.displayName)
    }
}
