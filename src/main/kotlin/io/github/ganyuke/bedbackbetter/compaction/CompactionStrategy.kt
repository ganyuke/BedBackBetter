// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.compaction

import io.github.ganyuke.bedbackbetter.RespawnRecord
import org.bukkit.Location

interface CompactionStrategy {
    fun compact(history: ArrayDeque<RespawnRecord>, limit: Int, resolver: (RespawnRecord) -> Location? = RespawnRecord::resolveLocation )
}