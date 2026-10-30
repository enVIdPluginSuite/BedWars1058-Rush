package dev.etran.bedwarsrush.compat

import org.bukkit.potion.PotionEffectType

/** Potion effect lookup that understands both the pre- and post-1.20.5 effect names. */
object Effects {
    private val renamed: Map<String, List<String>> = mapOf(
        "FAST_DIGGING" to listOf("HASTE"),
        "HASTE" to listOf("FAST_DIGGING"),
        "SLOW_DIGGING" to listOf("MINING_FATIGUE"),
        "MINING_FATIGUE" to listOf("SLOW_DIGGING"),
        "INCREASE_DAMAGE" to listOf("STRENGTH"),
        "STRENGTH" to listOf("INCREASE_DAMAGE"),
        "DAMAGE_RESISTANCE" to listOf("RESISTANCE"),
        "RESISTANCE" to listOf("DAMAGE_RESISTANCE"),
        "JUMP" to listOf("JUMP_BOOST"),
        "JUMP_BOOST" to listOf("JUMP"),
        "CONFUSION" to listOf("NAUSEA"),
        "NAUSEA" to listOf("CONFUSION"),
        "HEAL" to listOf("INSTANT_HEALTH"),
        "INSTANT_HEALTH" to listOf("HEAL"),
        "HARM" to listOf("INSTANT_DAMAGE"),
        "INSTANT_DAMAGE" to listOf("HARM"),
    )

    fun resolve(name: String): PotionEffectType? {
        val key = name.trim().uppercase().removePrefix("MINECRAFT:").replace(' ', '_')
        return (listOf(key) + (renamed[key] ?: emptyList())).firstNotNullOfOrNull { PotionEffectType.getByName(it) }
    }
}
