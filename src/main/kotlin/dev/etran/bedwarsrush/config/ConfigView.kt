package dev.etran.bedwarsrush.config

import org.bukkit.configuration.ConfigurationSection

/**
 * A read-only view over several config layers, most specific first.
 *
 * This is what makes per-group and per-arena overrides cheap: the settings parser is written once against a view, and a
 * lookup of `generators.team.iron.delay` falls through arena override -> group override -> global value.
 * Overriding is per *leaf*, so an arena can change one number without repeating its whole section.
 */
class ConfigView private constructor(
    private val layers: List<ConfigurationSection>,
    private val prefix: String,
) {
    constructor(layers: List<ConfigurationSection>) : this(layers, "")

    private fun full(path: String) = when {
        path.isEmpty() -> prefix
        prefix.isEmpty() -> path
        else -> "$prefix.$path"
    }

    private fun layerFor(path: String): ConfigurationSection? {
        val p = full(path)
        return layers.firstOrNull { it.isSet(p) }
    }

    fun sub(path: String): ConfigView = ConfigView(layers, full(path))

    fun has(path: String): Boolean = layerFor(path) != null

    fun string(path: String): String? = layerFor(path)?.getString(full(path))

    fun string(path: String, default: String): String = string(path) ?: default

    fun int(path: String): Int? {
        val layer = layerFor(path) ?: return null
        val v = layer.get(full(path))
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    fun int(path: String, default: Int): Int = int(path) ?: default

    fun double(path: String): Double? {
        val layer = layerFor(path) ?: return null
        val v = layer.get(full(path))
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
    }

    fun double(path: String, default: Double): Double = double(path) ?: default

    fun bool(path: String): Boolean? {
        val layer = layerFor(path) ?: return null
        val v = layer.get(full(path))
        return when (v) {
            is Boolean -> v
            is String -> when (v.trim().lowercase()) {
                "true", "yes", "on" -> true
                "false", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    fun bool(path: String, default: Boolean): Boolean = bool(path) ?: default

    /** The list at [path] from the most specific layer that defines it, or null. Lists replace, they do not merge. */
    fun stringList(path: String): List<String>? = layerFor(path)?.getStringList(full(path))

    /** The raw list at [path] (entries may be strings or maps), from the most specific layer that defines it. */
    fun rawList(path: String): List<*>? = layerFor(path)?.getList(full(path))

    /** Child keys of [path] (default: this view's own prefix), unioned across all layers, most specific first. */
    fun keys(path: String = ""): Set<String> {
        val p = full(path)
        val out = LinkedHashSet<String>()
        for (layer in layers) {
            layer.getConfigurationSection(p)?.getKeys(false)?.let { out.addAll(it) }
        }
        return out
    }
}
