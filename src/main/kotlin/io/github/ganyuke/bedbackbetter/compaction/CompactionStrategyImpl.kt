// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.compaction

import io.github.ganyuke.bedbackbetter.RespawnRecord
import org.bukkit.Location
import java.util.UUID

// keep only the most recent N spawn records
// most recent is the records at the end
object LastNCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location?) {
        // no idea why the limit would be zero but okay
        if (limit <= 0) {
            history.clear()
            return
        }

        // must guard in case history < limit
        repeat((history.size - limit).coerceAtLeast(0)) { history.removeFirst() }
    }
}

// keep only the most recent N valid spawn records
// most recent records at the end
// find N valid spawns at respawn time, drop all older
object LastNValidCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location?) {
        // no idea why the limit would be zero but okay
        if (limit <= 0) {
            history.clear()
            return
        }

        // keep track of N valid records
        var validCount = 0

        // find N-th valid records
        val lastKeptIndex = history.indexOfLast { resolver(it) != null && ++validCount == limit }

        // if not -1, that means we found N valid records
        if (lastKeptIndex != -1) repeat(lastKeptIndex) { history.removeFirst() }

        // don't clear anything, since we get a last index of -1 when we found only <N valid records
    }
}

// keep only the most recent N spawn records in dimension
// most recent is the records at the end
object LastNDimensionCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location?) {
        // no idea why the limit would be zero but okay
        if (limit <= 0) {
            history.clear()
            return
        }

        val dimCounts = mutableMapOf<UUID, Int>()
        // collect N records per dimension then drop the rest
        history.asReversed().retainAll { record ->
            val worldUuid = record.worldCoord.world
            val count = dimCounts.getOrDefault(worldUuid, 0)
            if (count < limit) {
                dimCounts[worldUuid] = count + 1
                true
            } else {
                false
            }
        }
    }
}

// keep only the most recent N spawn records in dimension
// most recent is the records at the end
object LastNValidDimensionCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location?) {
        // no idea why the limit would be zero but okay
        if (limit <= 0) {
            history.clear()
            return
        }

        val dimCounts = mutableMapOf<UUID, Int>()
        // collect N valid records per dimension then drop the rest
        history.asReversed().retainAll { record ->
            val worldUuid = record.worldCoord.world
            val count = dimCounts.getOrDefault(worldUuid, 0)
            if (count < limit) {
                if (resolver(record) != null) dimCounts[worldUuid] = count + 1
                true
            } else {
                false
            }
        }
    }
}