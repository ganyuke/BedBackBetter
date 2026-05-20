package io.github.ganyuke.bedFallback

import io.github.ganyuke.bedFallback.compaction.RespawnPolicy
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class BedFallback : JavaPlugin() {
    private lateinit var respawnHijackListener: RespawnHijackListener
    private lateinit var  playerSpawnWatcher: PlayerSpawnWatcher

    override fun onEnable() {
        // Plugin startup logic
        val playerRespawnRecordMap = HashMap<UUID, ArrayDeque<RespawnRecord>>()

        respawnHijackListener = RespawnHijackListener(this, playerRespawnRecordMap, RespawnPolicy.LAST_N_VALID)

        server.pluginManager.registerEvents(respawnHijackListener, this)

        playerSpawnWatcher = PlayerSpawnWatcher(this)
        playerSpawnWatcher.onEnable()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        respawnHijackListener.onDisable()
        playerSpawnWatcher.onDisable()
    }
}
