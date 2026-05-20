// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.validation

import io.github.ganyuke.bedbackbetter.RespawnRecord
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.data.type.RespawnAnchor

object RespawnAnchorValidator : SpawnValidator {

    /**
     * Column offsets based on the provided priority grid (1-9).
     * Modifiers are (modX, modZ).
     */
    private val columnPriorities = listOf(
        Pair(0, 1),   // 1: +Z
        Pair(1, 0),   // 2: +X
        Pair(-1, 0),  // 3: -X
        Pair(0, -1),  // 4: -Z
        Pair(1, 1),   // 5: +X, +Z
        Pair(-1, 1),  // 6: -X, +Z
        Pair(1, -1),  // 7: +X, -Z
        Pair(-1, -1), // 8: -X, -Z
        Pair(0, 0)    // 9: Center (Inside the anchor column)
    )

    /**
     * Finds the highest priority valid respawn location around a Respawn Anchor.
     *
     * @param record The player respawn record.
     * @return A valid Location to spawn the player, or null if obstructed, missing, or depleted.
     */
    override fun validate(record: RespawnRecord): Location? {
        val savedLoc = Bukkit.getWorld(record.worldCoord.world)?.let { record.worldCoord.toLocation(it) } ?: return null
        val world = savedLoc.world ?: return null
        val anchorBlock = world.getBlockAt(savedLoc)

        // 1. Anchor Presence & State Check
        // The block must still be a Respawn Anchor
        if (anchorBlock.blockData !is RespawnAnchor) return null

        // A Respawn Anchor must have at least 1 charge to be a valid spawn point
        val anchorData = anchorBlock.blockData as RespawnAnchor
        if (anchorData.charges == 0) return null

        // 2. Pass 1: Iterate through all columns for Layer 1 and Layer 2
        for ((modX, modZ) in columnPriorities) {
            val targetColumn = anchorBlock.getRelative(modX, 0, modZ)

            // Check Layer 1 (Below anchor level)
            val layer1Loc = getValidSpawnAt(targetColumn.getRelative(0, -1, 0))
            if (layer1Loc != null) return faceTowardsAnchor(layer1Loc, anchorBlock.location)

            // Check Layer 2 (Same level as anchor)
            val layer2Loc = getValidSpawnAt(targetColumn)
            if (layer2Loc != null) return faceTowardsAnchor(layer2Loc, anchorBlock.location)
        }

        // 3. Pass 2: Iterate through all columns for Layer 3
        // Layer 3 is only checked if all layer 1 & 2 checks fail completely.
        for ((modX, modZ) in columnPriorities) {
            val targetColumn = anchorBlock.getRelative(modX, 0, modZ)

            // Check Layer 3 (Above anchor level)
            val layer3Loc = getValidSpawnAt(targetColumn.getRelative(0, 1, 0))
            if (layer3Loc != null) return faceTowardsAnchor(layer3Loc, anchorBlock.location)
        }

        // 4. Return null if all 27 checks fail (obstructed)
        return null
    }

    /**
     * Checks if a specific block space meets the 1x2x1 minimum area requirement.
     */
    private fun getValidSpawnAt(feetBlock: Block): Location? {
        val floorBlock = feetBlock.getRelative(0, -1, 0)
        val headBlock = feetBlock.getRelative(0, 1, 0)

        // A valid standard Minecraft spawn requires:
        // 1. The block standing on must be solid
        // 2. The block at the feet must be passable
        // 3. The block at the head must be passable
        if (floorBlock.isSolid && feetBlock.isPassable && headBlock.isPassable) {
            // Return exact center of the block for the player to stand on
            return feetBlock.location.add(0.5, 0.0, 0.5)
        }

        return null
    }

    /**
     * Orients the final spawn location so the player is looking at the anchor when they spawn.
     */
    private fun faceTowardsAnchor(spawnLoc: Location, anchorLoc: Location): Location {
        // If the player spawned inside the anchor (Priority 9 / center column),
        // avoid calculating direction to prevent a NaN error from a zero-length vector.
        if (spawnLoc.blockX == anchorLoc.blockX && spawnLoc.blockZ == anchorLoc.blockZ) {
            return spawnLoc
        }

        // Aim the player's eyes (approx 1.62 blocks up) at the center of the anchor block
        val centerOfAnchor = anchorLoc.clone().add(0.5, 0.5, 0.5)
        val spawnEyeLoc = spawnLoc.clone().add(0.0, 1.62, 0.0)

        val direction = centerOfAnchor.toVector().subtract(spawnEyeLoc.toVector())
        spawnLoc.direction = direction

        return spawnLoc
    }
}