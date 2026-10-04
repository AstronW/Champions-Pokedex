package dev.champions.dex

data class Pokemon(
    val id: String, val no: Int, val formId: Int, val name: String, val english: String,
    val form: String, val formEnglish: String, val types: List<Int>, val stats: List<Int>,
    val abilities: List<Int>, val weight: Int, val sex: Int, val sprite: String, val shiny: String,
) {
    val key get() = "pokemon:$id"
    val displayName get() = when { form.isBlank() -> name; name in form -> form; else -> "$name · $form" }
    val total get() = stats.sum()
    val searchText = "$name $english $form $formEnglish $no ${no.toString().padStart(4, '0')}".lowercase()
}

data class Move(
    val id: Int, val name: String, val english: String, val description: String,
    val available: Boolean, val type: Int, val category: Int, val power: Int,
    val accuracy: Int, val pp: Int, val priority: Int, val contact: Boolean,
) {
    val key get() = "move:$id"
    val searchText = "$name $english $id".lowercase()
    val powerLabel get() = when { power == 0 -> "—"; power == 1 -> "变化"; else -> "$power" }
    val accuracyLabel get() = if (accuracy in 1..100) "$accuracy%" else "—"
    val categoryLabel get() = listOf("物理", "特殊", "变化").getOrElse(category) { "未知" }
}

data class Ability(val id: Int, val name: String, val english: String, val description: String) {
    val key get() = "ability:$id"
    val searchText = "$name $english $id".lowercase()
}

data class WikiPage(val key: String, val file: String, val title: String, val revision: String, val url: String)

data class Dex(
    val pokemon: List<Pokemon>, val moves: List<Move>, val abilities: List<Ability>,
    val learnsets: Map<String, List<Int>>, val wiki: Map<String, WikiPage>,
    val speciesCount: Int, val fingerprint: String, val pipeline: String, val spriteCommit: String,
) {
    val pokemonById = pokemon.associateBy { it.id }
    val movesById = moves.associateBy { it.id }
    val abilitiesById = abilities.associateBy { it.id }
    val forms = pokemon.groupBy { it.no }
    // Species lists choose the lowest game form ID, independently of PokeAPI defaults.
    val species = forms.values.map { variants -> variants.minBy { it.formId } }
    val moveUsers = pokemon.flatMap { p -> learnsets[p.id].orEmpty().map { it to p } }.groupBy({ it.first }, { it.second })
    val abilityUsers = pokemon.flatMap { p -> p.abilities.distinct().map { it to p } }.groupBy({ it.first }, { it.second })
}

/** Drawer destinations; each keeps its own saved state while the app is open. */
enum class DexRoot(val title: String, val group: String) {
    POKEMON("宝可梦", "资料"), MOVES("招式", "资料"), ABILITIES("特性", "资料"), TYPES("属性克制", "资料"),
    CALCULATOR("伤害计算", "对战工具"), SPEED("速度线", "对战工具"), TEAM("队伍构建", "对战工具"),
    FAVORITES("收藏与对比", "我的"),
}

enum class Appearance(val label: String) { SYSTEM("跟随系统"), LIGHT("亮色"), DARK("暗色") }
enum class SortOrder(val label: String, val short: String) { NUMBER("图鉴顺序", "编号"), NAME("名称", "名称"), TOTAL("种族值总和", "总和"), SPEED("速度", "速度") }
enum class MoveSort(val label: String) { DEFAULT("默认"), POWER("威力"), ACCURACY("命中"), NAME("名称") }

/** Move filters shared by the move catalogue and a Pokémon's learnset. Unavailable moves stay reachable on request. */
fun filterMoves(moves: List<Move>, query: String, type: Int?, category: Int?, includeUnavailable: Boolean, sort: MoveSort): List<Move> {
    val matching = moves.filter { (it.available || includeUnavailable) && it.searchText.matchesQuery(query) &&
        (type == null || it.type == type) && (category == null || it.category == category) }
    return when (sort) {
        MoveSort.DEFAULT -> matching
        MoveSort.POWER -> matching.sortedByDescending { if (it.power > 1) it.power else 0 }
        MoveSort.ACCURACY -> matching.sortedByDescending { if (it.accuracy in 1..100) it.accuracy else 0 }
        MoveSort.NAME -> matching.sortedBy { it.name }
    }
}

/** Champions level-50 actual stat: IV 31, 0–32 stat points, each point adds one. */
fun actualStat(base: Int, points: Int, index: Int, naturePercent: Int = 100) =
    if (index == 0) base + 75 + points else (base + 20 + points) * naturePercent / 100

val StatLabels = listOf("HP", "攻击", "防御", "特攻", "特防", "速度")

fun String.matchesQuery(query: String) = query.trim().lowercase().split(Regex("\\s+")).all { it in this }

fun filterPokemon(dex: Dex, query: String, type: Int?, allForms: Boolean, favoritesOnly: Boolean, favorites: Set<String>, sort: SortOrder): List<Pokemon> {
    // Searching a form name or favorites must find the exact form, even in species mode.
    val source = if (allForms || query.isNotBlank() || favoritesOnly || type!=null) dex.pokemon else dex.species
    val matching = source.filter { p ->
        p.searchText.matchesQuery(query) && (type == null || type in p.types) && (!favoritesOnly || p.key in favorites)
    }
    val filtered = if(!allForms && query.isBlank() && !favoritesOnly) matching.distinctBy { it.no } else matching
    return when (sort) {
        SortOrder.NUMBER -> filtered.sortedWith(compareBy<Pokemon> { it.no }.thenBy { it.formId })
        SortOrder.NAME -> filtered.sortedBy { it.name }
        SortOrder.TOTAL -> filtered.sortedByDescending { it.total }
        SortOrder.SPEED -> filtered.sortedByDescending { it.stats[5] }
    }
}
