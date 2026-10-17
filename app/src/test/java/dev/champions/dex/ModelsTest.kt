package dev.champions.dex

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    private fun pokemon(id:String,no:Int,formId:Int,name:String,form:String,types:List<Int>,speed:Int)=
        Pokemon(id,no,formId,name,"Example",form,"",types,listOf(80,80,80,80,80,speed),listOf(1,1,2),100,0,"","")
    private val regular=pokemon("0479000",479,0,"洛托姆","洛托姆",listOf(12,7),91)
    private val heat=pokemon("0479001",479,1,"洛托姆","加热洛托姆",listOf(12,9),86)
    private val charizard=pokemon("0006000",6,0,"喷火龙","",listOf(9,2),100)
    private val dex=Dex(listOf(charizard,regular,heat),emptyList(),emptyList(),emptyMap(),emptyMap(),2,"","","")

    @Test fun typeFilterFindsAlternateFormsInSpeciesMode() {
        val result=filterPokemon(dex,"",9,false,false,emptySet(),SortOrder.NUMBER)
        assertEquals(listOf(charizard,heat),result)
    }
    @Test fun moveSentinelsAreNotDisplayedAsOrdinaryValues() {
        val dynamic=Move(1,"","","",true,0,0,1,101,8,-2,false)
        assertEquals("变化",dynamic.powerLabel);assertEquals("—",dynamic.accuracyLabel)
        assertEquals("40",dynamic.copy(power=40).powerLabel)
        assertEquals("100%",dynamic.copy(accuracy=100).accuracyLabel)
    }

    @Test fun searchAcceptsWhitespaceAndRequiresEveryTermAcrossTheSameRecord() {
        val search = SearchQuery("  EXAMPLE\t0006\n")
        assertTrue(search.matches(charizard.searchText))
        assertFalse(search.matches(heat.searchText))
        assertTrue(SearchQuery("\t  \n").matches(""))
        assertTrue(SearchQuery("洛托姆 加热").matches(heat.searchText))
        assertFalse(SearchQuery("洛托姆 加热").matches(regular.searchText))
        assertEquals(listOf(charizard), filterPokemon(dex, " EXAMPLE 0006 ", null, false, false, emptySet(), SortOrder.NUMBER))
    }

    @Test fun typeMatchupsDoNotDoubleCountRepeatedSingleTypes() {
        assertEquals(2.0, effectiveness(10, listOf(9, 9)), 0.0)
        assertEquals(4.0, effectiveness(5, listOf(9, 2)), 0.0)
        assertEquals(0.25, effectiveness(11, listOf(9, 2)), 0.0)
        assertEquals(0.0, effectiveness(12, listOf(10, 4)), 0.0)
    }

    @Test fun moveSortsPutSentinelValuesLastAndHideUnavailableMovesUnlessAsked() {
        val status = Move(5, "戊", "", "", true, 0, 2, 0, 100, 10, 0, false)           // Power 0: no power at all
        val dynamic = status.copy(id = 1, name = "乙", category = 0, power = 1)         // Power 1 marks a dynamic power, sorted like 0
        val strong = dynamic.copy(id = 2, name = "甲", power = 120, accuracy = 70)
        val sure = dynamic.copy(id = 3, name = "丙", power = 60, accuracy = 101)         // 101 marks a move that never misses
        val closed = dynamic.copy(id = 4, name = "丁", power = 150, available = false)
        val moves = listOf(status, dynamic, strong, sure, closed)
        assertEquals(listOf(strong, sure, status, dynamic), filterMoves(moves, "", null, null, false, MoveSort.POWER))
        assertEquals(listOf(status, dynamic, strong, sure), filterMoves(moves, "", null, null, false, MoveSort.ACCURACY))
        assertEquals(listOf(closed, strong, sure, status, dynamic), filterMoves(moves, "", null, null, true, MoveSort.POWER))
        assertEquals(listOf(status, dynamic, strong, sure), filterMoves(moves, "", null, null, false, MoveSort.DEFAULT))
    }
}
