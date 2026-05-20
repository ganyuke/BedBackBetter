// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedbackbetter.validation.BedValidator
import io.github.ganyuke.bedbackbetter.validation.RespawnAnchorValidator
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.Location
import org.bukkit.World
import java.util.UUID

// we're using the Java UUID not the Kotlin Uuid which I assume would skirt this ultimate issue...
// apparently Kotlin's serializer doesn't know how to serialize a fancy string with stabbies in between
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

// and need to do that for the enum as well...
object PlayerSetSpawnCauseSerializer : KSerializer<PlayerSetSpawnEvent.Cause> {
    override val descriptor = PrimitiveSerialDescriptor("PlayerSetSpawnEvent.Cause", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PlayerSetSpawnEvent.Cause) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): PlayerSetSpawnEvent.Cause = PlayerSetSpawnEvent.Cause.valueOf(decoder.decodeString())
}

@Serializable
data class WorldCoord(val x: Int, val y: Int, val z: Int, @Contextual val world: UUID) {
    fun toLocation(world: World) = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    fun toCoord() = "($x, $y, $z)"
}

@Serializable
data class RespawnRecord(val worldCoord: WorldCoord, @Contextual val cause: PlayerSetSpawnEvent.Cause, val playerYaw: Float) {
    fun resolveLocation(): Location? = when (this.cause) {
        PlayerSetSpawnEvent.Cause.BED -> BedValidator.validate(this)
        PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR -> RespawnAnchorValidator.validate(this)
        // Bed and Respawn Anchor are the only two ways to respawn in vanilla (other than world spawn)
        // so and i don't want to bother trying to do the special logic for things other than vanilla respawns
        else -> null
    }
}
