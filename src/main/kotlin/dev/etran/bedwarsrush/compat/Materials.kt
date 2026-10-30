package dev.etran.bedwarsrush.compat

import org.bukkit.Material
import java.util.concurrent.ConcurrentHashMap

/** A block type plus the legacy data value (always 0 on 1.13+). */
data class BlockSpec(val material: Material, val data: Byte = 0)

/**
 * Resolves block names to [Material]s on every supported version.
 *
 * Why this exists: the plugin is compiled against Bukkit 1.8.8 but must run on servers up to 26.x. Constants such as
 * `Material.WOOL` or `Material.OAK_PLANKS` only exist on one side of the 1.13 "flattening", so everything is looked up
 * by *name* at runtime, and team colours are expressed as `RED_WOOL` (1.13+) or `WOOL:14` (1.8 - 1.12).
 */
object Materials {
    private data class Dye(val legacyData: Int, val modernName: String)

    // BedWars1058 team colour -> dye. AQUA is cyan and GRAY is light gray in BedWars1058's own palette.
    private val dyes: Map<String, Dye> = mapOf(
        "WHITE" to Dye(0, "WHITE"),
        "ORANGE" to Dye(1, "ORANGE"),
        "MAGENTA" to Dye(2, "MAGENTA"),
        "LIGHT_BLUE" to Dye(3, "LIGHT_BLUE"),
        "YELLOW" to Dye(4, "YELLOW"),
        "LIME" to Dye(5, "LIME"),
        "GREEN" to Dye(5, "LIME"),
        "PINK" to Dye(6, "PINK"),
        "DARK_GRAY" to Dye(7, "GRAY"),
        "GRAY" to Dye(8, "LIGHT_GRAY"),
        "LIGHT_GRAY" to Dye(8, "LIGHT_GRAY"),
        "AQUA" to Dye(9, "CYAN"),
        "CYAN" to Dye(9, "CYAN"),
        "PURPLE" to Dye(10, "PURPLE"),
        "BLUE" to Dye(11, "BLUE"),
        "BROWN" to Dye(12, "BROWN"),
        "DARK_GREEN" to Dye(13, "GREEN"),
        "RED" to Dye(14, "RED"),
        "BLACK" to Dye(15, "BLACK"),
    )

    /** Alternative names for the same block across versions. Tried in order after the requested name. */
    private val aliases: Map<String, List<String>> = mapOf(
        "WOOD" to listOf("OAK_PLANKS"),
        "PLANKS" to listOf("OAK_PLANKS", "WOOD"),
        "OAK_PLANKS" to listOf("WOOD"),
        "END_STONE" to listOf("ENDER_STONE"),
        "ENDER_STONE" to listOf("END_STONE"),
        "HARD_CLAY" to listOf("TERRACOTTA"),
        "TERRACOTTA" to listOf("HARD_CLAY"),
        "COBWEB" to listOf("WEB"),
        "WEB" to listOf("COBWEB"),
        "LOG" to listOf("OAK_LOG"),
        "OAK_LOG" to listOf("LOG"),
        "SLAB" to listOf("STONE_SLAB", "STEP"),
        "BED" to listOf("RED_BED", "BED_BLOCK"),
    )

    // kind -> (legacy material name, modern suffix)
    private val coloredKinds: Map<String, Pair<String, String>> = mapOf(
        "WOOL" to ("WOOL" to "WOOL"),
        "GLASS" to ("STAINED_GLASS" to "STAINED_GLASS"),
        "STAINED_GLASS" to ("STAINED_GLASS" to "STAINED_GLASS"),
        "GLASS_PANE" to ("STAINED_GLASS_PANE" to "STAINED_GLASS_PANE"),
        "TERRACOTTA" to ("STAINED_CLAY" to "TERRACOTTA"),
        "CLAY" to ("STAINED_CLAY" to "TERRACOTTA"),
        "STAINED_CLAY" to ("STAINED_CLAY" to "TERRACOTTA"),
        "CONCRETE" to ("CONCRETE" to "CONCRETE"), // 1.12+; falls back to wool below
        "CARPET" to ("CARPET" to "CARPET"),
    )

    private val cache = ConcurrentHashMap<String, Material>()
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Resolves a plain material name (no team colour), trying known aliases. Returns null if it does not exist. */
    fun resolve(name: String): Material? {
        val key = normalise(name)
        cache[key]?.let { return it }
        if (key in missing) return null
        val found = (listOf(key) + (aliases[key] ?: emptyList())).firstNotNullOfOrNull { Material.getMaterial(it) }
        if (found == null) missing.add(key) else cache[key] = found
        return found
    }

    /**
     * Turns a config token into a placeable block for a team.
     *
     * Tokens `WOOL`, `GLASS`, `GLASS_PANE`, `TERRACOTTA`, `CONCRETE` and `CARPET` become the team-coloured variant;
     * `WOOD`/`PLANKS` become oak planks; anything else is looked up as a literal material name.
     */
    fun block(token: String, teamColor: String): BlockSpec? {
        val key = normalise(token)
        val kind = coloredKinds[key]
        if (kind != null) return colored(kind, teamColor, key)
        val material = resolve(key) ?: return null
        return BlockSpec(material, 0)
    }

    private fun colored(kind: Pair<String, String>, teamColor: String, key: String): BlockSpec? {
        val dye = dyes[teamColor.uppercase()] ?: dyes.getValue("WHITE")
        if (!ServerVersion.current.isLegacyMaterials) {
            val material = Material.getMaterial("${dye.modernName}_${kind.second}") ?: return null
            return BlockSpec(material, 0)
        }
        // 1.8 - 1.12: one material + a data value. Concrete only exists from 1.12, so degrade to wool before that.
        val material = Material.getMaterial(kind.first)
            ?: (if (key == "CONCRETE") Material.getMaterial("WOOL") else null)
            ?: return null
        return BlockSpec(material, dye.legacyData.toByte())
    }

    /** True for any bed block on any version (`RED_BED` on 1.13+, `BED_BLOCK` on 1.8 - 1.12). */
    fun isBed(material: Material): Boolean {
        val n = material.name
        return n == "BED_BLOCK" || n == "BED" || n.endsWith("_BED")
    }

    fun isAir(material: Material): Boolean {
        val n = material.name
        return n == "AIR" || n == "CAVE_AIR" || n == "VOID_AIR"
    }

    fun isLiquid(material: Material): Boolean {
        val n = material.name
        return n == "WATER" || n == "LAVA" || n == "STATIONARY_WATER" || n == "STATIONARY_LAVA"
    }

    private fun normalise(name: String): String =
        name.trim().uppercase().removePrefix("MINECRAFT:").replace(' ', '_')
}
