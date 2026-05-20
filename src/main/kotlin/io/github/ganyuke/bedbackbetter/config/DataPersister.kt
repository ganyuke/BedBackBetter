package io.github.ganyuke.bedbackbetter.config

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.bedbackbetter.PlayerSetSpawnCauseSerializer
import io.github.ganyuke.bedbackbetter.RespawnRecord
import io.github.ganyuke.bedbackbetter.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.File
import java.util.UUID
import java.util.logging.Logger

object DataPersister {
    private val json = Json {
        serializersModule = SerializersModule {
            contextual(UUID::class, UUIDSerializer)
            contextual(PlayerSetSpawnEvent.Cause::class, PlayerSetSpawnCauseSerializer)
        }
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun loadRecords(dataFolder: File, logger: Logger, debug: Boolean): MutableMap<@Contextual UUID, ArrayDeque<RespawnRecord>> {
        val file = File(dataFolder, "respawns.json")

        // return empty map if file does not exist/is empty
        if (!file.exists() || file.length() == 0L) return mutableMapOf()

        return try {
            val jsonString = file.readText()
            val loaded = json.decodeFromString<Map<UUID, List<RespawnRecord>>>(jsonString)
            if (debug) logger.info("[DEBUG] Successfully loaded respawn records for ${loaded.size} player(s)")
            return loaded.mapValues { (_, v) -> ArrayDeque(v) }.toMutableMap()
        } catch (e: Exception) {
            logger.severe("Failed to load JSON respawn records! Is the JSON file corrupted?")
            e.printStackTrace()
            mutableMapOf()
        }
    }

    fun saveRecords(data: MutableMap<@Contextual UUID, ArrayDeque<RespawnRecord>>, dataFolder: File, logger: Logger, debug: Boolean) {
        val file = File(dataFolder, "respawns.json")

        try {
            // need to do a bit of a dance since serializable doesn't know what an ArrayDeque is
            val serializable = data.mapValues { (_, v) -> v.toList() }
            val jsonString = json.encodeToString(serializable)
            file.writeText(jsonString)
            if (debug) logger.info("[DEBUG] Saved respawn records for ${data.size} player(s)")
        } catch (e: Exception) {
            logger.severe("Failed to save respawn records!")
            e.printStackTrace()
        }
    }
}