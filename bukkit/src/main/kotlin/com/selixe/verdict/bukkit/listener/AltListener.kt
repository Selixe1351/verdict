package com.selixe.verdict.bukkit.listener

import com.selixe.verdict.bukkit.VerdictService
import com.selixe.verdict.bukkit.message.Messages
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.logging.Level

class AltListener(
    private val plugin: Plugin,
    private val service: VerdictService,
    private val messages: Messages
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val banned = try {
                service.getBannedAlts(player.uniqueId)
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Failed to check alts for ${player.name}", e)
                return@Runnable
            }

            if (banned.isEmpty()) {
                return@Runnable
            }

            val notice = messages.altAlert(player.name, banned.map { it.name })

            plugin.server.scheduler.runTask(plugin, Runnable {
                Bukkit.getOnlinePlayers()
                    .filter { it.hasPermission("verdict.notify") }
                    .forEach { it.sendMessage(notice) }

                Bukkit.getConsoleSender().sendMessage(notice)
            })
        })
    }

}
