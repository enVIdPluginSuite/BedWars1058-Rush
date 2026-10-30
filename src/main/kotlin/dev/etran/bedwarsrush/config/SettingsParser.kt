package dev.etran.bedwarsrush.config

import dev.etran.bedwarsrush.compat.Materials

/**
 * Turns a [ConfigView] into typed [RushSettings]. Never throws on bad user input: problems are reported through
 * [warn] and replaced by a safe default, so one typo can't take the whole mode down.
 */
class SettingsParser(private val warn: (String) -> Unit) {

    fun parse(view: ConfigView): RushSettings = RushSettings(
        game = game(view.sub("game")),
        bedDefense = bedDefense(view.sub("bed-defense")),
        bridge = bridge(view.sub("bridge")),
        generators = generators(view.sub("generators")),
    )

    // ------------------------------------------------------------ game ---

    private fun game(v: ConfigView): GameSettings {
        val upgrades = LinkedHashMap<String, Int>()
        val up = v.sub("team-upgrades")
        for (key in up.keys()) {
            val tier = up.int(key)
            if (tier == null) warn("game.team-upgrades.$key must be a whole number (tier index); ignoring.") else upgrades[key] = tier
        }
        return GameSettings(
            startDelayTicks = v.int("start-delay-ticks", 5).coerceAtLeast(0),
            nextEvent = v.string("next-event")?.trim()?.takeUnless { it.isEmpty() || it.equals("NONE", true) }?.uppercase(),
            tutorial = v.bool("tutorial", true),
            teamUpgrades = upgrades,
            teamEffects = effects(v.sub("team-effects"), "game.team-effects"),
            baseEffects = effects(v.sub("base-effects"), "game.base-effects"),
        )
    }

    private fun effects(v: ConfigView, where: String): List<EffectSpec> {
        val out = ArrayList<EffectSpec>()
        for (type in v.keys()) {
            val e = v.sub(type)
            if (!e.bool("enabled", true)) continue
            out.add(
                EffectSpec(
                    type = type,
                    amplifier = e.int("amplifier", 0).coerceIn(0, 255),
                    durationTicks = duration(e.string("duration"), "$where.$type.duration"),
                ),
            )
        }
        return out
    }

    /** `infinite` (default) or a number of seconds. */
    private fun duration(raw: String?, where: String): Int {
        val s = raw?.trim()?.lowercase()
        if (s == null || s.isEmpty() || s == "infinite" || s == "forever" || s == "permanent") return INFINITE_TICKS
        val seconds = s.toDoubleOrNull()
        if (seconds == null || seconds <= 0) {
            warn("$where must be 'infinite' or a positive number of seconds (got '$raw'); using infinite.")
            return INFINITE_TICKS
        }
        return (seconds * 20).toLong().coerceAtMost((INFINITE_TICKS - 1).toLong()).toInt()
    }

    // ----------------------------------------------------- bed defense ---

    private fun bedDefense(v: ConfigView): BedDefenseSettings {
        val layers = ArrayList<LayerSpec>()
        for ((index, entry) in (v.rawList("layers") ?: emptyList<Any?>()).withIndex()) {
            val spec = when (entry) {
                is String -> LayerSpec(entry.trim(), 1)
                is Map<*, *> -> {
                    val material = entry["material"]?.toString()?.trim()
                    val thickness = (entry["thickness"] as? Number)?.toInt() ?: 1
                    if (material.isNullOrEmpty()) null else LayerSpec(material, thickness)
                }
                else -> null
            }
            if (spec == null) {
                warn("bed-defense.layers[$index] is not a block name or a {material, thickness} map; skipping.")
                continue
            }
            if (Materials.block(spec.material, "WHITE") == null) {
                warn("bed-defense.layers[$index]: unknown block '${spec.material}' on this server; the layer will be skipped.")
            }
            layers.add(spec.copy(thickness = spec.thickness.coerceIn(1, 8)))
        }
        val replace = enumOf(v.string("replace", "AIR"), ReplaceMode.values(), ReplaceMode.AIR, "bed-defense.replace")
        return BedDefenseSettings(
            enabled = v.bool("enabled", true),
            delayTicks = v.int("delay-ticks", 0).coerceAtLeast(0),
            layers = layers,
            shape = enumOf(v.string("shape", "DIAMOND"), BedShape.values(), BedShape.DIAMOND, "bed-defense.shape"),
            replace = replace,
            includeBelow = v.bool("include-below", false),
            skipProtected = v.bool("skip-protected", false),
            maxBlocksPerTeam = v.int("max-blocks-per-team", 3000).coerceIn(1, 100_000),
        )
    }

    // ---------------------------------------------------------- bridge ---

