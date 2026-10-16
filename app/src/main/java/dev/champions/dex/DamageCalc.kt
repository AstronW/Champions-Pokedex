package dev.champions.dex

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.truncate

/*
 * Native port of the Champions mechanics of Smogon damage-calc (MIT, Honko and contributors), pinned at commit
 * eb9af6d94e7ee0ce034d2d6f2d44d4b2d66ff981 (vendor/damage-calc/src: mechanics/champions.ts, mechanics/util.ts,
 * pokemon.ts, move.ts, field.ts, stats.ts, result.ts).
 *
 * The port is deliberately literal: arithmetic runs on doubles like JavaScript numbers, and the objects are mutated in
 * the same order as upstream (including the Parental Bond child sharing the defender, move and field), so the rolls
 * match the original engine exactly. Description text (rawDesc) is omitted because nothing reads it.
 * scripts/calculator/fixtures.json, produced by the upstream source, is the reference for CalculatorEngineTest.
 */

private const val HP = 0
private const val ATK = 1
private const val DEF = 2
private const val SPA = 3
private const val SPD = 4
private const val SPE = 5

/** Upstream data the formula consults beyond the two Pokémon and the move. */
internal class ChampionsRules(
    private val types: Map<String, Map<String, Double>>,
    items: List<CalculatorItem>,
) {
    private val itemsByName = items.associateBy { it.name }
    private val itemsById = items.associateBy { toId(it.name) }

    fun effectiveness(attack: String, defend: String): Double =
        types[attack]?.get(defend) ?: throw IllegalStateException("缺少属性相性数据：$attack → $defend")
    fun isItem(name: String) = toId(name) in itemsById
    fun megaStone(name: String) = itemsById[toId(name)]?.megaStone.orEmpty()
    fun itemBoostType(name: String) = itemsByName[name]?.boostType
    fun berryResistType(name: String) = itemsByName[name]?.resistType
    fun flingPower(name: String) = if (name.isEmpty()) 0 else itemsByName[name]?.fling ?: 0
}

/** `toID` of upstream util.ts. */
internal fun toId(text: String): String {
    val lower = text.lowercase()
    return if (lower == "flabébé") "flabebe" else lower.replace(Regex("[^a-z0-9]+"), "")
}

internal class CalcNature(val plus: Int, val minus: Int)

/** The species fields upstream reads once overrides are applied: name, base stats, types and weight. */
internal class CalcSpecies(val name: String, val baseStats: IntArray, val types: List<String>, val weightkg: Double)

internal class CalcPokemon(
    val species: CalcSpecies,
    var ability: String,
    val abilityOn: Boolean,
    val alliesFainted: Int,
    var item: String,
    val gender: String,
    val nature: CalcNature,
    val evs: IntArray,
    val boosts: IntArray,
    curHP: Int,
    val status: String,
) {
    val name get() = species.name
    val level = 50
    val weightkg = species.weightkg
    var types = species.types
    var disabledItem = ""
    val rawStats = IntArray(6) { calcStat(it, species.baseStats[it], evs[it]) }
    val stats = rawStats.copyOf()
    val originalCurHP = if (curHP > 0 && curHP <= rawStats[HP]) curHP else rawStats[HP]

    /** `Stats.calcStatChampions`. */
    private fun calcStat(stat: Int, base: Int, sp: Int): Int {
        if (stat == HP) return if (base == 1) base else base + sp + 75
        val n = when {
            nature.plus == stat && nature.minus == stat -> 1.0
            nature.plus == stat -> 1.1
            nature.minus == stat -> 0.9
            else -> 1.0
        }
        return floor(n * (base + sp + 20)).toInt()
    }

    fun maxHP() = rawStats[HP]
    fun curHP() = originalCurHP
    fun hasAbility(vararg names: String) = ability.isNotEmpty() && ability in names
    fun hasItem(vararg names: String) = item.isNotEmpty() && item in names
    fun hasStatus(vararg names: String) = status.isNotEmpty() && status in names
    fun hasType(vararg names: String) = names.any { it in types }
    fun hasOriginalType(vararg names: String) = names.any { it in types }
    fun named(vararg names: String) = name in names

    /** Rebuilds from the current settings like upstream: stats are recomputed and type or item changes are dropped. */
    fun clone() = CalcPokemon(species, ability, abilityOn, alliesFainted, item, gender, nature, evs.copyOf(), boosts.copyOf(), originalCurHP, status)
}

internal class CalcMove(
    val name: String,
    val bp: Int,
    var type: String,
    var category: String,
    val flags: MutableSet<String>,
    val secondaries: Boolean,
    var target: String,
    val recoil: Boolean,
    val hasCrashDamage: Boolean,
    val isCrit: Boolean,
    var priority: Int,
    val dropsStats: Int,
    val ignoreDefensive: Boolean,
    val overrideDefensiveStat: String?,
    val breaksProtect: Boolean,
    val hits: Int,
) {
    val originalName get() = name
    val timesUsed = 1
    fun named(vararg names: String) = name in names
    fun hasType(vararg names: String?) = type in names
    fun flag(name: String) = name in flags
}

internal class CalcSide(
    var spikes: Int = 0, var isSR: Boolean = false, var isReflect: Boolean = false, var isLightScreen: Boolean = false,
    var isProtected: Boolean = false, var isCharge: Boolean = false, var isTailwind: Boolean = false,
    var isHelpingHand: Boolean = false, var isPowerTrick: Boolean = false, var isFriendGuard: Boolean = false,
    var isAuroraVeil: Boolean = false, var isSwitching: String? = null,
)

internal class CalcField(
    val gameType: String = "Singles",
    var weather: String? = null,
    var terrain: String? = null,
    val isMagicRoom: Boolean = false,
    val isWonderRoom: Boolean = false,
    val isGravity: Boolean = false,
    val isFairyAura: Boolean = false,
    val isDarkAura: Boolean = false,
    val attackerSide: CalcSide = CalcSide(),
    val defenderSide: CalcSide = CalcSide(),
) {
    fun hasWeather(vararg names: String) = weather != null && weather in names
    fun hasTerrain(vararg names: String) = terrain != null && terrain in names
}

/** `number | number[] | number[][]` of upstream results. */
internal sealed interface Damage {
    data class Fixed(val value: Int) : Damage
    data class Rolls(val values: List<Int>) : Damage
    data class Matrix(val rows: List<Damage>) : Damage

