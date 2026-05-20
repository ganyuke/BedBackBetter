package io.github.ganyuke.bedFallback.config

import io.github.ganyuke.bedFallback.compaction.FallbackPolicy
import io.github.ganyuke.bedFallback.compaction.LastNCompaction
import io.github.ganyuke.bedFallback.compaction.LastNDimensionCompaction
import io.github.ganyuke.bedFallback.compaction.LastNValidCompaction
import io.github.ganyuke.bedFallback.compaction.LastNValidDimensionCompaction

import org.bukkit.configuration.file.FileConfiguration
import java.util.logging.Logger

object PluginConfig {
    const val SPAWN_LIMIT_DEFAULT = 5
    val FALLBACK_POLICY_DEFAULT = FallbackPolicy.LAST_N_VALID

    data class PluginConfiguration(val spawnLimit: Int, val fallbackPolicy: FallbackPolicy, val debugMode: Boolean) {
        fun resolveFallback() = when (this.fallbackPolicy) {
            FallbackPolicy.LAST_N -> LastNCompaction
            FallbackPolicy.LAST_N_VALID -> LastNValidCompaction
            FallbackPolicy.LAST_N_IN_DIMENSION -> LastNDimensionCompaction
            FallbackPolicy.LAST_N_VALID_IN_DIMENSION -> LastNValidDimensionCompaction
        }
    }

    fun readConfig(logger: Logger, config: FileConfiguration) : PluginConfiguration {
        val spawnLimit = config.getInt("spawn-point-limit", -1).takeIf { it >= 0 }
            ?: SPAWN_LIMIT_DEFAULT.also {
                val actual = config.get("spawn-point-limit") // raw string is more trustworthy... YAML moment
                logger.warning("Invalid spawn limit (got: $actual). Using default: $it.")
            }

        val fallbackPolicy = config.getString("fallback-policy")
            ?.uppercase()
            ?.let { raw -> runCatching { FallbackPolicy.valueOf(raw) }.getOrNull() }
            ?: FALLBACK_POLICY_DEFAULT.also {
                logger.warning("Invalid fallback-policy set! Using default: '$it'")
            }

        val debugMode = config.getBoolean("debug-mode", false)

        return PluginConfiguration(spawnLimit, fallbackPolicy, debugMode)
    }
}