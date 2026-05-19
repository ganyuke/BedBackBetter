package io.github.ganyuke.bedFallback

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class BedFallback : JavaPlugin() {
    private lateinit var respawnHijackListener: RespawnHijackListener

    override fun onEnable() {
        // Plugin startup logic

        val playerRespawnRecordMap = HashMap<UUID, ArrayDeque<RespawnRecord>>()

        respawnHijackListener = RespawnHijackListener(this, playerRespawnRecordMap)

        server.pluginManager.registerEvents(respawnHijackListener, this)

        respawnHijackListener.onEnable()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        respawnHijackListener.onDisable()
    }
}