    /** `Result.range()`; null where upstream would produce NaN. */
    fun range(): Pair<Int, Int>? = when (this) {
        is Fixed -> value to value
        is Rolls -> if (values.size < 16) values.sum().let { it to it } else values.first() to values.last()
        is Matrix -> rows.fold(0 to 0) { (lo, hi), row -> (row as? Rolls ?: return null).let { lo + it.values.first() to hi + it.values.last() } }
    }
}

// —— mechanics/util.ts ——

private val EvItems = arrayOf("Macho Brace", "Power Anklet", "Power Band", "Power Belt", "Power Bracer", "Power Lens", "Power Weight")

/** Game Freak rounds down on .5. */
private fun pokeRound(n: Double) = if (n % 1 > 0.5) ceil(n) else floor(n)
private fun of16(n: Double) = if (n > 65535) n % 65536 else n
private fun of32(n: Double) = if (n > 4294967295.0) n % 4294967296.0 else n

/** Integer chain as in JavaScript: the product is truncated to 32 bits before the shift. */
private fun chainMods(mods: List<Int>, lowerBound: Int, upperBound: Int): Int {
    var m = 4096
    for (mod in mods) if (mod != 4096) m = (m.toLong() * mod + 2048).toInt() shr 12
    return max(min(m, upperBound), lowerBound)
}

private fun isGrounded(pokemon: CalcPokemon, field: CalcField) = field.isGravity || pokemon.hasItem("Iron Ball") ||
    (!pokemon.hasType("Flying") && !pokemon.hasAbility("Levitate", "Eelevate") && !pokemon.hasItem("Air Balloon"))

private val BoostTable = arrayOf(2 to 8, 2 to 7, 2 to 6, 2 to 5, 2 to 4, 2 to 3, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2)

private fun getModifiedStat(stat: Int, mod: Int): Int {
    val (numerator, denominator) = BoostTable[6 + mod]
    return floor(of16(stat.toDouble() * numerator) / denominator).toInt()
}

private fun computeFinalStats(attacker: CalcPokemon, defender: CalcPokemon, field: CalcField, vararg stats: Int) {
    for ((pokemon, side) in listOf(attacker to field.attackerSide, defender to field.defenderSide)) {
        for (stat in stats) {
            pokemon.stats[stat] = if (stat == SPE) getFinalSpeed(pokemon, field, side) else getModifiedStat(pokemon.rawStats[stat], pokemon.boosts[stat])
        }
    }
}

/** Quark Drive / Protosynthesis never apply: the boosted stat is not part of the calculator input. */
private fun getFinalSpeed(pokemon: CalcPokemon, field: CalcField, side: CalcSide): Int {
    val weather = field.weather ?: ""
    var speed = getModifiedStat(pokemon.rawStats[SPE], pokemon.boosts[SPE]).toDouble()
    val speedMods = mutableListOf<Int>()
    if (side.isTailwind) speedMods += 8192
    if ((pokemon.hasAbility("Unburden") && pokemon.abilityOn) ||
        (pokemon.hasAbility("Chlorophyll") && "Sun" in weather) ||
        (pokemon.hasAbility("Sand Rush") && weather == "Sand") ||
        (pokemon.hasAbility("Swift Swim") && "Rain" in weather) ||
        (pokemon.hasAbility("Slush Rush") && weather in listOf("Hail", "Snow")) ||
        (pokemon.hasAbility("Surge Surfer") && field.terrain == "Electric")
    ) speedMods += 8192
    else if (pokemon.hasAbility("Quick Feet") && pokemon.status.isNotEmpty()) speedMods += 6144
    else if (pokemon.hasAbility("Slow Start") && pokemon.abilityOn) speedMods += 2048

    if (!(pokemon.hasAbility("Unburden") && pokemon.abilityOn)) {
        if (pokemon.hasItem("Choice Scarf")) speedMods += 6144
        else if (pokemon.hasItem("Iron Ball", *EvItems)) speedMods += 2048
        else if (pokemon.hasItem("Quick Powder") && pokemon.named("Ditto")) speedMods += 8192
    }
    speed = of32(pokeRound(speed * chainMods(speedMods, 410, 131172) / 4096))
    if (pokemon.hasStatus("par") && !pokemon.hasAbility("Quick Feet")) speed = floor(of32(speed * 50) / 100)
    // Upstream caps generation numbers <= 2 at 999, and Champions is generation 0.
    return max(0.0, min(999.0, speed)).toInt()
}

private fun getMoveEffectiveness(rules: ChampionsRules, move: CalcMove, type: String, isGhostRevealed: Boolean, isGravity: Boolean): Double = when {
    isGhostRevealed && type == "Ghost" && move.hasType("Normal", "Fighting") -> 1.0
    isGravity && type == "Flying" && move.hasType("Ground") -> 1.0
    move.named("Freeze-Dry") && type == "Water" -> 2.0
    move.named("Nihil Light") && type == "Fairy" -> 1.0
    else -> rules.effectiveness(move.type, type).let { if (move.named("Flying Press")) it * rules.effectiveness("Flying", type) else it }
}

private fun checkAirLock(pokemon: CalcPokemon, field: CalcField) {
    if (pokemon.hasAbility("Air Lock", "Cloud Nine")) field.weather = null
}

private fun checkForecast(pokemon: CalcPokemon, weather: String?) {
    if (pokemon.hasAbility("Forecast") && pokemon.named("Castform")) {
        pokemon.types = listOf(when (weather) {
            "Sun", "Harsh Sunshine" -> "Fire"
            "Rain", "Heavy Rain" -> "Water"
            "Hail", "Snow" -> "Ice"
            else -> "Normal"
        })
    }
}

private fun checkItem(pokemon: CalcPokemon, magicRoomActive: Boolean) {
    if ((pokemon.hasAbility("Klutz") && pokemon.item !in EvItems) || magicRoomActive) {
        pokemon.disabledItem = pokemon.item
        pokemon.item = ""
    }
}

private fun IntArray.swap(a: Int, b: Int) { val t = this[a]; this[a] = this[b]; this[b] = t }

private fun checkRawStatChanges(pokemon: CalcPokemon, powerTrickActive: Boolean, wonderRoomActive: Boolean) {
    if (powerTrickActive) pokemon.rawStats.swap(ATK, DEF)
    if (wonderRoomActive) pokemon.rawStats.swap(DEF, SPD)
}

