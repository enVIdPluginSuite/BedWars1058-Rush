package dev.etran.bedwarsrush.arena

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

enum class ListKind(val key: String, val label: String) {
    GROUP("groups", "group"),
    WHITELIST("whitelist", "whitelist"),
    BLACKLIST("blacklist", "blacklist"),
}

/**
 * Arena groups / whitelist / blacklist entries managed with `/bwrush` commands.
 *
 * They live in their own file (`arena-lists.yml`) and are merged with the lists in config.yml. That way a command can
 * add or remove an entry without rewriting config.yml, which would strip its comments on servers older than 1.18.
 */
class DynamicLists(private val file: File) {
    private val lists: Map<ListKind, MutableList<String>> = ListKind.values().associate { it to ArrayList<String>() }

    fun get(kind: ListKind): List<String> = lists.getValue(kind)

    /** @return null on success, otherwise an error message (the previous contents stay in effect). */
    fun load(): String? {
        if (!file.exists()) {
            lists.values.forEach { it.clear() }
            return null
        }
        val yaml = YamlConfiguration()
        try {
            yaml.load(file)
        } catch (e: InvalidConfigurationException) {
            return "arena-lists.yml is not valid YAML: ${e.message?.lineSequence()?.firstOrNull()}"
        } catch (e: IOException) {
            return "could not read arena-lists.yml: ${e.message}"
        }
        for (kind in ListKind.values()) {
            val list = lists.getValue(kind)
            list.clear()
            list.addAll(yaml.getStringList(kind.key).map { it.trim() }.filter { it.isNotEmpty() })
        }
        return null
    }

    fun contains(kind: ListKind, value: String): Boolean = get(kind).any { it.equals(value.trim(), ignoreCase = true) }

    /** @return false if it was already present. */
    fun add(kind: ListKind, value: String): Boolean {
        if (contains(kind, value)) return false
        lists.getValue(kind).add(value.trim())
        return true
    }

    /** @return false if it was not present. */
    fun remove(kind: ListKind, value: String): Boolean =
        lists.getValue(kind).removeAll { it.equals(value.trim(), ignoreCase = true) }

    /** @return null on success, otherwise an error message. */
    fun save(): String? {
        val yaml = YamlConfiguration()
        for (kind in ListKind.values()) yaml.set(kind.key, get(kind).toList())
        return try {
            file.parentFile?.mkdirs()
            yaml.save(file)
            null
        } catch (e: IOException) {
            "could not save arena-lists.yml: ${e.message}"
        }
    }
}
