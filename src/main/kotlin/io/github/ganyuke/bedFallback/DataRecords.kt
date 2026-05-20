package io.github.ganyuke.bedFallback

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedFallback.validation.BedValidator
import io.github.ganyuke.bedFallback.validation.RespawnAnchorValidator
import org.bukkit.Location
import org.bukkit.World
import java.util.UUID

data class WorldCoord(val x: Int, val y: Int, val z: Int, val world: UUID)

data class RespawnRecord(val worldCoord: WorldCoord, val cause: PlayerSetSpawnEvent.Cause, val playerYaw: Float)

fun WorldCoord.toLocation(world: World) =
    Location(world, x.toDouble(), y.toDouble(), z.toDouble())

fun WorldCoord.toCoord() =
    "($x, $y, $z)"

fun resolveLocation(record: RespawnRecord): Location? {
    return when (record.cause) {
        PlayerSetSpawnEvent.Cause.BED -> BedValidator.validate(record)
        PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR -> RespawnAnchorValidator.validate(record)
        // Bed and Respawn Anchor are the only two ways to respawn in vanilla (other than world spawn)
        // so and i don't want to bother trying to do the special logic for things other than vanilla respawns
        else -> null
    }
}