private fun checkIntimidate(source: CalcPokemon, target: CalcPokemon) {
    val blocked = target.hasAbility("Clear Body", "White Smoke", "Hyper Cutter", "Full Metal Body") ||
        target.hasAbility("Inner Focus", "Own Tempo", "Oblivious", "Scrappy") || target.hasItem("Clear Amulet")
    if (source.hasAbility("Intimidate") && source.abilityOn && !blocked) {
        target.boosts[ATK] = when {
            target.hasAbility("Contrary", "Defiant", "Guard Dog") -> min(6, target.boosts[ATK] + 1)
            target.hasAbility("Simple") -> max(-6, target.boosts[ATK] - 2)
            else -> max(-6, target.boosts[ATK] - 1)
        }
        if (target.hasAbility("Competitive")) target.boosts[SPA] = min(6, target.boosts[SPA] + 2)
    }
}

private fun checkInfiltrator(pokemon: CalcPokemon, affectedSide: CalcSide) {
    if (pokemon.hasAbility("Infiltrator")) {
        affectedSide.isReflect = false
        affectedSide.isLightScreen = false
        affectedSide.isAuroraVeil = false
    }
}

/** JavaScript `substring(0, indexOf(' '))`, which is empty when there is no space. */
private fun String.firstWord() = indexOf(' ').let { if (it < 0) "" else substring(0, it) }

private fun checkSeedBoost(pokemon: CalcPokemon, field: CalcField) {
    if (pokemon.item.isEmpty()) return
    if (field.terrain != null && "Seed" in pokemon.item) {
        val terrainSeed = pokemon.item.firstWord()
        if (field.hasTerrain(terrainSeed)) {
            val stat = if (terrainSeed == "Grassy" || terrainSeed == "Electric") DEF else SPD
            pokemon.boosts[stat] = if (pokemon.hasAbility("Contrary")) max(-6, pokemon.boosts[stat] - 1) else min(6, pokemon.boosts[stat] + 1)
            pokemon.item = ""
        }
    }
}

/** Guaranteed, damage-relevant boosts between hits. Returns whether each side has used its item. */
private fun checkMultihitBoost(
    attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField,
    attackerUsedItemIn: Boolean = false, defenderUsedItemIn: Boolean = false,
): Pair<Boolean, Boolean> {
    var attackerUsedItem = attackerUsedItemIn
    var defenderUsedItem = defenderUsedItemIn
    if (move.named("Gyro Ball", "Electro Ball") && defender.hasAbility("Gooey", "Tangling Hair")) {
        if (attacker.hasItem("White Herb") && !attackerUsedItem) attackerUsedItem = true
        else {
            attacker.boosts[SPE] = max(attacker.boosts[SPE] - 1, -6)
            attacker.stats[SPE] = getFinalSpeed(attacker, field, field.attackerSide)
        }
    } else if (move.named("Power-Up Punch")) {
        attacker.boosts[ATK] = min(attacker.boosts[ATK] + 1, 6)
        attacker.stats[ATK] = getModifiedStat(attacker.rawStats[ATK], attacker.boosts[ATK])
    }

    val atkSimple = if (attacker.hasAbility("Simple")) 2 else 1
    val defSimple = if (defender.hasAbility("Simple")) 2 else 1

    // Same precedence as upstream: the used-item guard only covers Luminous Moss.
    if ((!defenderUsedItem && defender.hasItem("Luminous Moss") && move.hasType("Water")) ||
        (defender.hasItem("Maranga Berry") && move.category == "Special") ||
        (defender.hasItem("Kee Berry") && move.category == "Physical")
    ) {
        val defStat = if (defender.hasItem("Kee Berry")) DEF else SPD
        if (!attacker.hasAbility("Unaware")) {
            if (defender.hasAbility("Contrary")) {
                if (defender.hasItem("White Herb") && !defenderUsedItem) defenderUsedItem = true
                else defender.boosts[defStat] = max(-6, defender.boosts[defStat] - defSimple)
            } else {
                defender.boosts[defStat] = min(6, defender.boosts[defStat] + defSimple)
            }
            defender.stats[defStat] = getModifiedStat(defender.rawStats[defStat], defender.boosts[defStat])
            defenderUsedItem = true
        }
    }

    if (defender.hasAbility("Seed Sower")) field.terrain = "Grassy"
    if (defender.hasAbility("Sand Spit")) field.weather = "Sand"

    if (defender.hasAbility("Stamina")) {
        if (!attacker.hasAbility("Unaware")) {
            defender.boosts[DEF] = min(defender.boosts[DEF] + 1, 6)
            defender.stats[DEF] = getModifiedStat(defender.rawStats[DEF], defender.boosts[DEF])
        }
    } else if (defender.hasAbility("Water Compaction") && move.hasType("Water")) {
        if (!attacker.hasAbility("Unaware")) {
            defender.boosts[DEF] = min(defender.boosts[DEF] + 2, 6)
            defender.stats[DEF] = getModifiedStat(defender.rawStats[DEF], defender.boosts[DEF])
        }
    } else if (defender.hasAbility("Weak Armor")) {
        if (!attacker.hasAbility("Unaware")) {
            if (defender.hasItem("White Herb") && !defenderUsedItem && defender.boosts[DEF] == 0) defenderUsedItem = true
            else {
                defender.boosts[DEF] = max(defender.boosts[DEF] - 1, -6)
                defender.stats[DEF] = getModifiedStat(defender.rawStats[DEF], defender.boosts[DEF])
            }
        }
        defender.boosts[SPE] = min(defender.boosts[SPE] + 2, 6)
        defender.stats[SPE] = getFinalSpeed(defender, field, field.defenderSide)
    }

    if (move.dropsStats != 0 && !attacker.hasAbility("Unaware")) {
        val stat = if (move.category == "Special") SPA else ATK
        var boosts = attacker.boosts[stat]
        boosts = if (attacker.hasAbility("Contrary")) min(6, boosts + move.dropsStats) else max(-6, boosts - move.dropsStats * atkSimple)
        if (attacker.hasItem("White Herb") && attacker.boosts[stat] < 0 && !attackerUsedItem) {
            boosts += move.dropsStats * atkSimple
            attackerUsedItem = true
        }
        attacker.boosts[stat] = boosts
        // Upstream reads the defender's boost here; kept for parity (the attack is recomputed from boosts anyway).
        attacker.stats[stat] = getModifiedStat(attacker.rawStats[stat], defender.boosts[stat])
    }

    if (defender.hasAbility("Mummy", "Wandering Spirit", "Lingering Aroma") && move.flag("contact")) {
        val oldAttackerAbility = attacker.ability
        attacker.ability = defender.ability
        if (defender.hasAbility("Wandering Spirit")) defender.ability = oldAttackerAbility
    }
    return attackerUsedItem to defenderUsedItem
}

