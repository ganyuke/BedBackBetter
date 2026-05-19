package io.github.ganyuke.bedFallback

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

enum class RespawnPolicy {
    LAST_N,
    LAST_N_VALID,
    LAST_N_IN_DIMENSION,
    LAST_N_VALID_IN_DIMENSION,
}

class RespawnHijackListener : Listener {
    val playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>
    val playerRespawnCounter: MutableMap<UUID, Int> = HashMap()
    val killThisPlayer: MutableSet<UUID> = HashSet()
    val playerRespawnLimit = 5 // TODO; make configurable
    val playerRespawnPolicy: RespawnPolicy
    val plugin: JavaPlugin

    constructor(plugin: JavaPlugin, playerRespawnRecorder: MutableMap<UUID, ArrayDeque<RespawnRecord>>) {
        this.plugin = plugin
        this.playerRespawnRecorder = playerRespawnRecorder
        playerRespawnPolicy = RespawnPolicy.LAST_N

    }

    private val lastRespawnLocations = mutableMapOf<UUID, Location>()
    private lateinit var task: BukkitTask

    fun onEnable() {
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                checkRespawnLocation(player)
            }
        }, 0L, 1L)
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

    fun onDisable() {
        lastRespawnLocations.clear()
        playerRespawnRecorder.clear()
        killThisPlayer.clear()
        playerRespawnCounter.clear()
        task.cancel()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun listenForSpawnSet(playerSetSpawnEvent: PlayerSetSpawnEvent) {
        val ply = playerSetSpawnEvent.player

        // must re-set the player's spawn on failed spawn
        if (ply.uniqueId in killThisPlayer && playerSetSpawnEvent.cause != PlayerSetSpawnEvent.Cause.PLUGIN) {
            // ignore world spawn set
            killThisPlayer.remove(ply.uniqueId)
            playerSetSpawnEvent.isCancelled = true
            // make sure to set respawn AFTER removing from set
            // otherwise you'll get an infinite loop
            ply.respawnLocation = chooseNextSpawn(ply)
            return
        }

        val deq = playerRespawnRecorder.getOrPut(ply.uniqueId) { ArrayDeque() }
        val sLoc = playerSetSpawnEvent.location

        if (sLoc != null) {
            val respawnRecord = RespawnRecord(
                WorldCoord(
                    sLoc.x.toInt(),
                    sLoc.y.toInt(),
                    sLoc.z.toInt(),
                    sLoc.world.uid
                ),
                playerSetSpawnEvent.cause
            )
            val previousRecord = deq.lastOrNull()
            if (previousRecord?.worldCoord != respawnRecord.worldCoord) {
                plugin.logger.info("new record: $respawnRecord for player: ${ply.name}")
                deq.add(respawnRecord)
            }

        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun hijackRespawn(playerRespawnEvent: PlayerRespawnEvent) {
        val ply = playerRespawnEvent.player
        val uuid = ply.uniqueId

        // if bed was valid, let vanilla handle respawn
        if (!playerRespawnEvent.isMissingRespawnBlock) {
            plugin.logger.info("valid respawn block (${playerRespawnEvent.respawnLocation.x}, ${playerRespawnEvent.respawnLocation.y}, ${playerRespawnEvent.respawnLocation.z}) for player: $uuid")
            playerRespawnCounter.remove(uuid)
            return
        }

        // invalid respawn location given

        // check if the player even has previous history
        val lengthOfHistory = playerRespawnRecorder[uuid]?.size
        if (lengthOfHistory == null || lengthOfHistory == 1) {
            plugin.logger.info("no history; invalid respawn block (${playerRespawnEvent.respawnLocation.x}, ${playerRespawnEvent.respawnLocation.y}, ${playerRespawnEvent.respawnLocation.z}) for player: $uuid")

            // let vanilla send to world spawn when player has no history
            // or player only has one history record (as in THIS event
            // is that record)
            playerRespawnCounter.remove(uuid)
            if (lengthOfHistory == 1) {
                // last record was invalid, clean up map
                playerRespawnRecorder.remove(uuid)
            }
            return
        }

        // check if player still has history slots left
        val spawnsUsed = playerRespawnCounter.getOrPut(uuid) { 1 }
        if (spawnsUsed > playerRespawnLimit) {
            plugin.logger.info("no history slots left; invalid respawn block (${playerRespawnEvent.respawnLocation.x}, ${playerRespawnEvent.respawnLocation.y}, ${playerRespawnEvent.respawnLocation.z}) for player: $uuid")
            // no respawn? let vanilla send them to world spawn
            playerRespawnCounter.remove(uuid)
            return
        }

        // cancel the respawn event, force another respawn with the new coordinates
        // since current spawn was invalid, remove the record from map
        val deq = playerRespawnRecorder.getValue(uuid)

        // pop the previous record, since that was clearly invalid
        deq.removeLast()

        // force the player to respawn
        killThisPlayer.add(uuid)

        plugin.logger.info("${lengthOfHistory - 1} history slots left for player: ${ply.name}")

        val spawnRecord = deq.last()
        val worldCoord = spawnRecord.worldCoord
        val (x, y, z) = worldCoord

        // temporarily spawn the player at the correct point we want
        // spawn here gets overrode by world spawn when the player actually respawns
        // so we need to fix this again when world spawn tries to get set
        val loc = chooseNextSpawn(ply)
        if (loc != null) {
            playerRespawnEvent.respawnLocation = loc
        }
        plugin.logger.info("attempting respawn at (${x}, ${y}, ${z}) for player: ${ply.name}")

    }

    fun isValid(record: RespawnRecord): Boolean = when (record.cause) {
        PlayerSetSpawnEvent.Cause.BED -> isBedValid(record.worldCoord)
        PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR -> isAnchorValid(record.worldCoord)
        else -> true
    }

    // check if the respawn anchor is even valid
    fun isAnchorValid(coord: WorldCoord): Boolean =
        (Bukkit.getWorld(coord.world)?.getBlockAt(coord.x, coord.y, coord.z)?.blockData as? RespawnAnchor)
            ?.let { it.charges > 0 } ?: false

    fun isBedValid(coord: WorldCoord): Boolean = true // TODO: Implementation

    fun chooseNextSpawn(ply: Player): Location? {
        val history = playerRespawnRecorder[ply.uniqueId] ?: return null

        return history.asReversed()
            .asSequence() // lazy evaluate
            .filter { isValid(it) }
            .map { ( coord, _ ) ->
                Location(
                    Bukkit.getWorld(coord.world),
                    coord.x.toDouble(),
                    coord.y.toDouble(),
                    coord.z.toDouble()
                )
            }
            .firstOrNull() // find first valid spawn
    }

}