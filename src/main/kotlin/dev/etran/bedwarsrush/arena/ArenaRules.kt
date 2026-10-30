package dev.etran.bedwarsrush.arena

import dev.etran.bedwarsrush.util.Glob

enum class Reason {
    /** Arena matched the blacklist; blacklist always wins. */
    BLACKLISTED,

    /** Arena matched the whitelist. */
    WHITELISTED,

    /** Arena's group is one of the configured Rush groups. */
    GROUP,

    /** `whitelist-only` is on and the arena is not on the whitelist. */
    NOT_WHITELISTED,

    /** Nothing matched. */
    NO_MATCH,
}

data class Decision(val rush: Boolean, val reason: Reason, val matched: String? = null)

/**
 * Decides whether an arena plays the Rush mode.
 *
 * Order of evaluation (first hit wins):
 *  1. blacklist  -> never Rush
 *  2. whitelist  -> always Rush
 *  3. whitelist-only on -> not Rush
 *  4. group list -> Rush
 *  5. otherwise  -> not Rush
 *
 * All entries are case-insensitive wildcards (`*`, `?`).
 */
class ArenaRules(
    groups: Collection<String>,
    whitelist: Collection<String>,
    blacklist: Collection<String>,
    private val whitelistOnly: Boolean,
) {
    val groups: List<String> = clean(groups)
    val whitelist: List<String> = clean(whitelist)
    val blacklist: List<String> = clean(blacklist)

    /**
     * @param names every name the arena is known by (template name, world name, ...). Any of them may match a list entry.
     * @param group the BedWars1058 arena group, may be null/blank for ungrouped arenas.
     */
    fun evaluate(names: Collection<String>, group: String?): Decision {
        val candidates = names.filter { it.isNotBlank() }

        for (name in candidates) {
            Glob.firstMatch(blacklist, name)?.let { return Decision(false, Reason.BLACKLISTED, it) }
        }
        for (name in candidates) {
            Glob.firstMatch(whitelist, name)?.let { return Decision(true, Reason.WHITELISTED, it) }
        }
        if (whitelistOnly) return Decision(false, Reason.NOT_WHITELISTED)

        if (!group.isNullOrBlank()) {
            Glob.firstMatch(groups, group)?.let { return Decision(true, Reason.GROUP, it) }
        }
        return Decision(false, Reason.NO_MATCH)
    }

    companion object {
        private fun clean(input: Collection<String>): List<String> =
            input.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    }
}
