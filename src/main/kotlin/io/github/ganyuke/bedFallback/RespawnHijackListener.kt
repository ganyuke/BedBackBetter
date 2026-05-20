package io.github.ganyuke.bedFallback

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedFallback.compaction.CompactionStrategy
import io.github.ganyuke.bedFallback.compaction.LastNCompaction
import io.github.ganyuke.bedFallback.compaction.LastNDimensionCompaction
import io.github.ganyuke.bedFallback.compaction.LastNValidCompaction
import io.github.ganyuke.bedFallback.compaction.LastNValidDimensionCompaction
import io.github.ganyuke.bedFallback.compaction.RespawnPolicy
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class RespawnHijackListener : Listener {
    private val playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>
    private val pendingRespawnLocation: MutableMap<UUID, Location> = HashMap()

    private val playerRespawnLimit = 5 // TODO; make configurable
    private val compactionStrategy: CompactionStrategy
    private val plugin: JavaPlugin


    constructor(plugin: JavaPlugin, playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>, playerRespawnPolicy: RespawnPolicy) {
        this.plugin = plugin
        this.playerRespawnRecorder = playerRespawnRecorder
        this.compactionStrategy = when (playerRespawnPolicy) {
            RespawnPolicy.LAST_N -> LastNCompaction
            RespawnPolicy.LAST_N_VALID -> LastNValidCompaction
            RespawnPolicy.LAST_N_IN_DIMENSION -> LastNDimensionCompaction
            RespawnPolicy.LAST_N_VALID_IN_DIMENSION -> LastNValidDimensionCompaction
        }
    }

    fun onDisable() {
        playerRespawnRecorder.clear()
        pendingRespawnLocation.clear()
    }

    private fun resolveValidRecord(history: ArrayDeque<RespawnRecord>, skipLast: Int = 1): Pair<RespawnRecord, Location>? {
        val validEntry = history.indices.reversed().drop(skipLast)
            .firstNotNullOfOrNull { i ->
                resolveLocation(history[i])?.let { loc -> Pair(i, loc) }
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
            plugin.logger.info("$pendingRespawn, ${event.cause}")

            if (pendingRespawn != null) {
                plugin.logger.info("activating override")

                // ignore world spawn set
                event.isCancelled = true

                // make sure to set respawn AFTER removing from set
                // otherwise you'll get an infinite loop
                player.respawnLocation = pendingRespawn
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

        // record the new spawn location in our stack
        val history = playerRespawnRecorder.getOrPut(player.uniqueId) { ArrayDeque() }
        if (history.lastOrNull()?.worldCoord != respawnRecord.worldCoord) {
            plugin.logger.info("new record: $respawnRecord for player: ${player.name}")
            history.add(respawnRecord)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun hijackRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // skip hijack if the respawn is considered valid by vanilla
        if (!event.isMissingRespawnBlock) {
            val loc = event.respawnLocation
            plugin.logger.info("Valid respawn block at (${loc.x}, ${loc.y}, ${loc.z}) for player: ${player.name}")
            return
        }

        // vanilla considers spawn invalid; attempt to find a valid respawn record for the player
        // drop the most recent record since that is the one that just failed
        val playerSpawnRecords = playerRespawnRecorder[player.uniqueId]
        val resolved = playerSpawnRecords?.let { record -> resolveValidRecord(record, skipLast = 1) }
        playerSpawnRecords?.let { compactionStrategy.compact(it, playerRespawnLimit) }

        if (resolved != null) {
            val (record, respawnLocation) = resolved
            val coord = record.worldCoord
            val world = Bukkit.getWorld(coord.world)!! // resolveValidRecord already validated this world exists

            // this gets overridden later by world spawn, but we need to set the respawn location
            // in the event so the player gets sent to our fallback spawn (not world spawn) on the
            // failed respawn attempt
            event.respawnLocation = respawnLocation
            pendingRespawnLocation[player.uniqueId] = respawnLocation
            plugin.logger.info("Attempting respawn at (${coord.toCoord()}) for player: ${player.name}")

            // if it's a respawn anchor, deplete it
            if (record.cause == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) {
                val block = world.getBlockAt(coord.x, coord.y, coord.z)
                val anchor = block.blockData as? RespawnAnchor

                if (anchor != null) {
                    anchor.charges -= 1
                    block.blockData = anchor
                } else {
                    // what even is this thing??
                    plugin.logger.warning("Expected RespawnAnchor at ${coord.toCoord()} but found ${block.blockData::class.simpleName}")
                }
            }
        } else {
            plugin.logger.info("No valid respawn location found for player: ${player.name}, falling back to world spawn")
        }

        // if no respawn location was found at this point, then the player will be put at world spawn
    }
}