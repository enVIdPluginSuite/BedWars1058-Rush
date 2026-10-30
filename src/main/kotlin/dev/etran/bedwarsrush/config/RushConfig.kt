package dev.etran.bedwarsrush.config

import dev.etran.bedwarsrush.util.Glob
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Result of (re)loading config.yml: either a ready [RushConfig] or an error to show the admin. */
sealed class LoadResult {
    data class Success(val config: RushConfig) : LoadResult()
    data class Failure(val message: String) : LoadResult()
}

/**
 * An immutable snapshot of config.yml. Reloading builds a new snapshot and swaps it in, so a typo in the file can
 * never leave the plugin half-configured: the previous snapshot stays active and the admin gets the error.
 */
class RushConfig private constructor(
    private val root: YamlConfiguration,
    val warnings: List<String>,
) {
    val debug: Boolean = root.getBoolean("settings.debug", false)
    val applyToRunningOnReload: Boolean = root.getBoolean("settings.apply-to-running-arenas-on-reload", true)
    val injectLanguageMessages: Boolean = root.getBoolean("settings.inject-language-messages", true)

    val groups: List<String> = root.getStringList("arenas.groups")
    val whitelist: List<String> = root.getStringList("arenas.whitelist")
    val blacklist: List<String> = root.getStringList("arenas.blacklist")
    val whitelistOnly: Boolean = root.getBoolean("arenas.whitelist-only", false)

    private val parser = SettingsParser { /* runtime parses stay quiet; warnings were collected at load time */ }
    private val cache = ConcurrentHashMap<String, RushSettings>()

    /** Number of per-group / per-arena override blocks in the file. */
    val overrideCount: Int
        get() = (root.getConfigurationSection("overrides.groups")?.getKeys(false)?.size ?: 0) +
            (root.getConfigurationSection("overrides.arenas")?.getKeys(false)?.size ?: 0)

    /**
     * Effective settings for an arena: arena override > group override > global.
     * @param names every name the arena goes by (matched against `overrides.arenas` keys)
     */
    fun settingsFor(names: Collection<String>, group: String?): RushSettings {
        val key = names.joinToString("|") + "#" + (group ?: "")
        return cache.getOrPut(key) { parser.parse(ConfigView(layersFor(names, group))) }
    }

    private fun layersFor(names: Collection<String>, group: String?): List<ConfigurationSection> {
        val layers = ArrayList<ConfigurationSection>()
        matchingOverrides("overrides.arenas", names).forEach(layers::add)
        if (!group.isNullOrBlank()) matchingOverrides("overrides.groups", listOf(group)).forEach(layers::add)
        layers.add(root)
        return layers
    }

    /** Exact keys beat wildcard keys; among wildcards the longer (more specific) pattern wins. */
    private fun matchingOverrides(path: String, candidates: Collection<String>): List<ConfigurationSection> {
        val section = root.getConfigurationSection(path) ?: return emptyList()
        return section.getKeys(false)
            .filter { key -> candidates.any { Glob.matches(key, it) } }
            .sortedWith(compareBy<String> { Glob.hasWildcard(it) }.thenByDescending { it.length })
            .mapNotNull { section.getConfigurationSection(it) }
    }

    companion object {
        fun load(file: File): LoadResult {
            val yaml = YamlConfiguration()
            try {
                yaml.load(file)
            } catch (e: InvalidConfigurationException) {
                return LoadResult.Failure("config.yml is not valid YAML: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
            } catch (e: IOException) {
                return LoadResult.Failure("could not read config.yml: ${e.message}")
            }
            return LoadResult.Success(RushConfig(yaml, validate(yaml)))
        }

        /** Parses the global settings and every override once, purely to surface warnings at reload time. */
        private fun validate(root: YamlConfiguration): List<String> {
            val warnings = ArrayList<String>()
            SettingsParser { warnings.add(it) }.parse(ConfigView(listOf(root)))
            for (kind in listOf("groups", "arenas")) {
                val section = root.getConfigurationSection("overrides.$kind") ?: continue
                for (key in section.getKeys(false)) {
                    val child = section.getConfigurationSection(key)
                    if (child == null) {
                        warnings.add("overrides.$kind.$key must be a section; ignoring.")
                        continue
                    }
                    SettingsParser { warnings.add("overrides.$kind.$key -> $it") }.parse(ConfigView(listOf(child, root)))
                }
            }
            return warnings.distinct()
        }
    }
}
