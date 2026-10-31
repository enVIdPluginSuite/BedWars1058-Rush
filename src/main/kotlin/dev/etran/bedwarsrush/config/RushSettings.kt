package dev.etran.bedwarsrush.config

/** Duration value used by the BedWars1058 API for "never expires". */
const val INFINITE_TICKS = Int.MAX_VALUE

data class EffectSpec(val type: String, val amplifier: Int, val durationTicks: Int)

// ---------------------------------------------------------------- game ---

data class GameSettings(
    /** Delay after the game starts before effects / upgrades / generators are applied. */
    val startDelayTicks: Int,
    /** `BedWars1058` NextEvent name to jump to at start (`BEDS_DESTROY`...), or null to leave the schedule alone. */
    val nextEvent: String?,
    val tutorial: Boolean,
    /** BedWars1058 team upgrade key -> tier index (e.g. `upgrade-forge` -> 3). */
    val teamUpgrades: Map<String, Int>,
    /** Effects every team member keeps for the whole game. */
    val teamEffects: List<EffectSpec>,
    /** Effects applied only while standing at the team's base. */
    val baseEffects: List<EffectSpec>,
)

// ----------------------------------------------------------- bed defense ---

enum class ReplaceMode {
    /** Only fill air. */
    AIR,

    /** Fill air, water and lava. */
    AIR_AND_LIQUID,

    /** Replace everything except beds. */
    ANY_EXCEPT_BED,
}

enum class BedShape {
    /** Gem: a layer is every block exactly N straight steps (no diagonal steps) from the bed. Half an octahedron. */
    DIAMOND,

    /** Box: a layer is the hollow cuboid N blocks out, corners included. */
    BOX,
}

/** One concentric shell (or several, if [thickness] > 1). [material] is a block name or a team-coloured token. */
data class LayerSpec(val material: String, val thickness: Int)

data class BedDefenseSettings(
    val enabled: Boolean,
    val delayTicks: Int,
    val layers: List<LayerSpec>,
    val shape: BedShape,
    val replace: ReplaceMode,
    val includeBelow: Boolean,
    val skipProtected: Boolean,
    val maxBlocksPerTeam: Int,
)

// ---------------------------------------------------------------- bridge ---

enum class BridgeToggle {
    /** Left-click with a bridge block in hand to switch bridging on/off. */
    LEFT_CLICK,

    /** Bridging is always on; no toggle. */
    ALWAYS,
}

data class BridgeSettings(
    val enabled: Boolean,
    val toggle: BridgeToggle,
    /** Blocks added automatically after the one the player placed. */
    val extraBlocks: Int,
    val intervalTicks: Int,
    /** Wildcard patterns over `Material` names, e.g. `*WOOL`. */
    val materials: List<String>,
    val consumeItems: Boolean,
    val sound: String,
    val volume: Float,
    val pitch: Float,
    val actionBar: Boolean,
    val chat: Boolean,
)

// ------------------------------------------------------------ generators ---

/**
 * Tweaks for one of BedWars1058's own generators. Every field is optional: `null` means "leave BedWars1058's value".
 * Built-in generators count in whole seconds (BedWars1058 limitation); use [ExtraGeneratorSpec] for sub-second timing.
 */
data class GenTune(
    /** How many times to call `upgrade()` at game start (global diamond / emerald only). */
    val tierUps: Int,
    val delaySeconds: Int?,
    val amount: Int?,
    val spawnLimit: Int?,
    /** Change *what* it drops. */
    val material: String?,
) {
    val isEmpty: Boolean get() = tierUps == 0 && delaySeconds == null && amount == null && spawnLimit == null && material == null
}

enum class LocationSource {
    TEAM_IRON,
    TEAM_GOLD,
    TEAM_EMERALD,
    TEAM_SPAWN,
    TEAM_BED,
    ARENA_DIAMOND,
    ARENA_EMERALD,
    ;

    val perTeam: Boolean get() = name.startsWith("TEAM_")
}

data class DropSpec(
    val id: String,
    val material: String,
    val amount: Int,
    val delaySeconds: Double,
    /** Max items of this drop lying around its generator at once; 0 = unlimited. */
    val spawnLimit: Int,
    val displayName: String?,
    val pickupDelayTicks: Int,
)

/** A generator owned by this plugin: any number of drops, spawned wherever [source] points. */
data class ExtraGeneratorSpec(
    val id: String,
    val source: LocationSource,
    val drops: List<DropSpec>,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    /** Wildcard patterns over team names; empty = every team. Ignored for arena sources. */
    val teams: List<String>,
    val startDelaySeconds: Double,
    val stopWhenEliminated: Boolean,
)

data class GeneratorSettings(
    /** BedWars1058 may not have finished building its generators the instant a game starts; tier-ups wait this long. */
    val tierUpDelayTicks: Int,
    /** Re-assert tuned values every N seconds so BedWars1058's own tier events don't undo them. 0 = never. */
    val enforceIntervalSeconds: Int,
    val teamIron: GenTune,
    val teamGold: GenTune,
    val teamEmerald: GenTune,
    val globalDiamond: GenTune,
    val globalEmerald: GenTune,
    val extras: List<ExtraGeneratorSpec>,
)

// ------------------------------------------------------------------- all ---

/** Everything that can differ per arena (global values, overridden by group, overridden by arena). */
data class RushSettings(
    val game: GameSettings,
    val bedDefense: BedDefenseSettings,
    val bridge: BridgeSettings,
    val generators: GeneratorSettings,
)
