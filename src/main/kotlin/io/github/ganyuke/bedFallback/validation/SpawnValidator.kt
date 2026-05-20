package io.github.ganyuke.bedFallback.validation

import io.github.ganyuke.bedFallback.RespawnRecord
import org.bukkit.Location

interface SpawnValidator {
    /**
     * Checks if the record is still valid.
     * @return The final, adjusted bukkit Location to spawn the player, or null if obstructed/missing.
     */
    fun validate(record: RespawnRecord): Location?
}