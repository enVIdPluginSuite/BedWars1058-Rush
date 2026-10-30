package dev.etran.bedwarsrush

import com.andrei1058.bedwars.api.BedWars
import com.andrei1058.bedwars.api.arena.IArena
import dev.etran.bedwarsrush.arena.ArenaRules
import dev.etran.bedwarsrush.arena.Decision
import dev.etran.bedwarsrush.arena.DynamicLists
import dev.etran.bedwarsrush.arena.ListKind
import dev.etran.bedwarsrush.bridge.FastBridgeListener
import dev.etran.bedwarsrush.command.RushCommand
import dev.etran.bedwarsrush.config.LoadResult
import dev.etran.bedwarsrush.config.RushConfig
import dev.etran.bedwarsrush.config.RushSettings
import dev.etran.bedwarsrush.game.RushGameListener
import dev.etran.bedwarsrush.game.SessionManager
import dev.etran.bedwarsrush.lang.LanguageDefaults
import dev.etran.bedwarsrush.lang.Messages
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** What a `/bwrush reload` did, for the command (and the console) to report. */
data class ReloadReport(
    val success: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val rushArenas: Int,
    val totalArenas: Int,
    val refreshedArenas: Int,
)

class BedWarsRushPlugin : JavaPlugin() {
    lateinit var api: BedWars
        private set
    lateinit var messages: Messages
        private set
    lateinit var lists: DynamicLists
        private set
    lateinit var sessions: SessionManager
        private set
    lateinit var rushConfig: RushConfig
        private set
    lateinit var rules: ArenaRules
        private set

    private lateinit var bridge: FastBridgeListener

    override fun onEnable() {
        val registration = server.servicesManager.getRegistration(BedWars::class.java)
        if (registration == null) {
            logger.severe("BedWars1058 is installed but its API is not available. Disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        api = registration.provider

        messages = Messages(this)
        lists = DynamicLists(File(dataFolder, "arena-lists.yml"))
        sessions = SessionManager(this)
        bridge = FastBridgeListener(this)

        val report = reload()
        if (!report.success) {
            report.errors.forEach { logger.severe(it) }
            logger.severe("config.yml could not be loaded. Fix it and restart; disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        report.warnings.forEach { logger.warning(it) }

        server.pluginManager.registerEvents(RushGameListener(this), this)
        server.pluginManager.registerEvents(bridge, this)

        val command = getCommand("bwrush")
        if (command != null) {
            val handler = RushCommand(this)
            command.setExecutor(handler)
            command.tabCompleter = handler
        }

        logger.info(
            "BedWars1058-Rush ${description.version} by dev.etran enabled on Minecraft ${server.bukkitVersion.substringBefore('-')} - " +
                "${report.rushArenas}/${report.totalArenas} arenas currently run Rush.",
        )
    }

    override fun onDisable() {
        if (this::sessions.isInitialized) sessions.stopAll()
        if (this::bridge.isInitialized) bridge.clear()
    }

    // ----------------------------------------------------------- reload ---

    /**
     * (Re)loads config.yml, messages.yml and arena-lists.yml, and applies the result to running games.
     * Safe to call at any time: if config.yml is broken the old configuration stays active.
     */
    fun reload(): ReloadReport {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()

        if (!File(dataFolder, "config.yml").exists()) saveResource("config.yml", false)

        val loaded = RushConfig.load(File(dataFolder, "config.yml"))
        if (loaded is LoadResult.Failure) {
            errors.add(loaded.message)
            return ReloadReport(false, errors, warnings, 0, 0, 0)
        }
        val config = (loaded as LoadResult.Success).config
        warnings.addAll(config.warnings)

        messages.reload()?.let(warnings::add)
        lists.load()?.let(warnings::add)

        rushConfig = config
        rebuildRules()

        if (config.injectLanguageMessages) {
            try {
                LanguageDefaults.inject()
            } catch (t: Throwable) {
                warnings.add("Could not register texts in BedWars1058's language files: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        val refreshed = sessions.refresh(config.applyToRunningOnReload)
        val arenas = api.arenaUtil.arenas
        return ReloadReport(true, errors, warnings, arenas.count { isRush(it) }, arenas.size, refreshed)
    }

    /** Rebuilds the arena rules from config.yml + `/bwrush`-managed lists. Cheap; call after editing a list. */
    fun rebuildRules() {
        val c = rushConfig
        rules = ArenaRules(
            groups = c.groups + lists.get(ListKind.GROUP),
            whitelist = c.whitelist + lists.get(ListKind.WHITELIST),
            blacklist = c.blacklist + lists.get(ListKind.BLACKLIST),
            whitelistOnly = c.whitelistOnly,
        )
    }

    // ------------------------------------------------------------ arenas ---

    /** Names an arena can be matched by: its template name and the world it runs in. */
    fun namesOf(arena: IArena): List<String> = listOfNotNull(arena.arenaName, arena.worldName).distinct()

    fun decide(arena: IArena): Decision = rules.evaluate(namesOf(arena), arena.group)

    fun isRush(arena: IArena): Boolean = decide(arena).rush

    fun settingsFor(arena: IArena): RushSettings = rushConfig.settingsFor(namesOf(arena), arena.group)

    // ----------------------------------------------------------- logging ---

    fun warn(message: String) = logger.warning(message)

    fun debug(message: String) {
        if (this::rushConfig.isInitialized && rushConfig.debug) logger.info("[debug] $message")
    }
}
