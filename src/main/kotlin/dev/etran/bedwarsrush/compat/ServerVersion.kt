package dev.etran.bedwarsrush.compat

import org.bukkit.Bukkit

/**
 * Parses the running Minecraft version.
 *
 * Mojang switched from `1.x.y` to `YY.drop` numbering in 2026 (26.1, 26.2, 26.3, ...), so "major" is 1 for every
 * old version and 26+ for the new scheme.
 */
object ServerVersion {
    data class Version(val major: Int, val minor: Int, val patch: Int) {
        /** Pre-"flattening" servers (1.8 - 1.12) still use numeric block ids + data values. */
        val isLegacyMaterials: Boolean get() = major == 1 && minor < 13

        /** 1.8.x: sounds use the oldest resource-pack names. */
        val isV1_8: Boolean get() = major == 1 && minor == 8

        override fun toString(): String = "$major.$minor.$patch"
    }

    val current: Version by lazy {
        // No running server (unit tests) -> behave like the oldest supported version.
        parse(try { Bukkit.getBukkitVersion() } catch (_: Throwable) { "1.8.8-R0.1-SNAPSHOT" })
    }

    fun parse(raw: String): Version {
        val m = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?""").find(raw.trim())
            ?: return Version(1, 21, 0) // unknown format: assume modern rather than legacy
        val major = m.groupValues[1].toInt()
        val minor = m.groupValues[2].toInt()
        val patch = m.groupValues[3].toIntOrNull() ?: 0
        return Version(major, minor, patch)
    }
}
