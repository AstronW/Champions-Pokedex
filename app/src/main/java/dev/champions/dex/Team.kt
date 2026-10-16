package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A team slot: the form that starts the battle and its set. A Mega Stone in [item] makes the analysis use the Mega form;
 * [ability] stays the starting form's own. [up]/[down] are the raised and lowered stats ("" = none), as in the calculator.
 */
data class TeamMember(val id: String, val ability: Int, val item: Int? = null, val moves: List<Int> = emptyList(),
                      val up: String = "", val down: String = "")

const val TeamSize = 6
const val TeamMoveSlots = 4

internal fun encodeTeam(team: List<TeamMember?>): String = JSONArray().apply {
    team.forEach { m ->
        put(m?.let { JSONObject().put("id", it.id).put("ability", it.ability).put("item", it.item ?: JSONObject.NULL)
            .put("moves", JSONArray(it.moves)).put("up", it.up).put("down", it.down) } ?: JSONObject.NULL)
    }
}.toString()

/** Reads stored sets; before sets existed the team was a comma-separated list of form IDs. Always six slots. */
internal fun decodeTeam(json: String?, legacy: String?): List<TeamMember?> {
    val stored = json?.let { runCatching { JSONArray(it) }.getOrNull() }
    return List(TeamSize) { i ->
        if (stored != null) stored.optJSONObject(i)?.let { o ->
            TeamMember(o.getString("id"), o.optInt("ability"), if (o.isNull("item")) null else o.getInt("item"),
                o.optJSONArray("moves")?.let { a -> (0 until a.length()).map(a::getInt) }.orEmpty(), o.optString("up"), o.optString("down"))
        }
        else legacy.orEmpty().split(',').getOrNull(i)?.takeIf(String::isNotBlank)?.let { TeamMember(it, 0) }
    }
}

// —— Forms ——

/** Stones and Mega forms share a trailing X/Y/Z ("Charizardite X" → "Mega Charizard X"); Meowstic's two Megas differ by sex. */
internal fun megaFormFor(holder: Pokemon, stone: Item, variants: List<Pokemon>): Pokemon? {
    fun suffix(name: String) = name.substringAfterLast(' ', "").takeIf { it.length == 1 }.orEmpty()
    val megas = variants.filter { it.formEnglish.startsWith("Mega ") && suffix(it.formEnglish.removePrefix("Mega ")) == suffix(stone.english) }
    return megas.firstOrNull { it.sex == holder.sex } ?: megas.firstOrNull()
}

/** The form that fights: the Mega form when the member holds its stone. */
fun Dex.battleForm(m: TeamMember): Pokemon = m.item?.let { megaForms[m.id to it] } ?: pokemonById.getValue(m.id)

/** Keep only what the starting form allows: its abilities, items it may hold and available moves it learns. */
private fun Dex.fitted(m: TeamMember): TeamMember {
    val form = pokemonById.getValue(m.id)
    val learnable = learnsets[m.id].orEmpty().toSet()
    return m.copy(ability = m.ability.takeIf { it in form.abilities } ?: form.abilities.first(),
        item = m.item?.takeIf { id -> itemsById[id]?.let { it.holders.isEmpty() || m.id in it.holders } == true },
        moves = m.moves.filter { it in learnable && movesById[it]?.available == true }.distinct().take(TeamMoveSlots))
}

/** A Mega form is stored as its holder carrying the stone. Null when the form is no longer in the data. */
fun Dex.normalized(m: TeamMember): TeamMember? {
    val mega = megaSources[m.id]
    val start = mega?.first ?: m.id
    if (start !in pokemonById) return null
    return fitted(if (mega != null) m.copy(id = start, item = mega.second) else m)
}

fun Dex.newTeamMember(id: String): TeamMember? = normalized(TeamMember(id, 0))

/** Another form of the same species; leaving a Mega form drops its stone. */
fun Dex.withForm(m: TeamMember, formId: String): TeamMember {
    val mega = megaSources[formId]
    val start = mega?.first ?: formId
    return fitted(m.copy(id = start, item = mega?.second ?: m.item?.takeIf { megaForms[start to it] == null }))
}

// —— Matchups ——

// Type indices, as in [TypeNames].
private const val NORMAL = 0; private const val FIGHTING = 1; private const val FLYING = 2; private const val POISON = 3
private const val GROUND = 4; private const val ROCK = 5; private const val BUG = 6; private const val GHOST = 7; private const val STEEL = 8
private const val FIRE = 9; private const val WATER = 10; private const val GRASS = 11; private const val ELECTRIC = 12
private const val PSYCHIC = 13; private const val ICE = 14; private const val DRAGON = 15; private const val DARK = 16; private const val FAIRY = 17

