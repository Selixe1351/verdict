package com.selixe.verdict.bukkit.listener

import com.selixe.verdict.bukkit.VerdictService
import com.selixe.verdict.bukkit.message.Messages
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class ChatListener(
    private val service: VerdictService,
    private val messages: Messages,
    private val mutedCommands: Set<String>
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        if (isMuted(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val label = event.message
            .removePrefix("/")
            .substringBefore(' ')
            .substringAfter(':')
            .lowercase()

        if (label in mutedCommands && isMuted(event.player)) {
            event.isCancelled = true
        }
    }

    private fun isMuted(player: Player): Boolean {
        val mute = service.getActiveMute(player.uniqueId) ?: return false

        player.sendMessage(messages.render("muted", mute))

        return true
    }

}
