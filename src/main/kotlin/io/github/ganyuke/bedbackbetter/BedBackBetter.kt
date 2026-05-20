// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import io.github.ganyuke.bedbackbetter.config.PluginConfig
import io.github.ganyuke.bedbackbetter.config.PluginConfig.PluginConfiguration
import org.bukkit.plugin.java.JavaPlugin

class BedBackBetter : JavaPlugin() {
    private lateinit var respawnHijackListener: RespawnHijackListener
    private lateinit var pluginConfig: PluginConfiguration

    // debug mode toggle to watch spawn changes
    private var playerSpawnWatcher: PlayerSpawnWatcher? = null

    override fun onEnable() {
        saveDefaultConfig()

        pluginConfig = PluginConfig.readConfig(this.logger, this.config)

        respawnHijackListener = RespawnHijackListener(this, pluginConfig)
        server.pluginManager.registerEvents(respawnHijackListener, this)

        if (pluginConfig.debugMode) {
            playerSpawnWatcher = PlayerSpawnWatcher(this).also{ it.onEnable() }
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        respawnHijackListener.onDisable()
        playerSpawnWatcher?.onDisable()
    }
}