private fun getBaseDamage(level: Int, basePower: Int, attack: Int, defense: Int): Double =
    floor(of32(floor(of32(of32(floor(2.0 * level / 5 + 2) * basePower) * attack) / defense) / 50 + 2))

private fun getFinalDamage(baseAmount: Double, i: Int, effectiveness: Double, isBurned: Boolean, stabMod: Int, finalMod: Int, protect: Boolean): Int {
    var damage = floor(of32(baseAmount * (85 + i)) / 100)
    if (stabMod != 4096) damage = of32(damage * stabMod) / 4096
    damage = floor(of32(pokeRound(damage) * effectiveness))
    if (isBurned) damage = floor(damage / 2)
    if (protect) damage = pokeRound(of32(damage * 1024) / 4096)
    return of16(pokeRound(max(1.0, of32(damage * finalMod) / 4096))).toInt()
}

private fun getShellSideArmCategory(source: CalcPokemon, target: CalcPokemon, wonderRoomActive: Boolean): String {
    val physical = source.stats[ATK].toDouble() / target.stats[if (wonderRoomActive) SPD else DEF]
    val special = source.stats[SPA].toDouble() / target.stats[if (wonderRoomActive) DEF else SPD]
    return if (physical > special) "Physical" else "Special"
}

private fun getWeight(pokemon: CalcPokemon): Double {
    var weightHG = pokemon.weightkg * 10
    val abilityFactor = if (pokemon.hasAbility("Heavy Metal")) 2.0 else if (pokemon.hasAbility("Light Metal")) 0.5 else 1.0
    if (abilityFactor != 1.0) weightHG = max(truncate(weightHG * abilityFactor), 1.0)
    if (pokemon.hasItem("Float Stone")) weightHG = max(truncate(weightHG * 0.5), 1.0)
    return weightHG / 10
}

private fun getStabMod(pokemon: CalcPokemon, move: CalcMove): Int {
    var stabMod = 4096
    if (pokemon.hasOriginalType(move.type)) stabMod += 2048
    else if (pokemon.hasAbility("Protean", "Libero")) stabMod += 2048
    if (pokemon.hasAbility("Adaptability") && pokemon.hasType(move.type)) stabMod += 2048
    return stabMod
}

/** Only positive boosts count. */
private fun countBoosts(boosts: IntArray) = (ATK..SPE).sumOf { max(0, boosts[it]) }

private fun handleFixedDamageMoves(attacker: CalcPokemon, move: CalcMove) = when {
    move.named("Seismic Toss", "Night Shade") -> attacker.level
    move.named("Dragon Rage") -> 40
    move.named("Sonic Boom") -> 20
    else -> 0
}

// —— mechanics/champions.ts ——

private val IgnorableDefenderAbilities = setOf(
    "Aura Guard", "Armor Tail", "Aroma Veil", "Battle Armor", "Big Pecks", "Bulletproof", "Clear Body", "Contrary",
    "Damp", "Disguise", "Dry Skin", "Earth Eater", "Eelevate", "Filter", "Flash Fire", "Flower Veil", "Fluffy",
    "Friend Guard", "Fur Coat", "Grass Pelt", "Guard Dog", "Heatproof", "Heavy Metal", "Hyper Cutter", "Illuminate",
    "Immunity", "Inner Focus", "Insomnia", "Keen Eye", "Leaf Guard", "Levitate", "Light Metal", "Lightning Rod", "Limber",
    "Magic Bounce", "Magma Armor", "Marvel Scale", "Mirror Armor", "Motor Drive", "Multiscale", "Oblivious", "Overcoat", "Own Tempo",
    "Punk Rock", "Purifying Salt", "Queenly Majesty", "Sand Veil", "Sap Sipper", "Shell Armor", "Shield Dust", "Snow Cloak",
    "Solid Rock", "Soundproof", "Sticky Hold", "Storm Drain", "Sturdy", "Sweet Veil", "Tangled Feet", "Telepathy", "Thermal Exchange",
    "Thick Fat", "Unaware", "Vital Spirit", "Volt Absorb", "Water Absorb", "Water Bubble", "Water Veil", "White Smoke",
)

private fun CalcSide.clearScreens() { isReflect = false; isLightScreen = false; isAuroraVeil = false }

/** `calculate` of calc.ts. The Pokémon are cloned as upstream does; [move] and [field] must be fresh, as they are mutated. */
internal fun calculateDamage(rules: ChampionsRules, attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField): Damage =
    calculateChampions(rules, attacker.clone(), defender.clone(), move, field)

