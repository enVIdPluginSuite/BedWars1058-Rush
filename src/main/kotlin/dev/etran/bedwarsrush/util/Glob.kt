package dev.etran.bedwarsrush.util

/**
 * Tiny case-insensitive wildcard matcher: `*` matches any run of characters, `?` matches exactly one.
 * Used for arena names, group names and block names so admins can write things like `rush_*` or `*_WOOL`.
 */
object Glob {
    fun matches(pattern: String, text: String): Boolean {
        val p = pattern.trim().lowercase()
        val t = text.trim().lowercase()
        var pi = 0
        var ti = 0
        var star = -1
        var mark = 0
        while (ti < t.length) {
            when {
                pi < p.length && (p[pi] == '?' || p[pi] == t[ti]) -> {
                    pi++
                    ti++
                }
                pi < p.length && p[pi] == '*' -> {
                    star = pi
                    mark = ti
                    pi++
                }
                star != -1 -> {
                    pi = star + 1
                    mark++
                    ti = mark
                }
                else -> return false
            }
        }
        while (pi < p.length && p[pi] == '*') pi++
        return pi == p.length
    }

    /** Returns the first pattern that matches [text], or null. */
    fun firstMatch(patterns: Collection<String>, text: String): String? =
        patterns.firstOrNull { matches(it, text) }

    fun hasWildcard(pattern: String): Boolean = pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0
}
