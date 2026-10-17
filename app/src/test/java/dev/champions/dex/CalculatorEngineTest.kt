package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CalculatorEngineTest {
    private val dex = parseDex(JSONObject(File("src/main/assets/dex.json").readText()), Mechanics())
    private val catalog = CalculatorCatalog(JSONObject(File("src/main/assets/calculator/catalog.json").readText()))
    private val engine = CalculatorEngine(dex, catalog)
    /** Produced by the upstream source and the original JavaScript boundary (scripts/calculator/test.cjs). */
    private val cases = JSONArray(File("../scripts/calculator/fixtures.json").readText())

    private fun JSONObject.stats(key: String) = getJSONObject(key).let { s -> calcStats.map(s::getInt) }

    @Test fun nativeEngineMatchesTheUpstreamReferenceForEveryCase() {
        assertTrue(cases.length() > 1000)
        var damaging = 0
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            val expected = case.getJSONObject("expected")
            val actual = engine.calculate(case.getJSONObject("request").toString())
            if (expected.has("error")) {
                assertEquals(name, CalcFailure(expected.getString("error")), actual)
                continue
            }
            assertTrue("$name: $actual", actual is CalcDamage)
            actual as CalcDamage
            val rolls = expected.getJSONArray("rolls").let { r -> (0 until r.length()).map { j -> r.getJSONArray(j).let { row -> (0 until row.length()).map(row::getInt) } } }
            val wanted = CalcDamage(
                expected.getInt("min"), expected.getInt("max"), expected.getInt("maxHP"), expected.getInt("currentHP"),
                expected.getDouble("percentMin"), expected.getDouble("percentMax"), expected.getDouble("ohko"), rolls,
                expected.stats("attackerStats"), expected.stats("defenderStats"), expected.getInt("hits"),
            )
            assertEquals(name, wanted, actual)
            if (actual.max > 0) damaging++
        }
        assertTrue("too few damaging cases: $damaging", damaging > 900)
    }

    /** Remembers every key the engine asks for. */
    private class ReadKeys : JSONObject() {
        val read = mutableSetOf<String>()
        override fun opt(key: String?): Any? = super.opt(key).also { if (key != null) read += key }
    }

    @Test fun everyConditionThePageOffersIsReadByTheEngine() {
        val request = JSONObject(cases.getJSONObject(0).getJSONObject("request").toString())
        val field = ReadKeys(); val attackerSide = ReadKeys(); val defenderSide = ReadKeys()
        request.put("field", field.put("attackerSide", attackerSide).put("defenderSide", defenderSide))
        assertTrue(engine.calculate(request) is CalcDamage)
        assertTrue(field.read.containsAll(CalculatorFieldConditions.map { it.first }))
        for (side in listOf(attackerSide, defenderSide)) {
            val ignored = CalculatorSideConditions.map { it.first } - side.read
            assertTrue("offered but never read: $ignored", ignored.isEmpty())
        }
    }

    @Test fun malformedRequestsReportAMessageInsteadOfThrowing() {
        val request = JSONObject(cases.getJSONObject(0).getJSONObject("request").toString())
        request.getJSONObject("attacker").remove("sp")
        assertEquals(CalcFailure("能力点不能为空"), engine.calculate(request))
        request.put("attacker", "nothing")
        assertEquals(CalcFailure("此形态尚未适配计算器"), engine.calculate(request))
    }
}