private fun calculateChampions(rules: ChampionsRules, attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField): Damage {
    checkAirLock(attacker, field)
    checkAirLock(defender, field)
    checkForecast(attacker, field.weather)
    checkForecast(defender, field.weather)
    checkItem(attacker, field.isMagicRoom)
    checkItem(defender, field.isMagicRoom)
    checkRawStatChanges(attacker, field.attackerSide.isPowerTrick, field.isWonderRoom)
    checkRawStatChanges(defender, field.defenderSide.isPowerTrick, field.isWonderRoom)
    checkSeedBoost(attacker, field)
    checkSeedBoost(defender, field)

    computeFinalStats(attacker, defender, field, DEF, SPD, SPE)

    checkIntimidate(attacker, defender)
    checkIntimidate(defender, attacker)

    if (move.named("Meteor Beam", "Electro Shot")) {
        attacker.boosts[SPA] += if (attacker.hasAbility("Contrary")) -1 else 1
        attacker.boosts[SPA] = min(6, max(-6, attacker.boosts[SPA]))
    }

    computeFinalStats(attacker, defender, field, ATK, SPA)

    checkInfiltrator(attacker, field.defenderSide)
    checkInfiltrator(defender, field.attackerSide)

    val none = Damage.Fixed(0)
    if (move.category == "Status") return none

    if (move.named("Shell Side Arm") && getShellSideArmCategory(attacker, defender, field.isWonderRoom) == "Physical") {
        move.category = "Physical"
        move.flags += "contact"
    }

    val breaksProtect = move.breaksProtect || (attacker.hasAbility("Unseen Fist", "Piercing Drill") && move.flag("contact"))
    if (field.defenderSide.isProtected && !breaksProtect) return none

    if (move.name == "Pain Split") {
        val average = (attacker.curHP() + defender.curHP()) / 2
        return Damage.Fixed(max(0, defender.curHP() - average))
    }

    if (defender.ability in IgnorableDefenderAbilities && attacker.hasAbility("Mold Breaker")) defender.ability = ""

    // Merciless does not ignore Shell Armor.
    val isCritical = !defender.hasAbility("Shell Armor", "Battle Armor") &&
        (move.isCrit || (attacker.hasAbility("Merciless") && defender.hasStatus("psn", "tox"))) && move.timesUsed == 1

    var type = move.type
    if (move.originalName == "Weather Ball") {
        type = when {
            field.hasWeather("Sun", "Harsh Sunshine") || attacker.hasAbility("Mega Sol") -> "Fire"
            field.hasWeather("Rain", "Heavy Rain") -> "Water"
            field.hasWeather("Sand") -> "Rock"
            field.hasWeather("Hail", "Snow") -> "Ice"
            else -> "Normal"
        }
    } else if (move.originalName == "Terrain Pulse" && isGrounded(attacker, field)) {
        type = when {
            field.hasTerrain("Electric") -> "Electric"
            field.hasTerrain("Grassy") -> "Grass"
            field.hasTerrain("Misty") -> "Fairy"
            field.hasTerrain("Psychic") -> "Psychic"
            else -> "Normal"
        }
    } else if (move.named("Aura Wheel")) {
        if (attacker.named("Morpeko")) type = "Electric" else if (attacker.named("Morpeko-Hangry")) type = "Dark"
    } else if (move.named("Raging Bull")) {
        when {
            attacker.named("Tauros-Paldea-Combat") -> type = "Fighting"
            attacker.named("Tauros-Paldea-Blaze") -> type = "Fire"
            attacker.named("Tauros-Paldea-Aqua") -> type = "Water"
        }
        field.defenderSide.clearScreens()
    } else if (move.named("Brick Break", "Psychic Fangs")) {
        field.defenderSide.clearScreens()
    }

    if (attacker.hasAbility("Electromorphosis") && attacker.abilityOn) field.attackerSide.isCharge = true

    var hasAteAbilityTypeChange = false
    if (!move.named("Weather Ball", "Terrain Pulse", "Struggle")) {
        val normal = type == "Normal"
        val ate = when {
            attacker.hasAbility("Aerilate") && normal -> "Flying"
            attacker.hasAbility("Dragonize") && normal -> "Dragon"
            attacker.hasAbility("Liquid Voice") && move.flag("sound") -> null.also { type = "Water" }
            attacker.hasAbility("Pixilate") && normal -> "Fairy"
            attacker.hasAbility("Refrigerate") && normal -> "Ice"
            else -> null
        }
        if (ate != null) { type = ate; hasAteAbilityTypeChange = true }
    }
    move.type = type

    val isGhostRevealed = attacker.hasAbility("Scrappy")
    val type1Effectiveness = getMoveEffectiveness(rules, move, defender.types[0], isGhostRevealed, field.isGravity)
    val type2Effectiveness = defender.types.getOrNull(1)?.takeIf { it.isNotEmpty() }
        ?.let { getMoveEffectiveness(rules, move, it, isGhostRevealed, field.isGravity) } ?: 1.0
    var typeEffectiveness = type1Effectiveness * type2Effectiveness

    if (typeEffectiveness == 0.0 && move.hasType("Ground") && defender.hasItem("Iron Ball") && !defender.hasAbility("Klutz")) typeEffectiveness = 1.0
    if (typeEffectiveness == 0.0) return none

    if ((move.named("Steel Roller") && field.terrain == null) || (move.named("Poltergeist") && defender.item.isEmpty())) return none

    if ((move.hasType("Grass") && defender.hasAbility("Sap Sipper")) ||
        (move.hasType("Fire") && defender.hasAbility("Flash Fire")) ||
        (move.hasType("Water") && defender.hasAbility("Dry Skin", "Water Absorb")) ||
        (move.hasType("Electric") && defender.hasAbility("Lightning Rod", "Motor Drive", "Volt Absorb")) ||
        (move.hasType("Ground") && !field.isGravity && defender.hasAbility("Levitate", "Eelevate")) ||
        (move.flag("bullet") && defender.hasAbility("Bulletproof")) ||
        (move.flag("sound") && !move.named("Clangorous Soul") && defender.hasAbility("Soundproof")) ||
        (move.priority > 0 && defender.hasAbility("Queenly Majesty", "Armor Tail")) ||
        (move.hasType("Ground") && defender.hasAbility("Earth Eater"))
    ) return none

    if (move.hasType("Ground") && !field.isGravity && defender.hasItem("Air Balloon")) return none
    if (move.priority > 0 && field.hasTerrain("Psychic") && isGrounded(defender, field)) return none

    val fixedDamage = handleFixedDamageMoves(attacker, move)
    if (fixedDamage != 0) {
        return if (attacker.hasAbility("Parental Bond")) Damage.Rolls(listOf(fixedDamage, fixedDamage)) else Damage.Fixed(fixedDamage)
    }

    if (move.named("Final Gambit")) return Damage.Fixed(attacker.curHP())

    val basePower = calculateBasePower(rules, attacker, defender, move, field, hasAteAbilityTypeChange)
    if (basePower == 0) return none

    val attack = calculateAttack(attacker, defender, move, field, isCritical)
    val defense = calculateDefense(attacker, defender, move, field, isCritical)
    val baseDamage = calculateBaseDamage(attacker, basePower, attack, defense, move, field, isCritical)

    if (attacker.hasAbility("Gale Wings") && move.hasType("Flying") && attacker.curHP() == attacker.maxHP()) move.priority = 1

    // The random factor sits between the critical and STAB modifiers, so everything below is applied per roll.
    var stabMod = getStabMod(attacker, move)
    val applyBurn = attacker.hasStatus("brn") && move.category == "Physical" && !attacker.hasAbility("Guts") && !move.named("Facade")
    val finalMods = calculateFinalMods(rules, attacker, defender, move, field, isCritical, typeEffectiveness)

    val protect = field.defenderSide.isProtected && attacker.hasAbility("Unseen Fist", "Piercing Drill") && move.flag("contact")
    val finalMod = chainMods(finalMods, 41, 131072)
    val isSpread = field.gameType != "Singles" && move.target in listOf("allAdjacent", "allAdjacentFoes")

    var childDamage: Damage? = null
    if (attacker.hasAbility("Parental Bond") && move.hits == 1 && !isSpread) {
        val child = attacker.clone()
        child.ability = "Parental Bond (Child)"
        checkMultihitBoost(child, defender, move, field)
        // A child result of 0 is falsy upstream, leaving the parent's rolls alone.
        childDamage = calculateChampions(rules, child, defender, move, field).takeUnless { it == Damage.Fixed(0) }
    }

    val damage = List(16) { getFinalDamage(baseDamage, it, typeEffectiveness, applyBurn, stabMod, finalMod, protect) }
    var result: Damage = childDamage?.let { Damage.Matrix(listOf(Damage.Rolls(damage), it)) } ?: Damage.Rolls(damage)

    if (move.timesUsed > 1 || move.hits > 1) {
        val numAttacks = if (move.timesUsed > 1) move.timesUsed else move.hits
        var usedItems = false to false
        val damageMatrix = mutableListOf<Damage>(Damage.Rolls(damage))
        for (times in 1 until numAttacks) {
            usedItems = checkMultihitBoost(attacker, defender, move, field, usedItems.first, usedItems.second)
            val newAttack = calculateAttack(attacker, defender, move, field, isCritical)
            val newDefense = calculateDefense(attacker, defender, move, field, isCritical)
            // A lost -ate ability keeps the type but loses the boost.
            hasAteAbilityTypeChange = hasAteAbilityTypeChange && attacker.hasAbility("Aerilate", "Dragonize", "Pixilate", "Refrigerate")
            if (move.timesUsed > 1) stabMod = getStabMod(attacker, move)
            val newBasePower = calculateBasePower(rules, attacker, defender, move, field, hasAteAbilityTypeChange, times + 1)
            val newBaseDamage = calculateBaseDamage(attacker, newBasePower, newAttack, newDefense, move, field, isCritical)
            val newFinalMod = chainMods(calculateFinalMods(rules, attacker, defender, move, field, isCritical, typeEffectiveness, times), 41, 131072)
            damageMatrix += Damage.Rolls(List(16) { getFinalDamage(newBaseDamage, it, typeEffectiveness, applyBurn, stabMod, newFinalMod, protect) })
        }
        result = Damage.Matrix(damageMatrix)
    }
    return result
}

