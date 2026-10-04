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
    @Test fun favoritesAndSearchKeepExactForm() {
        assertEquals(listOf(heat),filterPokemon(dex,"",null,false,true,setOf(heat.key),SortOrder.NUMBER))
        assertEquals(listOf(heat),filterPokemon(dex,"加热",null,false,false,emptySet(),SortOrder.NUMBER))
        assertEquals(listOf(charizard),filterPokemon(dex,"0006",null,false,false,emptySet(),SortOrder.NUMBER))
    }
    @Test fun sortingUsesGameStats() {
        assertEquals(listOf(charizard,regular,heat),filterPokemon(dex,"",null,true,false,emptySet(),SortOrder.SPEED))
        assertEquals(2,filterPokemon(dex,"",null,false,false,emptySet(),SortOrder.NUMBER).size)
    }
    @Test fun moveSentinelsAreNotDisplayedAsOrdinaryValues() {
        val dynamic=Move(1,"","","",true,0,0,1,101,8,-2,false)
        assertEquals("变化",dynamic.powerLabel);assertEquals("—",dynamic.accuracyLabel)
        assertEquals("40",dynamic.copy(power=40).powerLabel)
        assertEquals("100%",dynamic.copy(accuracy=100).accuracyLabel)
    }
}
