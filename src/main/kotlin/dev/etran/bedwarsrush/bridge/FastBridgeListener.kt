package dev.etran.bedwarsrush.bridge

import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
import com.andrei1058.bedwars.api.language.Language
import dev.etran.bedwarsrush.BedWarsRushPlugin
import dev.etran.bedwarsrush.compat.BlockSpec
import dev.etran.bedwarsrush.compat.Blocks
import dev.etran.bedwarsrush.compat.Materials
import dev.etran.bedwarsrush.compat.Placeholders
import dev.etran.bedwarsrush.compat.Sounds
import dev.etran.bedwarsrush.config.BridgeSettings
import dev.etran.bedwarsrush.config.BridgeToggle
import dev.etran.bedwarsrush.lang.Paths
import dev.etran.bedwarsrush.util.Glob
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.HashSet
import java.util.UUID
import kotlin.math.abs

/**
 * Bridge mode: with a bridge block in hand, left click toggles bridging; each block the player then places is
 * automatically extended a few blocks in the same direction.
 */
class FastBridgeListener(private val plugin: BedWarsRushPlugin) : Listener {
    private val bridging = HashSet<UUID>()

    fun clear() = bridging.clear()

    @EventHandler
    fun onLeave(event: PlayerLeaveArenaEvent) {
        bridging.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        bridging.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) return

        val player = event.player
        val arena = rushArenaOf(player) ?: return
        val settings = plugin.settingsFor(arena).bridge
        if (!settings.enabled || settings.toggle != BridgeToggle.LEFT_CLICK) return
        val material = event.material ?: return
        if (!isBridgeBlock(settings, material)) return

        val nowOn = bridging.add(player.uniqueId)
        if (!nowOn) bridging.remove(player.uniqueId)
        notify(player, settings, if (nowOn) Paths.BRIDGE_MODE_ACTIVATED else Paths.BRIDGE_MODE_DEACTIVATED)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val player = event.player
        val arena = rushArenaOf(player) ?: return
        val settings = plugin.settingsFor(arena).bridge
        if (!settings.enabled) return
        if (settings.toggle == BridgeToggle.LEFT_CLICK && player.uniqueId !in bridging) return

        val placed = event.blockPlaced
        if (settings.extraBlocks <= 0 || !isBridgeBlock(settings, placed.type)) return

        // Direction = from the block that was clicked to the block that appeared; keep going that way.
        val against = event.blockAgainst
        val dx = (placed.x - against.x).coerceIn(-1, 1)
        val dy = (placed.y - against.y).coerceIn(-1, 1)
        val dz = (placed.z - against.z).coerceIn(-1, 1)
        if (dx == 0 && dy == 0 && dz == 0) return

        val template = event.itemInHand?.clone()?.also { it.amount = 1 }
        ExtendTask(arena, player, settings, Blocks.specOf(placed), placed, dx, dy, dz, template)
            .runTaskTimer(plugin, 0L, settings.intervalTicks.toLong())
    }

    // ----------------------------------------------------------- task ---

    private inner class ExtendTask(
        private val arena: IArena,
        private val player: Player,
        private val settings: BridgeSettings,
        private val spec: BlockSpec,
        origin: Block,
        private val dx: Int,
        private val dy: Int,
        private val dz: Int,
        private val template: ItemStack?,
    ) : BukkitRunnable() {
        private var x = origin.x + dx
        private var y = origin.y + dy
        private var z = origin.z + dz
        private val world = origin.world
        private var done = 0

        override fun run() {
            if (done >= settings.extraBlocks || !player.isOnline || arena.status != GameState.playing || y !in 0 until world.maxHeight) {
                cancel()
                return
            }
            val target = world.getBlockAt(x, y, z)
            if (!Materials.isAir(target.type) || playerInTheWay(arena, x, y, z) || arena.isProtected(target.location)) {
                cancel()
                return
            }
            if (settings.consumeItems && !consumeOne()) {
                cancel()
                return
            }

            Blocks.place(target, spec)
            arena.addPlacedBlock(target)
            playSound(target)
            x += dx
            y += dy
            z += dz
            done++
        }

        private fun consumeOne(): Boolean {
            val stack = template ?: return false
            return player.inventory.removeItem(stack.clone()).isEmpty()
        }

        private fun playSound(target: Block) {
            val name = Sounds.placeSound(settings.sound, spec.material) ?: return
            val at = target.location
            for (p in arena.players + arena.spectators) {
                if (p.world == at.world && p.location.distanceSquared(at) <= SOUND_RANGE_SQ) {
                    Sounds.play(p, at, name, settings.volume, settings.pitch)
                }
            }
        }
    }

    // ---------------------------------------------------------- utils ---

    /** The arena the player is *playing* in (not spectating), if it runs Rush and is in progress. */
    private fun rushArenaOf(player: Player): IArena? {
        val arena = plugin.api.arenaUtil.getArenaByPlayer(player) ?: return null
        if (arena.status != GameState.playing) return null
        if (!arena.players.contains(player)) return null
        return if (plugin.isRush(arena)) arena else null
    }

    private fun isBridgeBlock(settings: BridgeSettings, material: Material): Boolean =
        settings.materials.any { Glob.matches(it, material.name) }

    /** True if any living player's hitbox (0.6 x 1.8) would overlap the block at x/y/z. */
    private fun playerInTheWay(arena: IArena, x: Int, y: Int, z: Int): Boolean {
        for (p in arena.players) {
            val l = p.location
            if (l.world != arena.world) continue
            if (l.x > x - 0.3 && l.x < x + 1.3 && l.z > z - 0.3 && l.z < z + 1.3 && l.y < y + 1.0 && l.y + 1.8 > y) return true
        }
        return false
    }

    private fun notify(player: Player, settings: BridgeSettings, path: String) {
        val text = Placeholders.color(Language.getMsg(player, path))
        if (settings.actionBar) {
            try {
                plugin.api.versionSupport.playAction(player, text)
            } catch (_: Throwable) {
                // Action bar support is a nicety; chat fallback below still works if enabled.
            }
        }
        if (settings.chat) player.sendMessage(text)
    }

    private companion object {
        const val SOUND_RANGE_SQ = 24.0 * 24.0
    }
}
