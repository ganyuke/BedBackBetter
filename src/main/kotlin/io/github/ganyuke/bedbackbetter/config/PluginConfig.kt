// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter.config

import io.github.ganyuke.bedbackbetter.compaction.FallbackPolicy
import io.github.ganyuke.bedbackbetter.compaction.LastNCompaction
import io.github.ganyuke.bedbackbetter.compaction.LastNDimensionCompaction
import io.github.ganyuke.bedbackbetter.compaction.LastNValidCompaction
import io.github.ganyuke.bedbackbetter.compaction.LastNValidDimensionCompaction

import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Logger

object PluginConfig {
    const val SPAWN_LIMIT_DEFAULT = 5
    const val AUTO_SAVE_DEFAULT = 5
    val FALLBACK_POLICY_DEFAULT = FallbackPolicy.LAST_N_VALID

    data class PluginConfiguration(val spawnLimit: Int, val fallbackPolicy: FallbackPolicy, val autosaveInterval: Int, val fallbackMessage: String, val debugMode: Boolean) {
        fun resolveFallback() = when (this.fallbackPolicy) {
            FallbackPolicy.LAST_N -> LastNCompaction
            FallbackPolicy.LAST_N_VALID -> LastNValidCompaction
            FallbackPolicy.LAST_N_IN_DIMENSION -> LastNDimensionCompaction
            FallbackPolicy.LAST_N_VALID_IN_DIMENSION -> LastNValidDimensionCompaction
        }
    }

    fun readConfig(logger: Logger, config: FileConfiguration) : PluginConfiguration {
        val spawnLimit = config.getInt("spawn-point-limit").takeIf { it >= 0 } ?: run {
                val actual = config.get("spawn-point-limit")
                logger.warning("Invalid spawn-point-limit (got: $actual). Using default: $SPAWN_LIMIT_DEFAULT")
                SPAWN_LIMIT_DEFAULT
            }

        val fallbackPolicy = config.getString("fallback-policy")?.uppercase()
                ?.let { raw -> runCatching { FallbackPolicy.valueOf(raw) }.getOrNull() }
                ?: FALLBACK_POLICY_DEFAULT.also {
                    logger.warning("Invalid fallback-policy. Using default: '$it'")
                }

        val autosaveInterval = config.getInt("autosave-interval").takeIf { it >= 0 } ?: run {
                val actual = config.get("autosave-interval")
                logger.warning("Invalid autosave-interval (got: $actual). Using default: $AUTO_SAVE_DEFAULT")
                AUTO_SAVE_DEFAULT
            }

        val fallbackMessage = config.getString("fallback-respawn-message") ?: ""

        val debugMode = config.getBoolean("debug-mode", false)

        return PluginConfiguration(spawnLimit, fallbackPolicy, autosaveInterval, fallbackMessage, debugMode)
    }
}