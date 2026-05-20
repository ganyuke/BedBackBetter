// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.validation

import io.github.ganyuke.bedbackbetter.RespawnRecord
import org.bukkit.Location

interface SpawnValidator {
    /**
     * Checks if the record is still valid.
     * @return The final, adjusted bukkit Location to spawn the player, or null if obstructed/missing.
     */
    fun validate(record: RespawnRecord): Location?
}