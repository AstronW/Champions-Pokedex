package dev.champions.dex

import org.json.JSONObject

data class MoveCategory(
    val id: String,
    val name: String,
    val group: String,
    val revision: String,
    val url: String,
) {
    val key get() = "category:$id"
}

data class CategoryRelation(
    val categoryId: String,
    val entityKey: String,
    val relation: String,
    val section: String,
)

data class SpecialFlag(
    val field: String,
    val value: Boolean?,
    val text: String,
    val origin: String,
) {
    // Some explicit false/default flags intentionally have no prose in the Wiki template.
    val displayText get() =
        if (value == null) {
            "百科未标明"
        } else {
            text.ifBlank { if (value) "是" else "否" } + if (origin == "template_default") "（百科默认）" else ""
        }
}

data class MoveTarget(
    val text: String,
    val wikiCode: String,
    val gameCode: String,
)

data class StatusGameText(
    val name: String,
    val english: String,
    val description: String,
)

data class BattleStatus(
    val key: String,
    val bodyKey: String,
    val name: String,
    val kind: String,
    val aliases: List<String>,
    val game: List<StatusGameText>,
    val section: String,
) {
    /** The list and page already say these are statuses, so the wiki's "（状态）" disambiguator is dropped. */
    val displayName get() = name.removeSuffix("（状态）")
    val searchText = (listOf(name) + aliases + game.flatMap { listOf(it.name, it.english) }).joinToString(" ").lowercase()
    val kindLabel get() =
        when (kind) {
            "status_page" -> "状态"
            "weather_page" -> "天气"
            "redirected_definition" -> "招式中的状态定义"
            else -> "状态概述"
        }
}

data class StatusReference(
    val bodyKey: String,
    val entityKey: String,
    val field: String,
    val section: String,
) {
    // Full-page references can be historical/other-game mentions; never promote them to current effects.
    val inDescription get() = field in setOf("game_description", "effect_original", "effect_expanded")
}

data class Mechanics(
    val categories: List<MoveCategory> = emptyList(),
    val relations: List<CategoryRelation> = emptyList(),
    val flags: Map<String, List<SpecialFlag>> = emptyMap(),
    val targets: Map<String, MoveTarget> = emptyMap(),
    val statuses: List<BattleStatus> = emptyList(),
    val references: List<StatusReference> = emptyList(),
    val issues: Map<String, List<String>> = emptyMap(),
) {
    val categoriesById = categories.associateBy { it.id }
    val relationsByEntity = relations.groupBy { it.entityKey }
    val relationsByCategory = relations.groupBy { it.categoryId }
    val statusesByKey = statuses.associateBy { it.key }
    val statusesByBody = statuses.associateBy { it.bodyKey }
    val referencesByEntity = references.groupBy { it.entityKey }
    val referencesByBody = references.groupBy { it.bodyKey }
}

/** Parse the versioned champout export without collapsing unknown flags to false. */
fun parseMechanics(j: JSONObject): Mechanics =
    Mechanics(
        categories =
            j.getJSONArray("categories").objects {
                MoveCategory(
                    it.getString("category_key"),
                    it.getString("name_zh"),
                    it.getString("group_zh"),
                    it.getString("revision_id"),
                    it.getString("source_url"),
                )
            },
        relations =
            j
                .getJSONArray("relations")
                .objects {
                    CategoryRelation(
                        it.getString("category_key"),
                        it.getString("entity_key"),
                        it.getString("relation"),
                        it.getString("section"),
                    )
                }.distinct(),
        flags =
            j
                .getJSONArray("flags")
                .objects {
                    it.getString("entity_key") to
                        SpecialFlag(
                            it.getString("field"),
                            when (it.getString("value")) {
                                "true" -> true
                                "false" -> false
                                else -> null
                            },
                            it.getString("text_zh"),
                            it.getString("value_origin"),
                        )
                }.groupBy({ it.first }, { it.second }),
        targets =
            j
                .getJSONArray("targets")
                .objects {
                    it.getString("entity_key") to
                        MoveTarget(it.getString("target_text_zh"), it.getString("wiki_target"), it.getString("game_target"))
                }.toMap(),
        statuses =
            j.getJSONArray("statuses").objects {
                BattleStatus(
                    it.getString("key"),
                    it.getString("bodyKey"),
                    it.getString("name"),
                    it.getString("kind"),
                    it.getJSONArray("aliases").strings(),
                    it.getJSONArray("game").objects { g ->
                        StatusGameText(g.getString("name"), g.getString("english"), g.getString("description"))
                    },
                    it.getString("section"),
                )
            },
        references =
            j
                .getJSONArray("references")
                .objects {
                    StatusReference(
                        it.getString("body_key"),
                        it.getString("entity_key"),
                        it.getString("source_field"),
                        it.getString("source_section"),
                    )
                }.distinct(),
        issues =
            j
                .getJSONArray(
                    "issues",
                ).objects { it.getString("subject") to it.getString("detail") }
                .groupBy({ it.first }, { it.second }),
    )

/** Out-of-battle use has no meaning in Champions, so it is never listed. */
val HiddenSpecialFlags = setOf("field_use")

val SpecialFlagLabels =
    mapOf(
        "contact" to "接触",
        "protect" to "守住",
        "magic_coat" to "魔法反射",
        "snatch" to "抢夺",
        "mirror_move" to "鹦鹉学舌",
        "kings_rock" to "王者之证",
        "field_use" to "对战外使用",
        "distance_ignored" to "无视距离",
        "skill_swap" to "交换",
        "replaceable" to "覆盖",
        "copyable" to "复制",
        "suppressible" to "无效化",
        "mold_breaker" to "破格",
        "transform" to "变身",
        "entry" to "入场发动",
        "once_per_entry" to "每次入场仅一次",
        "once_per_battle" to "每场对战仅一次",
    )
