// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import io.github.ganyuke.bedbackbetter.config.PluginConfig.PluginConfiguration
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger

class RespawnHijackListener : Listener {
    private val playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>

    // no need to persist this since we are only listening to respawns. transition from dead to alive again is
    // fast enough that this probably doesn't matter to persist. respawn doesn't happen until the player
    // actually clicks "Respawn" on the "You Died!" screen.
    private val pendingRespawnLocation: MutableMap<UUID, RespawnRecord> = HashMap()

    private val logger: Logger
    private val pluginConfig: PluginConfiguration

    constructor(plugin: JavaPlugin, config: PluginConfiguration, respawnRecordMap: MutableMap<UUID, ArrayDeque<RespawnRecord>>) {
        this.logger = plugin.logger
        this.pluginConfig = config
        this.playerRespawnRecorder = respawnRecordMap
    }

    private fun Logger.debug(msg: String) {
        if (pluginConfig.debugMode) this.info("[DEBUG] $msg")
    }

    private fun Location.toStringCoord(): String = "(${x.toInt()},${y.toInt()},${z.toInt()})"

    fun onDisable() {
        playerRespawnRecorder.clear()
        pendingRespawnLocation.clear()
    }

    private fun resolveValidRecord(history: ArrayDeque<RespawnRecord>, skipLast: Int = 1): Pair<RespawnRecord, Location>? {
        val validEntry = history.indices.reversed().drop(skipLast)
            .firstNotNullOfOrNull { i ->
                history[i].resolveLocation()?.let { loc -> Pair(i, loc) }
            }

        val keepUntil = validEntry?.first ?: -1
        while (history.size - 1 > keepUntil) {
            history.removeLast()
        }

        return validEntry?.let { (i, loc) -> Pair(history[i], loc) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun listenForSpawnSet(event: PlayerSetSpawnEvent) {
        val player = event.player

        // must re-set the player's spawn on failed spawn
        if (event.cause != PlayerSetSpawnEvent.Cause.PLUGIN) {
            val pendingRespawn = pendingRespawnLocation.remove(player.uniqueId)
            if (pendingRespawn != null) {
                val coord = pendingRespawn.worldCoord
                val world = Bukkit.getWorld(coord.world)!! // already verified by logic that put this key here in the first place

                logger.debug(
                    "Overriding spawn for ${player.name}: moved from ${player.respawnLocation?.toStringCoord()} in ${player.respawnLocation?.world?.name} ${coord.toCoord()} in ${world.name}")

                // ignore world spawn set
                event.isCancelled = true

                // make sure to set respawn AFTER removing from set
                // otherwise you'll get an infinite loop
                val loc = coord.toLocation(world)
                loc.yaw = pendingRespawn.playerYaw // important for bed facing direction (left or right of head)
                player.respawnLocation = loc
                return
            }
        }

        if (event.cause == PlayerSetSpawnEvent.Cause.PLUGIN) return // suppress recording plugin respawns
        if (event.cause == PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN) return // suppress recording player respawn

        // I don't know why location would be null, but we can't
        // really do anything with a null location so return
        val location = event.location ?: return

        val respawnRecord = RespawnRecord(
            WorldCoord(location.x.toInt(), location.y.toInt(), location.z.toInt(), location.world.uid),
            event.cause,
            player.yaw
        )

        // deduplication check
        val history = playerRespawnRecorder.getOrPut(player.uniqueId) { ArrayDeque() }
        val mostRecentRecord = history.lastOrNull()

        // "Interacting with the bed again will still update which side of the bed is chosen for respawning, even if
        // the location of the spawn point itself does not change." - Minecraft Wiki, Bed
        if (mostRecentRecord?.worldCoord == respawnRecord.worldCoord) {
            if (mostRecentRecord.playerYaw != respawnRecord.playerYaw && respawnRecord.cause == PlayerSetSpawnEvent.Cause.BED) {
                history.removeLast()
            } else {
                return // record is probably exactly the same, just ignore it
            }
        }

        logger.debug("Added a new spawn record for player: ${player.name}: $respawnRecord")
        history.add(respawnRecord)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun hijackRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // isMissingRespawnBlock() was added in [this commit](https://github.com/PaperMC/Paper/pull/12422) in Paper 1.21.5
        // so that's as far as we can lower the API version, unless we want to do something hacky
        if (!event.isMissingRespawnBlock) { // only in 26.1+ i guess
            // skip hijack if the respawn is considered valid by vanilla
            val loc = event.respawnLocation

            logger.debug("Skipped hijacking spawn location for player ${player.name}: valid respawn at (${loc.x}, ${loc.y}, ${loc.z}) in ${loc.world.name}")
            return
        }

        // vanilla considers spawn invalid; attempt to find a valid respawn record for the player
        // drop the most recent record since that is the one that just failed
        val playerSpawnRecords = playerRespawnRecorder[player.uniqueId]
        val resolved = playerSpawnRecords?.let { record -> resolveValidRecord(record, skipLast = 1) }
        playerSpawnRecords?.let { pluginConfig.resolveFallback().compact(it, pluginConfig.spawnLimit) }

        if (resolved != null) {
            val (record, respawnLocation) = resolved
            val coord = record.worldCoord
            val world = Bukkit.getWorld(coord.world)!! // resolveValidRecord already validated this world exists

            // this gets overridden later by world spawn, but we need to set the respawn location
            // in the event so the player gets sent to our fallback spawn (not world spawn) on the
            // failed respawn attempt
            event.respawnLocation = respawnLocation
            pendingRespawnLocation[player.uniqueId] = record
            logger.debug("Hijacking spawn location for player ${player.name}: new location (${coord.toCoord()}) in world UID ${coord.world}")

            // if it's a respawn anchor, deplete it
            if (record.cause == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) {
                val block = world.getBlockAt(coord.x, coord.y, coord.z)
                val anchor = block.blockData as? RespawnAnchor

                if (anchor != null) {
                    anchor.charges -= 1
                    block.blockData = anchor
                } else {
                    // what even is this thing??
                    logger.warning("Expected RespawnAnchor at ${coord.toCoord()} but found ${block.blockData::class.simpleName}")
                }
            }
        } else {
            logger.debug("No valid respawn location found for player ${player.name}, falling back to world spawn")
        }

        // if no respawn location was found at this point, then the player will be put at world spawn
    }
}