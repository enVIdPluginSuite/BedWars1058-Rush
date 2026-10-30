package dev.etran.bedwarsrush.game

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.NextEvent
import com.andrei1058.bedwars.api.language.Language
import dev.etran.bedwarsrush.compat.Effects
import dev.etran.bedwarsrush.compat.Placeholders
import dev.etran.bedwarsrush.config.EffectSpec
import dev.etran.bedwarsrush.config.GameSettings
import dev.etran.bedwarsrush.lang.Paths

/** The non-generator, non-bed parts of Rush: starting upgrades, permanent effects, schedule jump, tutorial text. */
object GameRules {
    fun apply(arena: IArena, g: GameSettings, warn: (String) -> Unit) {
        g.nextEvent?.let { name ->
            val event = NextEvent.values().firstOrNull { it.name == name }
            if (event == null) {
                warn("game.next-event '$name' is not a BedWars1058 event. Valid: ${NextEvent.values().joinToString { it.name }}")
            } else {
                arena.setNextEvent(event)
            }
        }

        for (team in arena.teams) {
            for ((key, tier) in g.teamUpgrades) team.teamUpgradeTiers[key] = tier
            for (e in g.teamEffects) effect(e, warn)?.let { team.addTeamEffect(it, e.amplifier, e.durationTicks) }
            for (e in g.baseEffects) effect(e, warn)?.let { team.addBaseEffect(it, e.amplifier, e.durationTicks) }
        }

        if (g.tutorial) {
            for (team in arena.teams) {
                for (player in ArrayList(team.members)) {
                    for (line in Language.getList(player, Paths.ARENA_START_TUTORIAL)) {
                        player.sendMessage(Placeholders.apply(player, line))
                    }
                }
            }
        }
    }

    private fun effect(spec: EffectSpec, warn: (String) -> Unit) =
        Effects.resolve(spec.type) ?: run {
            warn("Unknown potion effect '${spec.type}' on this server; skipped.")
            null
        }
}
