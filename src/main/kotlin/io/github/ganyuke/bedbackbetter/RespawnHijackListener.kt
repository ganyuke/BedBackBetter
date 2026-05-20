// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import io.github.ganyuke.bedbackbetter.config.PluginConfig.PluginConfiguration
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import net.kyori.adventure.text.minimessage.MiniMessage
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

    // debounce the next plugin event. could use the previous pendingRespawnLocation but that is a bit messy conceptually
    private val suppressNextPluginEvent: MutableSet<UUID> = HashSet()

    private val logger: Logger
    private val pluginConfig: PluginConfiguration

    private val miniMessage = MiniMessage.miniMessage()

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

        when (event.cause) {
            PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN -> {
                if (pendingRespawnLocation[player.uniqueId] != null) {
                    // must re-set the player's spawn on failed spawn
                    val pendingRespawn = pendingRespawnLocation.remove(player.uniqueId)
                    if (pendingRespawn != null) {
                        val coord = pendingRespawn.worldCoord
                        val world = Bukkit.getWorld(coord.world)!! // already verified by logic that put this key here in the first place

                        logger.debug(
                            "Overriding spawn for ${player.name}: ${event.location?.toStringCoord()} in '${event.location?.world?.name}' -> ${coord.toCoord()} in '${world.name}'")

                        // ignore world spawn set
                        event.isCancelled = true

                        // suppress the next plugin event. don't want to re-record a new row unnecessarily
                        suppressNextPluginEvent.add(player.uniqueId)

                        // make sure to set respawn AFTER removing from set
                        // otherwise you'll get an infinite loop
                        val loc = coord.toLocation(world)
                        loc.yaw = pendingRespawn.playerYaw // important for bed facing direction (left or right of head)
                        player.respawnLocation = loc // this will run synchronously

                        if (pluginConfig.fallbackMessage.isNotBlank())
                            player.sendMessage(miniMessage.deserialize(pluginConfig.fallbackMessage))

                        return
                    }
                }
            }
            PlayerSetSpawnEvent.Cause.PLUGIN -> {
                if (suppressNextPluginEvent.remove(player.uniqueId)) {
                    logger.debug("Suppressed a plugin-fired PlayerSetSpawnEvent")
                    return
                }
            }
            else -> {}
        }

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
                logger.debug("Updating an existing spawn record for player: ${player.name}: $respawnRecord")
            } else {
                return // record is probably exactly the same, just ignore it
            }
        } else {
            logger.debug("Added a new spawn record for player ${player.name}: $respawnRecord")
        }

        // drop the oldest records if maxStored is enabled
        if (pluginConfig.maxStored != 0 && history.size >= pluginConfig.maxStored) {
            history.removeFirstOrNull()
            logger.debug("Player ${player.name} was over ${pluginConfig.maxStored} stored respawn point(s), dropped oldest from memory")
        }

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
        val playerSpawnRecords = playerRespawnRecorder[player.uniqueId] ?: return // can't do anything with a null record
        val resolved = resolveValidRecord(playerSpawnRecords, skipLast = 1) // drops records (like vanilla) until valid

        // get some memory savings for records that will never see the light of day
        val sizeBefore = playerSpawnRecords.size
        playerSpawnRecords.let { pluginConfig.resolveFallback().compact(it, pluginConfig.spawnLimit) }
        val sizeAfter = playerSpawnRecords.size
        if (sizeAfter < sizeBefore) logger.debug("Compacted spawn records for ${player.name}: $sizeBefore -> $sizeAfter")

        if (resolved != null) {
            val (record, respawnLocation) = resolved
            val coord = record.worldCoord
            val world = Bukkit.getWorld(coord.world)!! // resolveValidRecord already validated this world exists

            // this gets overridden later by world spawn, but we need to set the respawn location
            // in the event so the player gets sent to our fallback spawn (not world spawn) on the
            // failed respawn attempt
            event.respawnLocation = respawnLocation
            pendingRespawnLocation[player.uniqueId] = record
            logger.debug("Hijacking spawn location for player ${player.name}: new location ${coord.toCoord()} in '${respawnLocation.world.name}'")

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