package io.github.ganyuke.bedFallback

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import java.util.UUID

data class WorldCoord(val x: Int, val y: Int, val z: Int, val world: UUID)

data class RespawnRecord(val worldCoord: WorldCoord, val cause: PlayerSetSpawnEvent.Cause, val playerYaw: Float)

