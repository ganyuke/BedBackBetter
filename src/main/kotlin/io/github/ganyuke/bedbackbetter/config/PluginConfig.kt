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
    const val MAX_STORED_DEFAULT = 100
    const val AUTO_SAVE_DEFAULT = 5
    val FALLBACK_POLICY_DEFAULT = FallbackPolicy.LAST_N_VALID

    data class PluginConfiguration(
        val spawnLimit: Int, val maxStored: Int, val fallbackPolicy: FallbackPolicy,
        val autosaveInterval: Int, val fallbackMessage: String, val debugMode: Boolean
    ) {
        fun resolveFallback() = when (this.fallbackPolicy) {
            FallbackPolicy.LAST_N -> LastNCompaction
            FallbackPolicy.LAST_N_VALID -> LastNValidCompaction
            FallbackPolicy.LAST_N_IN_DIMENSION -> LastNDimensionCompaction
            FallbackPolicy.LAST_N_VALID_IN_DIMENSION -> LastNValidDimensionCompaction
        }
    }

    private fun FileConfiguration.getIntOrDefault(key: String, default: Int, logger: Logger, valid: (Int) -> Boolean = { true }): Int {
        val value = getInt(key)
        return if (valid(value)) value else {
            logger.warning("Invalid $key (got: ${get(key)}). Using default: $default")
            default
        }
    }

    fun readConfig(logger: Logger, config: FileConfiguration) : PluginConfiguration {
        val spawnLimit = config.getIntOrDefault("fallback-candidates", SPAWN_LIMIT_DEFAULT, logger) { it >= 0 }
        val maxStored = config.getIntOrDefault("max-stored-spawns", MAX_STORED_DEFAULT, logger) { it >= 0 }
        val autosaveInterval = config.getIntOrDefault("autosave-interval", AUTO_SAVE_DEFAULT, logger) { it >= 0 }

        val fallbackPolicy = config.getString("fallback-policy")?.uppercase()
                ?.let { raw -> runCatching { FallbackPolicy.valueOf(raw) }.getOrNull() }
                ?: FALLBACK_POLICY_DEFAULT.also {
                    logger.warning("Invalid fallback-policy. Using default: '$it'")
                }

        val fallbackMessage = config.getString("fallback-respawn-message") ?: ""

        val debugMode = config.getBoolean("debug-mode", false)

        return PluginConfiguration(spawnLimit, maxStored, fallbackPolicy, autosaveInterval, fallbackMessage, debugMode)
    }
}