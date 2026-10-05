package dev.champions.dex

val TypeNames = listOf("一般", "格斗", "飞行", "毒", "地面", "岩石", "虫", "幽灵", "钢", "火", "水", "草", "电", "超能力", "冰", "龙", "恶", "妖精")

// —— Type chart (attacking type → defending type), indexed like [TypeNames]. ——

private val chartSource = mapOf(
    "一般" to Triple(listOf(), listOf("岩石", "钢"), listOf("幽灵")),
    "格斗" to Triple(listOf("一般", "冰", "岩石", "恶", "钢"), listOf("毒", "飞行", "超能力", "虫", "妖精"), listOf("幽灵")),
    "飞行" to Triple(listOf("格斗", "虫", "草"), listOf("岩石", "钢", "电"), listOf()),
    "毒" to Triple(listOf("草", "妖精"), listOf("毒", "地面", "岩石", "幽灵"), listOf("钢")),
    "地面" to Triple(listOf("火", "电", "毒", "岩石", "钢"), listOf("草", "虫"), listOf("飞行")),
    "岩石" to Triple(listOf("火", "冰", "飞行", "虫"), listOf("格斗", "地面", "钢"), listOf()),
    "虫" to Triple(listOf("草", "超能力", "恶"), listOf("火", "格斗", "毒", "飞行", "幽灵", "钢", "妖精"), listOf()),
    "幽灵" to Triple(listOf("超能力", "幽灵"), listOf("恶"), listOf("一般")),
    "钢" to Triple(listOf("冰", "岩石", "妖精"), listOf("火", "水", "电", "钢"), listOf()),
    "火" to Triple(listOf("草", "冰", "虫", "钢"), listOf("火", "水", "岩石", "龙"), listOf()),
    "水" to Triple(listOf("火", "地面", "岩石"), listOf("水", "草", "龙"), listOf()),
    "草" to Triple(listOf("水", "地面", "岩石"), listOf("火", "草", "毒", "飞行", "虫", "龙", "钢"), listOf()),
    "电" to Triple(listOf("水", "飞行"), listOf("电", "草", "龙"), listOf("地面")),
    "超能力" to Triple(listOf("格斗", "毒"), listOf("超能力", "钢"), listOf("恶")),
    "冰" to Triple(listOf("草", "地面", "飞行", "龙"), listOf("火", "水", "冰", "钢"), listOf()),
    "龙" to Triple(listOf("龙"), listOf("钢"), listOf("妖精")),
    "恶" to Triple(listOf("超能力", "幽灵"), listOf("格斗", "恶", "妖精"), listOf()),
    "妖精" to Triple(listOf("格斗", "龙", "恶"), listOf("火", "毒", "钢"), listOf()),
)

private val chart: Array<DoubleArray> = Array(18) { at ->
    val (sup, half, zero) = chartSource.getValue(TypeNames[at])
    DoubleArray(18) { dt -> val n = TypeNames[dt]; when (n) { in zero -> 0.0; in sup -> 2.0; in half -> .5; else -> 1.0 } }
}

fun effectiveness(attack: Int, defend: Int) = chart[attack][defend]
fun effectiveness(attack: Int, defenders: List<Int>) = defenders.distinct().fold(1.0) { m, d -> m * chart[attack][d] }
