package dev.etran.bedwarsrush.generator

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.generator.GeneratorType
import com.andrei1058.bedwars.api.arena.team.ITeam
import dev.etran.bedwarsrush.compat.Materials
import dev.etran.bedwarsrush.compat.Placeholders
import dev.etran.bedwarsrush.config.DropSpec
import dev.etran.bedwarsrush.config.ExtraGeneratorSpec
import dev.etran.bedwarsrush.config.LocationSource
import dev.etran.bedwarsrush.util.Glob
import org.bukkit.Location
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import kotlin.math.roundToInt

/**
 * Generators owned by this plugin, independent of BedWars1058's own ones.
 *
 * Each generator has any number of *drops* (what spawns), each with its own item, amount, delay (sub-second allowed)
 * and spawn limit. One shared repeating task drives every spawner of an arena; the task's resolution is [PERIOD_TICKS].
 */
class ExtraGenerators(
    private val plugin: Plugin,
    private val arena: IArena,
    private val specs: List<ExtraGeneratorSpec>,
    private val warn: (String) -> Unit,
) {
    private class Spawner(
        val location: Location,
        val drop: DropSpec,
        val team: ITeam?,
        val stopWhenEliminated: Boolean,
        var countdownTicks: Int,
    ) {
        val items = ArrayList<Item>()
        val delayTicks: Int = (drop.delaySeconds * 20).roundToInt().coerceAtLeast(PERIOD_TICKS)
    }

    private val spawners = ArrayList<Spawner>()
    private var task: BukkitTask? = null

    val spawnerCount: Int get() = spawners.size

    fun start() {
        stop()
        for (spec in specs) {
            for ((team, location) in resolveLocations(spec)) {
                val at = location.clone().add(spec.offsetX, spec.offsetY, spec.offsetZ)
                for (drop in spec.drops) {
                    val first = (spec.startDelaySeconds * 20).roundToInt().coerceAtLeast(0) +
                        (drop.delaySeconds * 20).roundToInt().coerceAtLeast(PERIOD_TICKS)
                    spawners.add(Spawner(at, drop, team, spec.stopWhenEliminated, first))
                }
            }
        }
        if (spawners.isNotEmpty()) {
            task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, PERIOD_TICKS.toLong(), PERIOD_TICKS.toLong())
        }
    }

    /** Stops spawning and removes every item this class dropped that is still lying around. */
    fun stop() {
        task?.cancel()
        task = null
        for (s in spawners) {
            for (item in s.items) if (item.isValid) item.remove()
        }
        spawners.clear()
    }

    private fun tick() {
        if (arena.status != GameState.playing) {
            stop()
            return
        }
        for (s in spawners) {
            if (s.stopWhenEliminated && s.team != null && s.team.isBedDestroyed && s.team.members.isEmpty()) continue
            s.countdownTicks -= PERIOD_TICKS
            if (s.countdownTicks > 0) continue
            s.countdownTicks = s.delayTicks

            s.items.removeAll { !it.isValid || it.isDead }
            if (s.drop.spawnLimit > 0 && s.items.sumOf { it.itemStack.amount } >= s.drop.spawnLimit) continue
            spawn(s)
        }
    }

    private fun spawn(s: Spawner) {
        val material = Materials.resolve(s.drop.material) ?: return
        val stack = ItemStack(material, s.drop.amount)
        s.drop.displayName?.let { name ->
            val meta = stack.itemMeta
            if (meta != null) {
                meta.displayName = Placeholders.color(name)
                stack.itemMeta = meta
            }
        }
        val world = s.location.world ?: return
        val item = world.dropItem(s.location, stack)
        item.velocity = Vector(0.0, 0.0, 0.0) // generators drop in place instead of scattering
        item.pickupDelay = s.drop.pickupDelayTicks
        s.items.add(item)
    }

    // ------------------------------------------------------- locations ---

    private fun resolveLocations(spec: ExtraGeneratorSpec): List<Pair<ITeam?, Location>> {
        if (!spec.source.perTeam) {
            val wanted = if (spec.source == LocationSource.ARENA_DIAMOND) GeneratorType.DIAMOND else GeneratorType.EMERALD
            return arena.oreGenerators.filter { it.type == wanted }.map { null as ITeam? to it.location }
        }
        val out = ArrayList<Pair<ITeam?, Location>>()
        for (team in arena.teams) {
            if (spec.teams.isNotEmpty() && spec.teams.none { Glob.matches(it, team.name) }) continue
            val locations = teamLocations(team, spec.source)
            if (locations.isEmpty()) {
                warn("generators.extra.${spec.id}: no ${spec.source} location for team ${team.name} in ${arena.arenaName}.")
            }
            locations.forEach { out.add(team to it) }
        }
        return out
    }

    private fun teamLocations(team: ITeam, source: LocationSource): List<Location> = when (source) {
        LocationSource.TEAM_IRON -> fromArenaConfig(team, "Iron") { team.ironGenerator?.location }
        LocationSource.TEAM_GOLD -> fromArenaConfig(team, "Gold") { team.goldGenerator?.location }
        LocationSource.TEAM_EMERALD -> fromArenaConfig(team, "Emerald") { team.emeraldGenerator?.location }
        LocationSource.TEAM_SPAWN -> listOfNotNull(team.spawn)
        LocationSource.TEAM_BED -> listOfNotNull(team.bed?.clone()?.add(0.5, 1.0, 0.5))
        else -> emptyList()
    }

    /** BedWars1058 keeps these in the arena's own config (`Team.<name>.Iron` etc.); fall back to the live generator. */
    private fun fromArenaConfig(team: ITeam, key: String, fallback: () -> Location?): List<Location> {
        val configured = try {
            arena.config.getArenaLocations("Team." + team.name + "." + key).orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        if (configured.isNotEmpty()) return configured
        return listOfNotNull(fallback())
    }

    companion object {
        const val PERIOD_TICKS = 2
    }
}
