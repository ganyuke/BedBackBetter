package io.github.ganyuke.bedFallback.compaction

import io.github.ganyuke.bedFallback.RespawnRecord
import org.bukkit.Location

interface CompactionStrategy {
    fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location? = RespawnRecord::resolveLocation )
}