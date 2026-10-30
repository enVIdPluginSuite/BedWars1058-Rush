package dev.etran.bedwarsrush.game

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import dev.etran.bedwarsrush.BedWarsRushPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class RushGameListener(private val plugin: BedWarsRushPlugin) : Listener {
    @EventHandler
    fun onStateChange(event: GameStateChangeEvent) {
        val arena = event.arena
        if (event.newState == GameState.playing) {
            if (plugin.isRush(arena)) plugin.sessions.start(arena)
        } else {
            plugin.sessions.stop(arena)
        }
    }
}
