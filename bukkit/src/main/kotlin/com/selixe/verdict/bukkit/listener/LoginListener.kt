package com.selixe.verdict.bukkit.listener

import com.selixe.verdict.bukkit.VerdictService
import com.selixe.verdict.bukkit.message.Messages
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import java.util.logging.Level
import java.util.logging.Logger

class LoginListener(
    private val service: VerdictService,
    private val messages: Messages,
    private val logger: Logger
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return
        }

        val ban = try {
            service.login(event.uniqueId, event.name, event.address.hostAddress)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to check bans for ${event.name}", e)
            return
        }

        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messages.render("ban-screen", ban))
        }
    }

}
