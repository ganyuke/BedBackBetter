// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.validation

import io.github.ganyuke.bedbackbetter.RespawnRecord
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Bed
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

object BedValidator : SpawnValidator {

    /**
     * Main entry point to find a valid bed respawn location.
     *
     * @param record The player respawn record.
     * @return A valid Location to spawn the player, or null if obstructed/missing.
     */
    override fun validate(record: RespawnRecord): Location? {
        val savedLoc = Bukkit.getWorld(record.worldCoord.world)?.let { record.worldCoord.toLocation(it) } ?: return null
        val world = savedLoc.world ?: return null
        val savedBlock = world.getBlockAt(savedLoc)
        val playerYawWhenSet = record.playerYaw

        // 1. Bed Presence Check
        // "as long as there is a bed present in the same location, the player can respawn there."
        if (savedBlock.blockData !is Bed) return null

        // 2. Find the Head of the bed
        // "the location of the head of the bed is saved... if a bed is in that space (whether it is the foot or the head) then the respawn works."
        val bedData = savedBlock.blockData as Bed
        val headBlock = if (bedData.part == Bed.Part.HEAD) {
            savedBlock
        } else {
            savedBlock.getRelative(bedData.facing)
        }

        // Safety check in case the block next to the foot isn't actually the head
        if (headBlock.blockData !is Bed || (headBlock.blockData as Bed).part != Bed.Part.HEAD) return null

        val bedFacing = (headBlock.blockData as Bed).facing

        // 3. Determine Search Direction (Clockwise vs Counter-Clockwise)
        // "Which block is chosen between the two corresponds to which direction the player was facing..."
        val preferRight = prefersRightSide(bedFacing, playerYawWhenSet)

        // 4. Get the 10 adjacent blocks in the correct circular sequence
        val searchSequence = get10AdjacentBlocks(headBlock, bedFacing, preferRight)

        // 5. Search the 10 blocks for a valid column
        for (candidateBlock in searchSequence) {
            val validLoc = getValidSpawnInColumn(candidateBlock)
            if (validLoc != null) {
                return faceTowardsHead(validLoc, headBlock.location)
            }
        }

        // 6. Fallback Priority: Above the Bed
        // "If all of the 10 blocks... are obstructed, the block above the head of the bed is checked, followed by the block above the foot of the bed."
        val aboveHead = headBlock.getRelative(BlockFace.UP)
        if (aboveHead.isPassable && aboveHead.getRelative(BlockFace.UP).isPassable) {
            // Bed height is 0.5625 blocks
            val loc = headBlock.location.add(0.5, 0.5625, 0.5)
            return faceTowardsHead(loc, headBlock.location)
        }

        val footBlock = headBlock.getRelative(bedFacing.oppositeFace)
        val aboveFoot = footBlock.getRelative(BlockFace.UP)
        if (aboveFoot.isPassable && aboveFoot.getRelative(BlockFace.UP).isPassable) {
            val loc = footBlock.location.add(0.5, 0.5625, 0.5)
            return faceTowardsHead(loc, headBlock.location)
        }

        // "If a bed is obstructed, the player's spawn point is cleared... respawns at the world spawn point."
        return null
    }

    /**
     * Determines whether to check the Right side (Clockwise) or Left side (Counter-Clockwise) first.
     */
    private fun prefersRightSide(bedFacing: BlockFace, playerYaw: Float): Boolean {
        // "a bed facing west will respawn the player on the south side of the bed if they are facing towards the north... perfectly aligned... right side takes priority."
        val rightFace = getRightFace(bedFacing)

        // Convert player yaw to a directional vector
        val pitchRad = 0.0
        val yawRad = Math.toRadians(playerYaw.toDouble() + 90.0) // Bukkit yaw offset
        val playerVec = Vector(cos(yawRad) * cos(pitchRad), 0.0, sin(yawRad) * cos(pitchRad))

        val rightVec = rightFace.direction

        // Dot product checks how closely the player was looking on the right side of the bed.
        val dotProduct = playerVec.dot(rightVec)

        // If they were looking on the right side (dot > 0), the game spawns them on the left side (preferRight = false).
        // If perfectly aligned (dot == 0.0) or looking left, right side takes priority.
        return dotProduct <= 0.0
    }

