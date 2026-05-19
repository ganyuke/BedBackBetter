package io.github.ganyuke.bedFallback

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.collections.set

class PlayerSpawnWatcher {
    private lateinit var task: BukkitTask
    private val lastRespawnLocations = mutableMapOf<UUID, Location>()
    private val plugin: JavaPlugin

    constructor(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    fun onEnable() {
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                checkRespawnLocation(player)
            }
        }, 0L, 1L)
    }

    fun onDisable() {
        task.cancel()
    }

    private fun checkRespawnLocation(player: Player) {
        val currentRespawn = player.respawnLocation ?: player.world.spawnLocation
        val lastRespawn = lastRespawnLocations[player.uniqueId]
        if (lastRespawn == null || lastRespawn != currentRespawn) {
            if (lastRespawn != null) {
                plugin.logger.info("Respawn changed for ${player.name}: ${currentRespawn.blockX}, ${currentRespawn.blockY}, ${currentRespawn.blockZ}.")
            }
            lastRespawnLocations[player.uniqueId] = currentRespawn
        }
    }
}