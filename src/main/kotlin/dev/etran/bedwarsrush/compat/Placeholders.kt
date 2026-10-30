package dev.etran.bedwarsrush.compat

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.lang.reflect.Method

/** Colour codes plus optional PlaceholderAPI support (looked up reflectively so it stays a soft dependency). */
object Placeholders {
    private var papiMethod: Method? = null
    private var papiLookedUp = false

    fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    fun color(lines: List<String>): List<String> = lines.map(::color)

    fun apply(player: Player, text: String): String {
        val withPapi = if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) papi(player, text) else text
        return color(withPapi)
    }

    private fun papi(player: Player, text: String): String {
        if (!papiLookedUp) {
            papiLookedUp = true
            papiMethod = try {
                Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", Player::class.java, String::class.java)
            } catch (_: Throwable) {
                null
            }
        }
        val method = papiMethod ?: return text
        return try {
            method.invoke(null, player, text) as? String ?: text
        } catch (_: Throwable) {
            text
        }
    }
}
