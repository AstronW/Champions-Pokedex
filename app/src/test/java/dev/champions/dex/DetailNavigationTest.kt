package dev.champions.dex

import org.junit.Assert.*
import org.junit.Test

class DetailNavigationTest {
    @Test fun switchingFormsRepeatedlyDoesNotAccumulateReturnSteps() {
        val original = "0#pokemon:0003000"
        var stack = listOf(original)
        repeat(20) { i ->
            stack = openDetailEntry(stack, "${2*i+1}#pokemon:0003001")
            assertEquals(2, stack.size)
            stack = openDetailEntry(stack, "${2*i+2}#pokemon:0003000")
            assertEquals(listOf(original), stack)
        }
        assertTrue(stack.dropLast(1).isEmpty())
    }

    @Test fun revisitKeepsTheExistingEntryAndItsPrecedingPath() {
        val stack = listOf("0#pokemon:0006000", "1#move:53", "2#wiki:move:53", "3#ability:18")
        assertEquals(stack.take(2), openDetailEntry(stack, "4#move:53"))
        assertEquals(stack, openDetailEntry(stack, "4#ability:18"))
    }
}