private fun calculateBasePower(
    rules: ChampionsRules, attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField,
    hasAteAbilityTypeChange: Boolean, hit: Int = 1,
): Int {
    val turnOrder = if (attacker.stats[SPE] > defender.stats[SPE]) "first" else "last"
    var basePower: Double = when (move.name) {
        "Payback" -> move.bp * (if (turnOrder == "last") 2.0 else 1.0)
        "Electro Ball" -> {
            val r = floor(attacker.stats[SPE].toDouble() / defender.stats[SPE])
            if (defender.stats[SPE] == 0) 40.0 else if (r >= 4) 150.0 else if (r >= 3) 120.0 else if (r >= 2) 80.0 else if (r >= 1) 60.0 else 40.0
        }
        "Gyro Ball" -> if (attacker.stats[SPE] == 0) 1.0 else min(150.0, floor(25.0 * defender.stats[SPE] / attacker.stats[SPE]) + 1)
        "Punishment" -> min(200.0, 60.0 + 20 * countBoosts(defender.boosts))
        "Low Kick", "Grass Knot" -> getWeight(defender).let { w ->
            if (w >= 200) 120.0 else if (w >= 100) 100.0 else if (w >= 50) 80.0 else if (w >= 25) 60.0 else if (w >= 10) 40.0 else 20.0
        }
        "Hex", "Infernal Parade" -> move.bp * (if (defender.status.isNotEmpty()) 2.0 else 1.0)
        "Barb Barrage" -> move.bp * (if (defender.hasStatus("psn", "tox")) 2.0 else 1.0)
        "Heavy Slam", "Heat Crash" -> (getWeight(attacker) / getWeight(defender)).let { wr ->
            if (wr >= 5) 120.0 else if (wr >= 4) 100.0 else if (wr >= 3) 80.0 else if (wr >= 2) 60.0 else 40.0
        }
        "Stored Power", "Power Trip" -> 20.0 + 20 * countBoosts(attacker.boosts)
        "Acrobatics" -> move.bp * (if (attacker.item.isEmpty()) 2.0 else 1.0)
        "Assurance" -> move.bp * (if (defender.hasAbility("Parental Bond (Child)")) 2.0 else 1.0)
        "Smelling Salts" -> move.bp * (if (defender.hasStatus("par")) 2.0 else 1.0)
        "Weather Ball" -> move.bp * (if (field.weather != null || attacker.hasAbility("Mega Sol")) 2.0 else 1.0)
        "Terrain Pulse" -> move.bp * (if (isGrounded(attacker, field) && field.terrain != null) 2.0 else 1.0)
        "Rising Voltage" -> move.bp * (if (isGrounded(defender, field) && field.hasTerrain("Electric")) 2.0 else 1.0)
        "Fling" -> rules.flingPower(attacker.item).toDouble()
        "Eruption", "Water Spout" -> max(1.0, floor(150.0 * attacker.curHP() / attacker.maxHP()))
        "Flail", "Reversal" -> floor(48.0 * attacker.curHP() / attacker.maxHP()).let { p ->
            if (p <= 1) 200.0 else if (p <= 4) 150.0 else if (p <= 9) 100.0 else if (p <= 16) 80.0 else if (p <= 32) 40.0 else 20.0
        }
        // Triple Axel's power rises with each consecutive hit (20, 40, 60).
        "Triple Axel" -> hit * 20.0
        "Hard Press" -> {
            val scaled = 100 * floor(defender.curHP() * 4096.0 / defender.maxHP())
            floor(floor((100 * scaled + 2048 - 1) / 4096) / 100).takeIf { it != 0.0 } ?: 1.0
        }
        else -> move.bp.toDouble()
    }
    if (basePower == 0.0) return 0
    val bpMods = calculateBPMods(rules, attacker, defender, move, field, basePower, hasAteAbilityTypeChange, turnOrder, hit)
    basePower = of16(max(1.0, pokeRound(basePower * chainMods(bpMods, 41, 2097152) / 4096)))
    return basePower.toInt()
}