    private fun bridge(v: ConfigView): BridgeSettings = BridgeSettings(
        enabled = v.bool("enabled", true),
        toggle = enumOf(v.string("toggle", "LEFT_CLICK"), BridgeToggle.values(), BridgeToggle.LEFT_CLICK, "bridge.toggle"),
        extraBlocks = v.int("extra-blocks", 5).coerceIn(0, 64),
        intervalTicks = v.int("interval-ticks", 3).coerceIn(1, 40),
        materials = (v.stringList("materials") ?: listOf("*WOOL")).map { it.trim() }.filter { it.isNotEmpty() },
        consumeItems = v.bool("consume-items", false),
        sound = v.string("sound", "AUTO"),
        volume = v.double("volume", 1.0).toFloat().coerceIn(0f, 10f),
        pitch = v.double("pitch", 1.0).toFloat().coerceIn(0.5f, 2f),
        actionBar = v.bool("messages.action-bar", true),
        chat = v.bool("messages.chat", false),
    )

    // ------------------------------------------------------ generators ---

    private fun generators(v: ConfigView): GeneratorSettings {
        val extras = ArrayList<ExtraGeneratorSpec>()
        val extraView = v.sub("extra")
        for (id in extraView.keys()) extra(extraView.sub(id), id)?.let(extras::add)
        return GeneratorSettings(
            tierUpDelayTicks = v.int("tier-up-delay-ticks", 100).coerceAtLeast(0),
            enforceIntervalSeconds = v.int("enforce-interval-seconds", 5).coerceAtLeast(0),
            teamIron = tune(v.sub("team.iron"), false, "generators.team.iron"),
            teamGold = tune(v.sub("team.gold"), false, "generators.team.gold"),
            teamEmerald = tune(v.sub("team.emerald"), false, "generators.team.emerald"),
            globalDiamond = tune(v.sub("global.diamond"), true, "generators.global.diamond"),
            globalEmerald = tune(v.sub("global.emerald"), true, "generators.global.emerald"),
            extras = extras,
        )
    }

    private fun tune(v: ConfigView, allowTierUps: Boolean, where: String): GenTune {
        val material = v.string("material")?.trim()?.takeIf { it.isNotEmpty() }
        if (material != null && Materials.resolve(material) == null) {
            warn("$where.material: unknown item '$material' on this server; ignoring.")
        }
        val tierUps = v.int("tier-ups", 0).coerceIn(0, 10)
        if (tierUps > 0 && !allowTierUps) warn("$where.tier-ups is only supported for generators.global.*; ignoring.")
        return GenTune(
            tierUps = if (allowTierUps) tierUps else 0,
            delaySeconds = positiveOrWarn(v.int("delay"), "$where.delay", 1),
            amount = positiveOrWarn(v.int("amount"), "$where.amount", 1),
            spawnLimit = positiveOrWarn(v.int("spawn-limit"), "$where.spawn-limit", 0),
            material = material?.takeIf { Materials.resolve(it) != null },
        )
    }

    private fun positiveOrWarn(value: Int?, where: String, min: Int): Int? {
        if (value == null) return null
        if (value < min) {
            warn("$where must be at least $min (got $value); ignoring.")
            return null
        }
        return value
    }

    private fun extra(v: ConfigView, id: String): ExtraGeneratorSpec? {
        val where = "generators.extra.$id"
        if (!v.bool("enabled", true)) return null

        val sourceName = v.string("location")?.trim()?.uppercase()
        val source = sourceName?.let { n -> LocationSource.values().firstOrNull { it.name == n } }
        if (source == null) {
            warn("$where.location must be one of ${LocationSource.values().joinToString()} (got '${v.string("location")}'); skipping this generator.")
            return null
        }

        val drops = ArrayList<DropSpec>()
        val dropsView = v.sub("drops")
        for (dropId in dropsView.keys()) {
            val d = dropsView.sub(dropId)
            if (!d.bool("enabled", true)) continue
            val material = d.string("material")?.trim()
            if (material.isNullOrEmpty() || Materials.resolve(material) == null) {
                warn("$where.drops.$dropId.material: unknown item '${material ?: ""}' on this server; skipping this drop.")
                continue
            }
            drops.add(
                DropSpec(
                    id = dropId,
                    material = material,
                    amount = d.int("amount", 1).coerceIn(1, 64),
                    delaySeconds = d.double("delay", 10.0).coerceAtLeast(0.1),
                    spawnLimit = d.int("spawn-limit", 0).coerceAtLeast(0),
                    displayName = d.string("name")?.takeIf { it.isNotBlank() },
                    pickupDelayTicks = d.int("pickup-delay-ticks", 0).coerceIn(0, 32767),
                ),
            )
        }
        if (drops.isEmpty()) {
            warn("$where has no usable drops; skipping this generator.")
            return null
        }

        return ExtraGeneratorSpec(
            id = id,
            source = source,
            drops = drops,
            offsetX = v.double("offset.x", 0.0),
            offsetY = v.double("offset.y", 0.0),
            offsetZ = v.double("offset.z", 0.0),
            teams = v.stringList("teams") ?: emptyList(),
            startDelaySeconds = v.double("start-delay-seconds", 0.0).coerceAtLeast(0.0),
            stopWhenEliminated = v.bool("stop-when-eliminated", true),
        )
    }

    // ----------------------------------------------------------- utils ---

    private fun <E : Enum<E>> enumOf(raw: String, values: Array<E>, default: E, where: String): E {
        val match = values.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        if (match == null) {
            warn("$where must be one of ${values.joinToString { it.name }} (got '$raw'); using ${default.name}.")
            return default
        }
        return match
    }
}
