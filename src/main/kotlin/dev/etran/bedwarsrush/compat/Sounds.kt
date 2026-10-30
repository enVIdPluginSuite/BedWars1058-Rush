package dev.etran.bedwarsrush.compat

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Sounds without touching `org.bukkit.Sound`.
 *
 * `Sound` stopped being a plain enum in newer Paper/Spigot builds, so calling `Sound.valueOf(...)` from a plugin compiled
 * against an old API throws `IncompatibleClassChangeError`. Playing a sound by its resource-pack *name* works on every
 * version, but those names changed twice (1.9 and 1.13), hence the table below.
 */
object Sounds {
    private enum class Family { CLOTH, GLASS, WOOD, STONE }

    private fun familyOf(material: Material): Family {
        val n = material.name
        return when {
            n.contains("GLASS") -> Family.GLASS
            n.contains("WOOL") || n.contains("CARPET") -> Family.CLOTH
            n.contains("PLANKS") || n.contains("LOG") || n == "WOOD" -> Family.WOOD
            else -> Family.STONE
        }
    }

    /**
     * @param configured `AUTO`, `NONE`, or a raw sound name such as `block.stone.place`
     * @return the sound name to play, or null for silence
     */
    fun placeSound(configured: String, placed: Material): String? {
        val c = configured.trim()
        if (c.isEmpty() || c.equals("NONE", ignoreCase = true)) return null
        if (!c.equals("AUTO", ignoreCase = true)) return c

        val v = ServerVersion.current
        val family = familyOf(placed)
        return when {
            v.isV1_8 -> when (family) {
                Family.CLOTH -> "dig.cloth"
                Family.GLASS -> "dig.glass"
                Family.WOOD -> "dig.wood"
                Family.STONE -> "dig.stone"
            }
            v.isLegacyMaterials -> when (family) { // 1.9 - 1.12
                Family.CLOTH -> "block.cloth.place"
                Family.GLASS -> "block.glass.place"
                Family.WOOD -> "block.wood.place"
                Family.STONE -> "block.stone.place"
            }
            else -> when (family) { // 1.13+
                Family.CLOTH -> "block.wool.place"
                Family.GLASS -> "block.glass.place"
                Family.WOOD -> "block.wood.place"
                Family.STONE -> "block.stone.place"
            }
        }
    }

    fun play(player: Player, at: Location, sound: String?, volume: Float, pitch: Float) {
        if (sound == null) return
        try {
            player.playSound(at, sound, volume, pitch)
        } catch (_: Throwable) {
            // A bad custom sound name must never break gameplay.
        }
    }
}
