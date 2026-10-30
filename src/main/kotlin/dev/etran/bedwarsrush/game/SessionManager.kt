package dev.etran.bedwarsrush.game

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import dev.etran.bedwarsrush.BedWarsRushPlugin
import dev.etran.bedwarsrush.bed.BedDefense
import dev.etran.bedwarsrush.config.RushSettings
import dev.etran.bedwarsrush.generator.ExtraGenerators
import dev.etran.bedwarsrush.generator.GeneratorTuner
import org.bukkit.scheduler.BukkitTask
import java.util.IdentityHashMap

/**
 * Owns everything that is running for Rush arenas: one [Session] per live arena, holding its scheduled tasks and
 * extra generators so they can be cancelled together (game end, reload, plugin disable).
 */
class SessionManager(private val plugin: BedWarsRushPlugin) {
    private val sessions = IdentityHashMap<IArena, Session>()

    val activeCount: Int get() = sessions.size

    fun isRunning(arena: IArena): Boolean = sessions.containsKey(arena)

    private inner class Session(val arena: IArena) {
        private val tasks = ArrayList<BukkitTask>()
        var extras: ExtraGenerators? = null
        var enforceTask: BukkitTask? = null
        var alive = true

        /** Runs [block] now if [delayTicks] <= 0, otherwise later, but only while this session is still valid. */
        fun later(delayTicks: Int, block: () -> Unit) {
            if (delayTicks <= 0) {
                block()
                return
            }
            tasks.add(
                plugin.server.scheduler.runTaskLater(
                    plugin,
                    Runnable { if (alive && arena.status == GameState.playing) block() },
                    delayTicks.toLong(),
                ),
            )
        }

        fun cancelAll() {
            alive = false
            tasks.forEach { it.cancel() }
            tasks.clear()
            enforceTask?.cancel()
            enforceTask = null
            extras?.stop()
            extras = null
        }
    }

    // ---------------------------------------------------------- start ---

    fun start(arena: IArena) {
        stop(arena)
        val settings = plugin.settingsFor(arena)
        val session = Session(arena)
        sessions[arena] = session
        plugin.debug("Rush starting in '${arena.arenaName}' (group '${arena.group}')")

        session.later(settings.bedDefense.delayTicks) {
            if (settings.bedDefense.enabled) {
                val placed = BedDefense.buildAll(arena, settings.bedDefense, plugin::warn)
                plugin.debug("Bed defense placed $placed blocks in '${arena.arenaName}'")
            }
        }

        session.later(settings.game.startDelayTicks) {
            GameRules.apply(arena, settings.game, plugin::warn)
            activateGenerators(session, settings)
        }

        session.later(settings.generators.tierUpDelayTicks) {
            GeneratorTuner.tierUp(arena, settings.generators)
            GeneratorTuner.tune(arena, settings.generators)
        }
    }

    private fun activateGenerators(session: Session, settings: RushSettings) {
        val arena = session.arena
        GeneratorTuner.tune(arena, settings.generators)

        session.extras?.stop()
        session.extras = null
        if (settings.generators.extras.isNotEmpty()) {
            val extras = ExtraGenerators(plugin, arena, settings.generators.extras, plugin::warn)
            extras.start()
            session.extras = extras
            plugin.debug("Started ${extras.spawnerCount} extra spawners in '${arena.arenaName}'")
        }

        session.enforceTask?.cancel()
        session.enforceTask = null
        val interval = settings.generators.enforceIntervalSeconds
        if (interval > 0) {
            val period = interval * 20L
            session.enforceTask = plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable {
                    if (arena.status != GameState.playing) stop(arena) else GeneratorTuner.tune(arena, settings.generators)
                },
                period,
                period,
            )
        }
    }

    // ----------------------------------------------------------- stop ---

    fun stop(arena: IArena) {
        sessions.remove(arena)?.cancelAll()
    }

    fun stopAll() {
        val all = ArrayList(sessions.values)
        sessions.clear()
        all.forEach { it.cancelAll() }
    }

    // --------------------------------------------------------- reload ---

    /**
     * Re-applies the (new) configuration to games that are already running, without restarting them.
     * Only the live-adjustable parts are touched: generator tuning and extra generators. Blocks already placed and
     * effects already granted are left as they are.
     *
     * @return number of arenas that were refreshed
     */
    fun refresh(applyToRunning: Boolean): Int {
        if (!applyToRunning) return 0
        var refreshed = 0
        for (session in ArrayList(sessions.values)) {
            val arena = session.arena
            if (arena.status != GameState.playing || !plugin.isRush(arena)) {
                // Arena was taken out of Rush by the new config: stop driving it.
                stop(arena)
                continue
            }
            activateGenerators(session, plugin.settingsFor(arena))
            refreshed++
        }
        return refreshed
    }
}
