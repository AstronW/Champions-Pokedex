package dev.champions.dex

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CalculatorCatalogTest {
    private val source = JSONObject(File("src/main/assets/calculator/catalog.json").readText())
    private val catalog = CalculatorCatalog(source)

    @Test fun everyNatureAdjustmentResolvesToTheOriginalEngineName() {
        assertEquals(25, catalog.natureEffects.size)
        catalog.natureEffects.forEach { (name, stats) ->
            assertEquals(if (stats.first == stats.second) "Serious" else name, catalog.natureFor(stats.first, stats.second))
        }
        assertEquals("Serious", catalog.natureFor("spa", ""))
        assertEquals("Serious", catalog.natureFor("", "atk"))
        assertEquals("Serious", catalog.natureFor("", ""))
    }

    @Test fun itemOrderAndMoveHitBoundsMatchTheGeneratedCatalog() {
        val items = source.getJSONArray("items")
        assertEquals(items.length(), catalog.items.size)
        catalog.items.forEachIndexed { i, item ->
            assertEquals(items.getJSONObject(i).getString("name"), item.name)
            assertEquals(items.getJSONObject(i).getString("label"), item.label)
        }
        val hits = source.getJSONObject("moveHits")
        assertEquals(hits.length(), catalog.moveHits.size)
        hits.keys().forEach { id ->
            val range = catalog.moveHits.getValue(id.toInt())
            assertEquals(hits.getJSONArray(id).getInt(0), range.first)
            assertEquals(hits.getJSONArray(id).getInt(1), range.last)
        }
    }
}
