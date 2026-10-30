package dev.etran.bedwarsrush.command

import com.andrei1058.bedwars.api.arena.IArena
import dev.etran.bedwarsrush.BedWarsRushPlugin
import dev.etran.bedwarsrush.ReloadReport
import dev.etran.bedwarsrush.arena.Decision
import dev.etran.bedwarsrush.arena.ListKind
import dev.etran.bedwarsrush.arena.Reason
import dev.etran.bedwarsrush.util.Glob
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class RushCommand(private val plugin: BedWarsRushPlugin) : TabExecutor {
    private val m get() = plugin.messages

    private val subcommands = listOf("help", "reload", "status", "list", "check", "group", "whitelist", "blacklist")

    // ---------------------------------------------------------- execute ---

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            null, "help", "?" -> help(sender, label)
            "reload", "rl" -> if (allowed(sender, "bwrush.reload")) reload(sender)
            "status", "info" -> if (allowed(sender, "bwrush.info")) status(sender)
            "list", "arenas" -> if (allowed(sender, "bwrush.info")) listArenas(sender)
            "check" -> if (allowed(sender, "bwrush.info")) check(sender, label, args)
            "group", "groups" -> if (allowed(sender, "bwrush.manage")) editList(sender, label, ListKind.GROUP, args)
            "whitelist", "wl" -> if (allowed(sender, "bwrush.manage")) editList(sender, label, ListKind.WHITELIST, args)
            "blacklist", "bl" -> if (allowed(sender, "bwrush.manage")) editList(sender, label, ListKind.BLACKLIST, args)
            else -> sender.sendMessage(m.prefixed("unknown-subcommand", "label" to label))
        }
        return true
    }

    private fun allowed(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        sender.sendMessage(m.prefixed("no-permission"))
        return false
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(m.get("help-header", "version" to plugin.description.version))
        for (key in listOf("reload", "status", "list", "check", "group", "whitelist", "blacklist")) {
            sender.sendMessage(m.get("help-$key", "label" to label))
        }
    }

    // ----------------------------------------------------------- reload ---

    private fun reload(sender: CommandSender) {
        val started = System.nanoTime()
        val report: ReloadReport = plugin.reload()
        val ms = (System.nanoTime() - started) / 1_000_000

        if (!report.success) {
            sender.sendMessage(m.prefixed("reload-failed"))
            report.errors.forEach { sender.sendMessage(m.get("reload-error-line", "message" to it)) }
            sender.sendMessage(m.get("reload-kept-old"))
            return
        }
        sender.sendMessage(
            m.prefixed(
                "reload-success",
                "ms" to ms,
                "rush" to report.rushArenas,
                "total" to report.totalArenas,
                "refreshed" to report.refreshedArenas,
            ),
        )
        report.warnings.forEach { sender.sendMessage(m.get("reload-warning-line", "message" to it)) }
        if (report.warnings.isNotEmpty()) plugin.logger.warning("Reload finished with ${report.warnings.size} warning(s).")
    }

    // ----------------------------------------------------------- status ---

    private fun status(sender: CommandSender) {
        val cfg = plugin.rushConfig
        val arenas = plugin.api.arenaUtil.arenas
        sender.sendMessage(m.get("status-header"))
        sender.sendMessage(
            m.get("status-arenas", "rush" to arenas.count { plugin.isRush(it) }, "total" to arenas.size, "running" to plugin.sessions.activeCount),
        )
        for (kind in ListKind.values()) {
            val fromFile = when (kind) {
                ListKind.GROUP -> cfg.groups
                ListKind.WHITELIST -> cfg.whitelist
                ListKind.BLACKLIST -> cfg.blacklist
            }
            val fromCommands = plugin.lists.get(kind)
            sender.sendMessage(
                m.get(
                    "status-list",
                    "kind" to kind.label,
                    "values" to joinOrNone(fromFile + fromCommands.filter { c -> fromFile.none { it.equals(c, true) } }),
                ),
            )
        }
        sender.sendMessage(m.get("status-whitelist-only", "value" to cfg.whitelistOnly))
        sender.sendMessage(m.get("status-overrides", "count" to cfg.overrideCount))
    }

    private fun listArenas(sender: CommandSender) {
        val arenas = plugin.api.arenaUtil.arenas
        if (arenas.isEmpty()) {
            sender.sendMessage(m.prefixed("no-arenas"))
            return
        }
        sender.sendMessage(m.get("list-header", "count" to arenas.size))
        for (arena in arenas.sortedBy { it.arenaName.lowercase() }) {
            val d = plugin.decide(arena)
            sender.sendMessage(
                m.get(
                    if (d.rush) "list-line-rush" else "list-line-normal",
                    "arena" to arena.arenaName,
                    "group" to (arena.group ?: "-"),
                    "state" to arena.status.name,
                    "reason" to describe(d),
                ),
            )
        }
    }

    // ------------------------------------------------------------ check ---

    private fun check(sender: CommandSender, label: String, args: Array<out String>) {
        val name = args.getOrNull(1)
        if (name == null) {
            sender.sendMessage(m.prefixed("usage-check", "label" to label))
            return
        }
        val arena = plugin.api.arenaUtil.getArenaByName(name)
        if (arena == null) {
            sender.sendMessage(m.prefixed("arena-not-found", "arena" to name))
            return
        }
        val d = plugin.decide(arena)
        val s = plugin.settingsFor(arena)
        sender.sendMessage(m.get("check-header", "arena" to arena.arenaName))
        sender.sendMessage(m.get("check-result", "result" to m.get(if (d.rush) "word-rush" else "word-normal"), "reason" to describe(d)))
        sender.sendMessage(m.get("check-group", "group" to (arena.group ?: "-")))
        if (!d.rush) return
        sender.sendMessage(
            m.get(
                "check-bed",
                "state" to onOff(s.bedDefense.enabled),
                "layers" to joinOrNone(s.bedDefense.layers.map { if (it.thickness > 1) "${it.material}x${it.thickness}" else it.material }),
            ),
        )
        sender.sendMessage(m.get("check-bridge", "state" to onOff(s.bridge.enabled), "mode" to s.bridge.toggle.name, "blocks" to s.bridge.extraBlocks))
        val g = s.generators
        val tuned = listOf("iron" to g.teamIron, "gold" to g.teamGold, "emerald" to g.teamEmerald, "diamond" to g.globalDiamond, "map-emerald" to g.globalEmerald)
            .filter { !it.second.isEmpty }.map { it.first }
        sender.sendMessage(m.get("check-generators", "tuned" to joinOrNone(tuned), "extra" to joinOrNone(g.extras.map { it.id })))
    }

    // ------------------------------------------------------- list edits ---

    private fun editList(sender: CommandSender, label: String, kind: ListKind, args: Array<out String>) {
        val action = args.getOrNull(1)?.lowercase() ?: "list"
        val value = args.drop(2).joinToString(" ").trim()
        val cfg = plugin.rushConfig
        val fromFile = when (kind) {
            ListKind.GROUP -> cfg.groups
            ListKind.WHITELIST -> cfg.whitelist
            ListKind.BLACKLIST -> cfg.blacklist
        }

        when (action) {
            "list" -> {
                sender.sendMessage(m.get("list-edit-file", "kind" to kind.label, "values" to joinOrNone(fromFile)))
                sender.sendMessage(m.get("list-edit-commands", "kind" to kind.label, "values" to joinOrNone(plugin.lists.get(kind))))
            }
            "add" -> {
                if (value.isEmpty()) return sender.sendMessage(m.prefixed("usage-edit", "label" to label, "kind" to kind.label))
                if (!plugin.lists.add(kind, value)) return sender.sendMessage(m.prefixed("already-listed", "value" to value, "kind" to kind.label))
                persist(sender)
                sender.sendMessage(m.prefixed("added", "value" to value, "kind" to kind.label))
                if (!matchesAnything(kind, value)) sender.sendMessage(m.get("matches-nothing", "value" to value))
            }
            "remove", "rm", "delete" -> {
                if (value.isEmpty()) return sender.sendMessage(m.prefixed("usage-edit", "label" to label, "kind" to kind.label))
                if (plugin.lists.remove(kind, value)) {
                    persist(sender)
                    sender.sendMessage(m.prefixed("removed", "value" to value, "kind" to kind.label))
                } else if (fromFile.any { it.equals(value, true) }) {
                    sender.sendMessage(m.prefixed("defined-in-config", "value" to value, "kind" to kind.label))
                } else {
                    sender.sendMessage(m.prefixed("not-listed", "value" to value, "kind" to kind.label))
                }
            }
            else -> sender.sendMessage(m.prefixed("usage-edit", "label" to label, "kind" to kind.label))
        }
    }

    private fun persist(sender: CommandSender) {
        plugin.lists.save()?.let { sender.sendMessage(m.prefixed("save-failed", "message" to it)) }
        plugin.rebuildRules()
        plugin.sessions.refresh(plugin.rushConfig.applyToRunningOnReload)
    }

    private fun matchesAnything(kind: ListKind, pattern: String): Boolean {
        val arenas = plugin.api.arenaUtil.arenas
        return when (kind) {
            ListKind.GROUP -> arenas.any { Glob.matches(pattern, it.group ?: "") }
            else -> arenas.any { a -> plugin.namesOf(a).any { Glob.matches(pattern, it) } }
        }
    }

    // ------------------------------------------------------------ utils ---

    private fun describe(d: Decision): String = when (d.reason) {
        Reason.BLACKLISTED -> m.get("reason-blacklisted", "match" to d.matched)
        Reason.WHITELISTED -> m.get("reason-whitelisted", "match" to d.matched)
        Reason.GROUP -> m.get("reason-group", "match" to d.matched)
        Reason.NOT_WHITELISTED -> m.get("reason-not-whitelisted")
        Reason.NO_MATCH -> m.get("reason-no-match")
    }

    private fun onOff(value: Boolean) = m.get(if (value) "word-on" else "word-off")

    private fun joinOrNone(values: Collection<String>): String =
        if (values.isEmpty()) m.get("word-none") else values.joinToString(", ")

    // ------------------------------------------------------ tab complete ---

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val current = args.lastOrNull().orEmpty()
        val options: List<String> = when {
            args.size <= 1 -> subcommands
            else -> when (args[0].lowercase()) {
                "check" -> if (args.size == 2) arenaNames() else emptyList()
                "group", "groups" -> editOptions(ListKind.GROUP, args)
                "whitelist", "wl" -> editOptions(ListKind.WHITELIST, args)
                "blacklist", "bl" -> editOptions(ListKind.BLACKLIST, args)
                else -> emptyList()
            }
        }
        return options.filter { it.startsWith(current, ignoreCase = true) }.toMutableList()
    }

    private fun editOptions(kind: ListKind, args: Array<out String>): List<String> = when {
        args.size == 2 -> listOf("add", "remove", "list")
        args.size == 3 && args[1].equals("add", true) -> if (kind == ListKind.GROUP) groupNames() else arenaNames()
        args.size == 3 && args[1].lowercase() in listOf("remove", "rm", "delete") -> plugin.lists.get(kind)
        else -> emptyList()
    }

    private fun arenaNames(): List<String> = plugin.api.arenaUtil.arenas.map(IArena::getArenaName)

    private fun groupNames(): List<String> = plugin.api.arenaUtil.arenas.mapNotNull { it.group }.distinct()
}
