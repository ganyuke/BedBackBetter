// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedbackbetter.validation.BedValidator
import io.github.ganyuke.bedbackbetter.validation.RespawnAnchorValidator
import org.bukkit.Location
import org.bukkit.World
import java.util.UUID

data class WorldCoord(val x: Int, val y: Int, val z: Int, val world: UUID) {
    fun toLocation(world: World) = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    fun toCoord() = "($x, $y, $z)"
}

data class RespawnRecord(val worldCoord: WorldCoord, val cause: PlayerSetSpawnEvent.Cause, val playerYaw: Float) {
    fun resolveLocation(): Location? = when (this.cause) {
        PlayerSetSpawnEvent.Cause.BED -> BedValidator.validate(this)
        PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR -> RespawnAnchorValidator.validate(this)
        // Bed and Respawn Anchor are the only two ways to respawn in vanilla (other than world spawn)
        // so and i don't want to bother trying to do the special logic for things other than vanilla respawns
        else -> null
    }
}