    /**
     * Generates the 10 adjacent blocks around a 1x2 bed in a clockwise or counter-clockwise circle.
     */
    private fun get10AdjacentBlocks(head: Block, facing: BlockFace, clockwise: Boolean): List<Block> {
        val fwd = facing
        val back = facing.oppositeFace
        val right = getRightFace(facing)
        val left = getLeftFace(facing)

        // The 10 relative offsets around a 1x2 structure (Head is 0,0. Foot is back 1)
        val clockwisePath = listOf(
            right,                           // Right of Head
            right.add(back),                 // Right of Foot
            right.add(back).add(back),       // Corner: Right-Back
            back.add(back),                  // Back of Foot
            left.add(back).add(back),        // Corner: Left-Back
            left.add(back),                  // Left of Foot
            left,                            // Left of Head
            left.add(fwd),                   // Corner: Left-Front
            fwd,                             // Front of Head
            right.add(fwd)                   // Corner: Right-Front
        )

        val path = if (clockwise) clockwisePath else clockwisePath.reversed()

        // Map abstract BlockFaces/Vectors to physical Blocks in the world
        return path.map { faceModifier ->
            head.world.getBlockAt(head.x + faceModifier.modX, head.y, head.z + faceModifier.modZ)
        }
    }

    /**
     * Checks if a specific (X, Z) column is valid for respawning.
     */
    private fun getValidSpawnInColumn(bedLevelBlock: Block): Location? {
        // "the block at the level of the bed must be air or non-solid"
        if (!bedLevelBlock.isPassable) return null

        val world = bedLevelBlock.world
        val startY = bedLevelBlock.y
        val x = bedLevelBlock.x
        val z = bedLevelBlock.z

        // "...there must be a space with a solid block below it and two non-colliding blocks for the player to stand in 0-2 blocks below the bed."
        // We check offsets 0, -1, and -2 for the player's feet.
        for (yOffset in 0 downTo -2) {
            val feetY = startY + yOffset

            val floorBlock = world.getBlockAt(x, feetY - 1, z)
            val feetBlock = world.getBlockAt(x, feetY, z)
            val headBlock = world.getBlockAt(x, feetY + 1, z)

            if (floorBlock.isSolid && feetBlock.isPassable && headBlock.isPassable) {
                // Return center of block
                return Location(world, x + 0.5, feetY.toDouble(), z + 0.5)
            }
        }
        return null
    }

    /**
     * "The player is always respawned facing the head of the bed, the same as for waking up."
     */
    private fun faceTowardsHead(spawnLoc: Location, headLoc: Location): Location {
        val direction = headLoc.toVector().subtract(spawnLoc.toVector())
        spawnLoc.direction = direction
        return spawnLoc
    }

    // --- Utility Functions for BlockFace Math ---

    private fun getRightFace(face: BlockFace): BlockFace = when(face) {
        BlockFace.NORTH -> BlockFace.EAST
        BlockFace.EAST -> BlockFace.SOUTH
        BlockFace.SOUTH -> BlockFace.WEST
        BlockFace.WEST -> BlockFace.NORTH
        else -> face
    }

    private fun getLeftFace(face: BlockFace): BlockFace = when(face) {
        BlockFace.NORTH -> BlockFace.WEST
        BlockFace.WEST -> BlockFace.SOUTH
        BlockFace.SOUTH -> BlockFace.EAST
        BlockFace.EAST -> BlockFace.NORTH
        else -> face
    }

    // Helper to combine BlockFaces for corners easily
    private fun BlockFace.add(other: BlockFace): BlockFace {
        val newModX = this.modX + other.modX
        val newModZ = this.modZ + other.modZ
        return BlockFace.entries.firstOrNull { it.modX == newModX && it.modZ == newModZ && it.modY == 0 }
            ?: BlockFace.SELF
    }
}