package io.github.ganyuke.bedFallback.compaction

import io.github.ganyuke.bedFallback.RespawnRecord
import io.github.ganyuke.bedFallback.resolveLocation
import java.util.UUID

// keep only the most recent N spawn records
// most recent is the records at the end
object LastNCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int) {
        history.subList(0, maxOf(0, history.size - limit)).clear()
    }
}

// keep only the most recent N valid spawn records
// most recent records at the end
// find N valid spawns at respawn time, drop all older
object LastNValidCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int) {
        var validCount = 0
        val lastKeptIndex = history.indexOfLast { resolveLocation(it) != null && ++validCount == limit }
        if (lastKeptIndex != -1) history.subList(0, lastKeptIndex).clear()
    }
}

// keep only the most recent N spawn records in dimension
// most recent is the records at the end
object LastNDimensionCompaction : CompactionStrategy {
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int) {
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
    override fun compact(history: ArrayDeque<RespawnRecord>, limit: Int) {
        val dimCounts = mutableMapOf<UUID, Int>()
        // collect N valid records per dimension then drop the rest
        history.asReversed().retainAll { record ->
            val worldUuid = record.worldCoord.world
            val count = dimCounts.getOrDefault(worldUuid, 0)
            if (count < limit) {
                if (resolveLocation(record) != null) dimCounts[worldUuid] = count + 1
                true
            } else {
                false
            }
        }
    }
}