package dev.champions.dex

import org.junit.Assert.*
import org.junit.Test

class DetailNavigationTest {
    @Test fun switchingFormsRepeatedlyDoesNotAccumulateReturnSteps() {
        val original = "pokemon:0003000"
        var stack = listOf(original)
        repeat(20) {
            stack = openDetailEntry(stack, "pokemon:0003001")
            assertEquals(2, stack.size)
            stack = openDetailEntry(stack, original)
            assertEquals(listOf(original), stack)
        }
    }

    @Test fun revisitKeepsTheExistingEntryAndItsPrecedingPath() {
        val stack = listOf("pokemon:0006000", "move:53", "wiki:move:53", "ability:18")
        assertEquals(stack.take(2), openDetailEntry(stack, "move:53"))
        assertEquals(stack, openDetailEntry(stack, "ability:18"))
    }
}
