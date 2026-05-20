// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class PlayerSpawnWatcher {
    private var task: BukkitTask? = null
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
        task?.cancel()
    }

    private fun checkRespawnLocation(player: Player) {
        val currentRespawn = player.respawnLocation ?: player.world.spawnLocation
        val lastRespawn = lastRespawnLocations[player.uniqueId]
        if (lastRespawn == null || lastRespawn != currentRespawn) {
            if (lastRespawn != null) {
                plugin.logger.info("[DEBUG] Respawn changed for player ${player.name}: ${currentRespawn.blockX}, ${currentRespawn.blockY}, ${currentRespawn.blockZ}.")
            }
            lastRespawnLocations[player.uniqueId] = currentRespawn
        }
    }
}