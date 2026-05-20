// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: ganyuke
package io.github.ganyuke.bedbackbetter

import io.github.ganyuke.bedbackbetter.config.DataPersister
import io.github.ganyuke.bedbackbetter.config.PluginConfig
import io.github.ganyuke.bedbackbetter.config.PluginConfig.PluginConfiguration
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class BedBackBetter : JavaPlugin() {
    private lateinit var respawnHijackListener: RespawnHijackListener
    private lateinit var pluginConfig: PluginConfiguration
    private lateinit var respawnRecordMap: MutableMap<UUID, ArrayDeque<RespawnRecord>>

    // periodically save state to disk in case of server crash
    private var autosaveTask: BukkitTask? = null

    // debug mode toggle to watch spawn changes
    private var playerSpawnWatcher: PlayerSpawnWatcher? = null

    override fun onEnable() {
        saveDefaultConfig()

        pluginConfig = PluginConfig.readConfig(this.logger, this.config)

        respawnRecordMap = DataPersister.loadRecords(dataFolder, this.logger, pluginConfig.debugMode)

        respawnHijackListener = RespawnHijackListener(this, pluginConfig, respawnRecordMap)
        server.pluginManager.registerEvents(respawnHijackListener, this)

        if (pluginConfig.debugMode) {
            playerSpawnWatcher = PlayerSpawnWatcher(this).also{ it.onEnable() }
        }

        // autosave task, disable autosave if zero
        if (pluginConfig.autosaveInterval > 0) {
            autosaveTask = Bukkit.getScheduler().runTaskTimer(
                this,
                Runnable { DataPersister.saveRecords(respawnRecordMap, dataFolder, this.logger, pluginConfig.debugMode) },
                pluginConfig.autosaveInterval * 60L * 20L, // delay first autosave, don't want to re-save immediately
                pluginConfig.autosaveInterval * 60L * 20L
            )
        }
    }

    override fun onDisable() {
        DataPersister.saveRecords(respawnRecordMap, dataFolder, this.logger, pluginConfig.debugMode)
        respawnHijackListener.onDisable()
        playerSpawnWatcher?.onDisable()
        autosaveTask?.cancel()
    }
}
