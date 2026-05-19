package io.github.ganyuke.bedFallback

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

enum class RespawnPolicy {
    LAST_N,
    LAST_N_VALID,
    LAST_N_IN_DIMENSION,
    LAST_N_VALID_IN_DIMENSION,
}

class RespawnHijackListener : Listener {
    private val playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>
    private val pendingRespawnLocation: MutableMap<UUID, Location> = HashMap()

    private val playerRespawnLimit = 5 // TODO; make configurable
    private val playerRespawnPolicy: RespawnPolicy
    private val plugin: JavaPlugin


    constructor(plugin: JavaPlugin, playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>) {
        this.plugin = plugin
        this.playerRespawnRecorder = playerRespawnRecorder
        playerRespawnPolicy = RespawnPolicy.LAST_N

    }

    fun onDisable() {
        playerRespawnRecorder.clear()
        pendingRespawnLocation.clear()
    }

    fun WorldCoord.toLocation(world: World) =
        Location(world, x.toDouble(), y.toDouble(), z.toDouble())

    fun WorldCoord.toCoord() =
        "($x, $y, $z)"

    // check if the respawn anchor is even valid
    private fun isAnchorValid(coord: WorldCoord): Boolean {
        val world = Bukkit.getWorld(coord.world) ?: return false // bad world? just to be safe
        if (world.environment != World.Environment.NETHER) return false // respawn anchor only valid in nether
        // attempt to cast block to respawn anchor; failure indicates: not a respawn anchor anymore (i.e. it's gone)
        val anchor = world.getBlockAt(coord.x, coord.y, coord.z).blockData as? RespawnAnchor ?: return false
        return anchor.charges > 0 // check if the respawn anchor charges are still valid
    }

    // check if the bed is even valid
    private fun isBedValid(coord: WorldCoord): Boolean {
        val world = Bukkit.getWorld(coord.world) ?: return false // bad world? just to be safe
        if (world.environment != World.Environment.NORMAL) return false // bed only valid in the overworld
        // attempt to cast block to bed; failure indicates: not a bed anymore (i.e. it's gone)
        val bed = world.getBlockAt(coord.x, coord.y, coord.z).blockData as? Bed ?: return false
        // TODO: check obstructions to bed
        return true
    }

    private fun isValid(record: RespawnRecord): Boolean = when (record.cause) {
        PlayerSetSpawnEvent.Cause.BED -> isBedValid(record.worldCoord)
        PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR -> isAnchorValid(record.worldCoord)
        // Bed and Respawn Anchor are the only two ways to respawn in vanilla (other than world spawn)
        // so I won't bother trying to do the special logic for things other than vanilla respawns
        else -> true
    }

    private fun resolveValidRecord(history: ArrayDeque<RespawnRecord>, skipLast: Int = 1): RespawnRecord? {
        val validIndex = history.indices.reversed().drop(skipLast)
            .firstOrNull { isValid(history[it]) }

        // drop everything before stale record since they are invalid
        // no valid record means clear everything
        val keepUntil = validIndex ?: -1
        while (history.size - 1 > keepUntil) {
            history.removeLast()
        }

        return if (validIndex != null) history[validIndex] else null
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
            event.cause
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
        val respawnRecord = playerRespawnRecorder[player.uniqueId]
            // drop the most recent record since that is the one that just failed
            // then iterate backward through the records to find the first valid
            ?.let { resolveValidRecord(it, skipLast = 1) }

        val world = respawnRecord
            ?.let { record -> Bukkit.getWorld(record.worldCoord.world) }

        val respawnLocation = world?.let { world -> respawnRecord.worldCoord.toLocation(world) }

        if (respawnLocation != null) {
            // this gets overridden later by world spawn, but we need to set the respawn location
            // in the event so the player gets sent to our fallback spawn (not world spawn) on the
            // failed respawn attempt
            event.respawnLocation = respawnLocation

            val coord = respawnRecord.worldCoord

            // if it's a respawn anchor, need to deplete it
            if (respawnRecord.cause == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) {
                val block = world.getBlockAt(coord.x, coord.y, coord.z)
                val anchor = block.blockData as? RespawnAnchor

                if (anchor != null) {
                    anchor.charges -= 1
                    block.blockData = anchor
                } else {
                    plugin.logger.warning("Expected RESPAWN ANCHOR at ${coord.toCoord()} but failed to cast.")
                    // what even is this thing??
                }
            }

            pendingRespawnLocation[player.uniqueId] = respawnLocation
            plugin.logger.info("Attempting respawn at (${coord.toCoord()}}) for player: ${player.name}")
        } else {
            plugin.logger.info("No valid respawn location found for player: ${player.name}")
        }

        // if no respawn location was found at this point, then the player will be put at world spawn
    }
}