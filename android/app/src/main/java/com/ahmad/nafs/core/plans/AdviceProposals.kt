package com.ahmad.nafs.core.plans

import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Only complete, validated suggestions are offered for explicit creation. */
object AdviceProposals {
    fun key(result: JsonObject): String = "advice-items/" + MessageDigest.getInstance("SHA-256")
        .digest(result.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun parse(result: JsonObject): List<PlanProposal> {
        val pack = result["starter_pack"] as? JsonObject ?: result["recommendation"] as? JsonObject ?: return emptyList()
        fun text(row: JsonObject, key: String) = (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        val reason = text(pack, "summary").take(300).takeIf { it.length >= 10 } ?: "Suggested by your saved Coach advice."
        val proposals = buildList {
            (pack["suggested_tasks"] as? JsonArray).orEmpty().take(20).forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                add(PlanProposal("task", text(row, "title"), "📋", reason, task = PlanTask(text(row, "priority"))))
            }
            listOf("suggested_habits", "related_habits", "supporting_habits").forEach { field ->
                (pack[field] as? JsonArray).orEmpty().take(20).forEach habit@{ element ->
                    val row = element as? JsonObject ?: return@habit
                    val type = text(row, "type").let { if (it == "boolean") "simple" else it }
                    add(PlanProposal("habit", text(row, "name"), text(row, "emoji"), reason,
                        habit = PlanHabit(type, (row["target_value"] as? JsonPrimitive)?.doubleOrNull,
                            text(row, "unit").takeIf(String::isNotBlank), (row["time_target_mins"] as? JsonPrimitive)?.intOrNull, "daily")))
                }
            }
            (pack["suggested_challenges"] as? JsonArray).orEmpty().take(20).forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                add(PlanProposal("challenge", text(row, "title"), text(row, "emoji"), text(row, "reason"),
                    challenge = PlanChallenge(text(row, "frequency"), (row["duration_days"] as? JsonPrimitive)?.intOrNull ?: 0, false)))
            }
        }
        return proposals.filter(RecoveryRules::validStep).distinct().take(20)
    }
}
