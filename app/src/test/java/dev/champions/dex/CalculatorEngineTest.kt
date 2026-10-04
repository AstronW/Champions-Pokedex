package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CalculatorEngineTest {
    private val assets = File("src/main/assets")
    private val engine = CalculatorEngine { File(assets, it).readText() }

    @Test fun androidInterpreterMatchesNodeForEveryGoldenCase() {
        val cases = JSONArray(File("../scripts/calculator/fixtures.json").readText())
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val result = JSONObject(engine.calculate(case.getJSONObject("request").toString()))
            assertFalse("${case.getString("name")}: $result", result.has("error"))
            assertEquals(case.getString("name"), case.getJSONObject("expected").toString(), result.toString())
        }
    }
}