private fun calculateBPMods(
    rules: ChampionsRules, attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField,
    basePower: Double, hasAteAbilityTypeChange: Boolean, turnOrder: String, hit: Int,
): List<Int> {
    val bpMods = mutableListOf<Int>()
    val defenderItem = defender.item.ifEmpty { defender.disabledItem }
    var resistedKnockOffDamage = defenderItem.isEmpty()
    // Only a Mega Stone that matches the holder (or an already Mega-Evolved holder) resists Knock Off.
    if (!resistedKnockOffDamage) {
        val megaStone = rules.megaStone(defenderItem)
        resistedKnockOffDamage = defender.name in megaStone.keys || defender.name in megaStone.values
    }
    // An item already knocked off stays resisted.
    if (!resistedKnockOffDamage && hit > 1 && !defender.hasAbility("Sticky Hold")) resistedKnockOffDamage = true

    if ((move.named("Facade") && attacker.hasStatus("brn", "par", "psn", "tox")) ||
        (move.named("Venoshock") && defender.hasStatus("psn", "tox")) ||
        (move.named("Lash Out") && countBoosts(attacker.boosts) < 0)
    ) bpMods += 8192
    else if (move.named("Expanding Force") && isGrounded(attacker, field) && field.hasTerrain("Psychic")) {
        move.target = "allAdjacentFoes"
        bpMods += 6144
    } else if ((move.named("Knock Off") && !resistedKnockOffDamage) ||
        (move.named("Misty Explosion") && isGrounded(attacker, field) && field.hasTerrain("Misty")) ||
        (move.named("Grav Apple") && field.isGravity)
    ) bpMods += 6144
    else if (move.named("Solar Beam", "Solar Blade") && field.hasWeather("Rain", "Sand", "Hail", "Snow") && !attacker.hasAbility("Mega Sol")) bpMods += 2048

    if (field.attackerSide.isHelpingHand) bpMods += 6144

    if (isGrounded(attacker, field) &&
        ((field.hasTerrain("Electric") && move.hasType("Electric")) || (field.hasTerrain("Grassy") && move.hasType("Grass")) ||
            (field.hasTerrain("Psychic") && move.hasType("Psychic")))
    ) bpMods += 5325
    if (isGrounded(defender, field) &&
        ((field.hasTerrain("Misty") && move.hasType("Dragon")) || (field.hasTerrain("Grassy") && move.named("Bulldoze", "Earthquake")))
    ) bpMods += 2048

    // Technician uses the power after move-specific changes.
    if ((attacker.hasAbility("Technician") && basePower <= 60) ||
        (attacker.hasAbility("Mega Launcher") && move.flag("pulse")) ||
        (attacker.hasAbility("Strong Jaw") && move.flag("bite")) ||
        (attacker.hasAbility("Steely Spirit") && move.hasType("Steel")) ||
        (attacker.hasAbility("Sharpness") && move.flag("slicing"))
    ) bpMods += 6144

    if (field.attackerSide.isCharge && move.hasType("Electric")) bpMods += 8192

    val aura = "${move.type} Aura"
    if (attacker.hasAbility(aura) || defender.hasAbility(aura) || (field.isFairyAura && move.type == "Fairy") || (field.isDarkAura && move.type == "Dark")) {
        bpMods += 5448
    }

    if ((attacker.hasAbility("Sheer Force") && (move.secondaries || move.named("Electro Shot"))) ||
        (attacker.hasAbility("Sand Force") && field.hasWeather("Sand") && move.hasType("Rock", "Ground", "Steel")) ||
        (attacker.hasAbility("Analytic") && (turnOrder != "first" || field.defenderSide.isSwitching == "out" || attacker.abilityOn)) ||
        (attacker.hasAbility("Tough Claws") && move.flag("contact")) ||
        (attacker.hasAbility("Punk Rock") && move.flag("sound"))
    ) bpMods += 5325

    if (attacker.hasAbility("Rivalry") && attacker.gender != "N" && defender.gender != "N") {
        bpMods += if (attacker.gender == defender.gender) 5120 else 3072
    }

    // -ate abilities already changed the type.
    if (hasAteAbilityTypeChange) bpMods += 4915

    if ((attacker.hasAbility("Reckless") && (move.recoil || move.hasCrashDamage)) || (attacker.hasAbility("Iron Fist") && move.flag("punch"))) {
        bpMods += 4915
    }

    if (defender.hasAbility("Dry Skin") && move.hasType("Fire")) bpMods += 5120

    if (attacker.hasAbility("Supreme Overlord") && attacker.alliesFainted != 0) {
        bpMods += intArrayOf(4096, 4506, 4915, 5325, 5734, 6144)[min(5, attacker.alliesFainted)]
    }

    if (attacker.hasItem("${move.type} Gem")) bpMods += 5325
    else if (attacker.item.isNotEmpty() && move.hasType(rules.itemBoostType(attacker.item))) bpMods += 4915
    else if ((attacker.hasItem("Muscle Band") && move.category == "Physical") || (attacker.hasItem("Wise Glasses") && move.category == "Special")) {
        bpMods += 4505
    }
    return bpMods
}

private fun calculateAttack(attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField, isCritical: Boolean): Int {
    val attackSource = if (move.named("Foul Play")) defender else attacker
    // Body Press in Wonder Room uses the normal Defense, which checkRawStatChanges moved to Sp. Def.
    val attackStat = if (move.named("Body Press")) (if (field.isWonderRoom) SPD else DEF) else if (move.category == "Special") SPA else ATK
    val boosts = attackSource.boosts[attackStat]
    var attack = if (boosts == 0 || (isCritical && boosts < 0) || defender.hasAbility("Unaware")) attackSource.rawStats[attackStat].toDouble()
    else getModifiedStat(attackSource.rawStats[attackStat], boosts).toDouble()

    // Unlike all other attack modifiers, Hustle applies directly.
    if (attacker.hasAbility("Hustle") && move.category == "Physical") attack = pokeRound(attack * 3 / 2)

    val atMods = calculateAtMods(attacker, defender, move, field)
    return of16(max(1.0, pokeRound(attack * chainMods(atMods, 410, 131072) / 4096))).toInt()
}

