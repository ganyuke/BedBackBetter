package io.github.ganyuke.bedFallback.compaction

import io.github.ganyuke.bedFallback.RespawnRecord

interface CompactionStrategy {
    fun compact(history: ArrayDeque<RespawnRecord>, limit: Int)
}