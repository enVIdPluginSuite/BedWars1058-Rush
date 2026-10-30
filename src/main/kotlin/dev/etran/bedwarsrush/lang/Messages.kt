package dev.etran.bedwarsrush.lang

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/** Admin-facing texts (commands, reload feedback) from `messages.yml`; missing keys fall back to the built-in file. */
class Messages(private val plugin: JavaPlugin) {
    private var user = YamlConfiguration()
    private var defaults = YamlConfiguration()

    /** @return null on success, otherwise a description of what was wrong with the user's file. */
    fun reload(): String? {
        defaults = plugin.getResource("messages.yml")?.use { stream ->
            YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8))
        } ?: YamlConfiguration()

        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) plugin.saveResource("messages.yml", false)

        val loaded = YamlConfiguration()
        return try {
            loaded.load(file)
            user = loaded
            null
        } catch (e: InvalidConfigurationException) {
            user = YamlConfiguration()
            "messages.yml is not valid YAML (${e.message?.lineSequence()?.firstOrNull()}); using built-in texts."
        } catch (e: IOException) {
            user = YamlConfiguration()
            "could not read messages.yml (${e.message}); using built-in texts."
        }
    }

    private fun raw(key: String): String = user.getString(key) ?: defaults.getString(key) ?: key

    /** Looks up [key], substitutes `%name%` placeholders, and translates `&` colour codes. */
    fun get(key: String, vararg replacements: Pair<String, Any?>): String {
        var text = raw(key)
        for ((name, value) in replacements) text = text.replace("%$name%", value.toString())
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text)
    }

    /** [get] with the plugin prefix in front. */
    fun prefixed(key: String, vararg replacements: Pair<String, Any?>): String = get("prefix") + get(key, *replacements)
}