private fun calculateAtMods(attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField): List<Int> {
    val atMods = mutableListOf<Int>()
    if (attacker.hasAbility("Solar Power") && field.hasWeather("Sun") && move.category == "Special") atMods += 6144
    else if ((attacker.hasAbility("Guts") && attacker.status.isNotEmpty() && move.category == "Physical") ||
        (attacker.curHP() <= attacker.maxHP() / 3.0 &&
            ((attacker.hasAbility("Overgrow") && move.hasType("Grass")) || (attacker.hasAbility("Blaze") && move.hasType("Fire")) ||
                (attacker.hasAbility("Torrent") && move.hasType("Water")) || (attacker.hasAbility("Swarm") && move.hasType("Bug")))) ||
        (move.category == "Special" && attacker.abilityOn && attacker.hasAbility("Plus", "Minus"))
    ) atMods += 6144
    else if (attacker.hasAbility("Flash Fire") && attacker.abilityOn && move.hasType("Fire")) atMods += 6144
    else if (attacker.hasAbility("Fire Mane") && move.hasType("Fire")) atMods += 6144
    else if ((attacker.hasAbility("Water Bubble") && move.hasType("Water")) ||
        (attacker.hasAbility("Huge Power", "Pure Power") && move.category == "Physical")
    ) atMods += 8192
    else if (attacker.hasAbility("Stakeout") && attacker.abilityOn) atMods += 8192

    if ((defender.hasAbility("Thick Fat") && move.hasType("Fire", "Ice")) ||
        (defender.hasAbility("Water Bubble") && move.hasType("Fire")) ||
        (defender.hasAbility("Purifying Salt") && move.hasType("Ghost"))
    ) atMods += 2048
    if (defender.hasAbility("Heatproof") && move.hasType("Fire")) atMods += 2048
    if (attacker.hasItem("Light Ball") && "Pikachu" in attacker.name) atMods += 8192
    return atMods
}

private fun calculateDefense(attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField, isCritical: Boolean): Int {
    val hitsPhysical = move.overrideDefensiveStat == "def" || move.category == "Physical"
    val defenseStat = if (hitsPhysical) DEF else SPD
    val boosts = defender.boosts[defenseStat]
    var defense = if (boosts == 0 || (isCritical && boosts > 0) || move.ignoreDefensive || attacker.hasAbility("Unaware")) defender.rawStats[defenseStat].toDouble()
    else getModifiedStat(defender.rawStats[defenseStat], boosts).toDouble()

    // Unlike all other defense modifiers, the weather boosts apply directly.
    if (!attacker.hasAbility("Mega Sol")) {
        if (field.hasWeather("Sand") && defender.hasType("Rock") && !hitsPhysical) defense = pokeRound(defense * 3 / 2)
        if (field.hasWeather("Snow") && defender.hasType("Ice") && hitsPhysical) defense = pokeRound(defense * 3 / 2)
    }

    val dfMods = mutableListOf<Int>()
    if (defender.hasAbility("Marvel Scale") && defender.status.isNotEmpty() && hitsPhysical) dfMods += 6144
    else if (defender.hasAbility("Grass Pelt") && field.hasTerrain("Grassy") && hitsPhysical) dfMods += 6144
    else if (defender.hasAbility("Fur Coat") && hitsPhysical) dfMods += 8192
    return of16(max(1.0, pokeRound(defense * chainMods(dfMods, 410, 131072) / 4096))).toInt()
}

private fun calculateBaseDamage(
    attacker: CalcPokemon, basePower: Int, attack: Int, defense: Int, move: CalcMove, field: CalcField, isCritical: Boolean,
): Double {
    var baseDamage = getBaseDamage(attacker.level, basePower, attack, defense)
    if (field.gameType != "Singles" && move.target in listOf("allAdjacent", "allAdjacentFoes")) baseDamage = pokeRound(of32(baseDamage * 3072) / 4096)
    if (attacker.hasAbility("Parental Bond (Child)")) baseDamage = pokeRound(of32(baseDamage * 1024) / 4096)

    val isMegaSol = attacker.hasAbility("Mega Sol")
    if (((field.hasWeather("Sun") || isMegaSol) && move.hasType("Fire")) || ((field.hasWeather("Rain") && !isMegaSol) && move.hasType("Water"))) {
        baseDamage = pokeRound(of32(baseDamage * 6144) / 4096)
    } else if (((field.hasWeather("Sun") || isMegaSol) && move.hasType("Water")) || (field.hasWeather("Rain") && move.hasType("Fire"))) {
        baseDamage = pokeRound(of32(baseDamage * 2048) / 4096)
    }
    if (isCritical) baseDamage = floor(of32(baseDamage * 1.5))
    return baseDamage
}

private fun calculateFinalMods(
    rules: ChampionsRules, attacker: CalcPokemon, defender: CalcPokemon, move: CalcMove, field: CalcField,
    isCritical: Boolean, typeEffectiveness: Double, hitCount: Int = 0,
): List<Int> {
    val finalMods = mutableListOf<Int>()
    val screen = if (field.gameType != "Singles") 2732 else 2048
    // Reflect and Light Screen do not stack with Aurora Veil.
    if (field.defenderSide.isReflect && move.category == "Physical" && !isCritical && !field.defenderSide.isAuroraVeil) finalMods += screen
    else if (field.defenderSide.isLightScreen && move.category == "Special" && !isCritical && !field.defenderSide.isAuroraVeil) finalMods += screen
    if (field.defenderSide.isAuroraVeil && !isCritical) finalMods += screen

    if (attacker.hasAbility("Sniper") && isCritical) finalMods += 6144

    if (defender.hasAbility("Multiscale") && defender.curHP() == defender.maxHP() && hitCount == 0 &&
        (!field.defenderSide.isSR && (field.defenderSide.spikes == 0 || defender.hasType("Flying"))) &&
        !attacker.hasAbility("Parental Bond (Child)")
    ) finalMods += 2048

    if ((defender.hasAbility("Fluffy") || defender.hasAbility("Aura Guard")) && move.flag("contact") && !attacker.hasAbility("Long Reach")) finalMods += 2048
    else if (defender.hasAbility("Punk Rock") && move.flag("sound")) finalMods += 2048

    if (defender.hasAbility("Solid Rock", "Filter") && typeEffectiveness > 1) finalMods += 3072
    if (field.defenderSide.isFriendGuard) finalMods += 3072
    if (defender.hasAbility("Fluffy") && move.hasType("Fire")) finalMods += 8192

    // Metronome needs consecutive uses, which the calculator does not model.
    if (attacker.hasItem("Expert Belt") && typeEffectiveness > 1) finalMods += 4915
    else if (attacker.hasItem("Life Orb")) finalMods += 5324

    if (move.hasType(rules.berryResistType(defender.item)) && (typeEffectiveness > 1 || move.hasType("Normal")) &&
        hitCount == 0 && !attacker.hasAbility("Unnerve")
    ) finalMods += if (defender.hasAbility("Ripen")) 1024 else 2048
    return finalMods
}

internal fun selfDrop(category: String, selfBoosts: Map<String, Int>): Int =
    selfBoosts[if (category == "Special") "spa" else "atk"]?.takeIf { it < 0 }?.let(::abs) ?: 0