/** Abilities that change the damage a type deals to the holder (game ability IDs); levitation is handled with grounding. */
private val AbilityTaken: Map<Int, Map<Int, Double>> = mapOf(
    297 to mapOf(GROUND to 0.0), 18 to mapOf(FIRE to 0.0),            // Earth Eater, Flash Fire
    10 to mapOf(ELECTRIC to 0.0), 31 to mapOf(ELECTRIC to 0.0), 78 to mapOf(ELECTRIC to 0.0), // Volt Absorb, Lightning Rod, Motor Drive
    11 to mapOf(WATER to 0.0), 87 to mapOf(WATER to 0.0, FIRE to 1.25), 157 to mapOf(GRASS to 0.0), // Water Absorb, Dry Skin, Sap Sipper
    47 to mapOf(FIRE to .5, ICE to .5), 85 to mapOf(FIRE to .5), 199 to mapOf(FIRE to .5),      // Thick Fat, Heatproof, Water Bubble
    272 to mapOf(GHOST to .5), 218 to mapOf(FIRE to 2.0),             // Purifying Salt, Fluffy
)
private val Levitating = setOf(26, 313)                               // Levitate, Eelevate
private val SuperEffectiveFilters = setOf(111, 116)                   // Filter, Solid Rock
private const val AIR_BALLOON = 541
private const val IRON_BALL = 278
/** Type-resist berries (item IDs, Occa…Roseli); Chilan Berry (200) weakens any Normal hit, the rest only super-effective ones. */
private val ResistBerries = mapOf(184 to FIRE, 185 to WATER, 186 to ELECTRIC, 187 to GRASS, 188 to ICE, 189 to FIGHTING, 190 to POISON,
    191 to GROUND, 192 to FLYING, 193 to PSYCHIC, 194 to BUG, 195 to ROCK, 196 to GHOST, 197 to DRAGON, 198 to DARK, 199 to STEEL,
    686 to FAIRY, 200 to NORMAL)

/** Refrigerate, Pixilate, Aerilate and Dragonize turn Normal moves into their type. */
private val Skins = mapOf(174 to ICE, 182 to FAIRY, 184 to FLYING, 312 to DRAGON)
private const val LIQUID_VOICE = 204
private const val SCRAPPY = 113
private const val WEATHER_BALL = 311; private const val TERRAIN_PULSE = 805; private const val STRUGGLE = 165; private const val RAGING_BULL = 873
private const val FREEZE_DRY = 573; private const val FLYING_PRESS = 560
/** Weather and terrain the holder's own ability sets on entry, as Weather Ball and Terrain Pulse see them (Mega Sol counts as sun). */
private val WeatherBallTypes = mapOf(70 to FIRE, 315 to FIRE, 2 to WATER, 45 to ROCK, 117 to ICE)
private val TerrainPulseTypes = mapOf(226 to ELECTRIC, 227 to PSYCHIC, 229 to GRASS)

private fun Dex.categoryMoves(category: String) = mechanics.relationsByCategory[category].orEmpty()
    .filter { it.relation == "member" && it.entityKey.startsWith("move:") }.mapNotNull { it.entityKey.substringAfter(':').toIntOrNull() }.toSet()

/** A damaging move as this member uses it; [cause] names what changed its type. */
class Attack(val move: Move, val type: Int, val cause: String?, private val scrappy: Boolean, private val fixedDamage: Boolean) {
    private fun single(defender: Int): Double {
        val base = if (scrappy && defender == GHOST && (type == NORMAL || type == FIGHTING)) 1.0 else effectiveness(type, defender)
        return when (move.id) {
            FREEZE_DRY -> if (defender == WATER) 2.0 else base
            FLYING_PRESS -> base * effectiveness(FLYING, defender)
            else -> base
        }
    }
    /** Fixed-damage moves ignore matchups apart from immunity. */
    fun against(defenders: List<Int>): Double {
        val m = defenders.distinct().fold(1.0) { acc, d -> acc * single(d) }
        return if (fixedDamage) (if (m == 0.0) 0.0 else 1.0) else m
    }
}

/** A member resolved for analysis: the battle form (Mega with its stone), the ability in effect, held item and moves. */
class MemberSet(dex: Dex, val member: TeamMember) {
    val form = dex.battleForm(member)
    val isMega = form.id != member.id
    val ability = if (isMega) form.abilities.first() else member.ability
    val item = member.item?.let(dex.itemsById::get)
    val moves = member.moves.mapNotNull(dex.movesById::get)

    val attacks: List<Attack> = run {
        val sound = dex.categoryMoves("声音类招式"); val fixed = dex.categoryMoves("固定伤害类招式")
        val abilityName = dex.abilitiesById[ability]?.name
        moves.filter { it.category != 2 }.map { m ->
            val type = when {
                m.id == RAGING_BULL -> form.types.last()                         // Paldean Tauros forms hit with their breed's type
                m.id == WEATHER_BALL -> WeatherBallTypes[ability] ?: m.type
                m.id == TERRAIN_PULSE -> TerrainPulseTypes[ability] ?: m.type
                ability == LIQUID_VOICE && m.id in sound -> WATER
                m.type == NORMAL && m.id != STRUGGLE -> Skins[ability] ?: m.type
                else -> m.type
            }
            Attack(m, type, if (type == m.type) null else if (m.id == RAGING_BULL) "形态" else abilityName, ability == SCRAPPY, m.id in fixed)
        }
    }

    /** Multiplier for an attack of [type] against this member, with its ability and item. */
    fun taken(type: Int): Double {
        val grounded = item?.id == IRON_BALL                               // An Iron Ball grounds Flying types and levitating holders.
        val chart = effectiveness(type, if (grounded && type == GROUND) form.types.filter { it != FLYING } else form.types)
        if (chart == 0.0) return 0.0
        if (type == GROUND && !grounded && (ability in Levitating || item?.id == AIR_BALLOON)) return 0.0
        var m = chart * (AbilityTaken[ability]?.get(type) ?: 1.0)
        if (chart > 1 && ability in SuperEffectiveFilters) m *= .75
        if (item != null && ResistBerries[item.id] == type && (chart > 1 || type == NORMAL)) m *= .5
        return m
    }

    /** Best multiplier any damaging move reaches against a single defending type. */
    fun hits(type: Int): Double = attacks.maxOfOrNull { it.against(listOf(type)) } ?: 0.0
}

fun multiplierText(m: Double) = "×" + when (m) {
    .5 -> "½"; .25 -> "¼"
    else -> String.format(Locale.ROOT, "%.3f", m).trimEnd('0').trimEnd('.')
}
