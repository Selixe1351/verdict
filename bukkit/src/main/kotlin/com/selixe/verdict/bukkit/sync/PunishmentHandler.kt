package com.selixe.verdict.bukkit.sync

import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.message.Messages
import com.selixe.verdict.bukkit.util.IpHasher
import org.bukkit.Bukkit

class PunishmentHandler(
    private val messages: Messages,
    private val hasher: IpHasher
) {

    fun handle(event: PunishmentEvent) {
        val punishment = event.punishment

        if (!event.revoked) {
            enforce(event)
        }

        val key = if (event.revoked) "notify-revoked" else "notify-issued"
        val notice = messages.render(key, punishment, event.revoked)

        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission("verdict.notify") }
            .forEach { it.sendMessage(notice) }

        Bukkit.getConsoleSender().sendMessage(notice)
    }

    private fun enforce(event: PunishmentEvent) {
        val punishment = event.punishment
        val target = punishment.target?.let(Bukkit::getPlayer)

        when (punishment.type) {
            PunishmentType.BAN -> {
                val screen = messages.render("ban-screen", punishment)

                target?.kick(screen)

                val ipHash = punishment.ipHash ?: return

                Bukkit.getOnlinePlayers()
                    .filter { player -> player.address?.address?.hostAddress?.let(hasher::hash) == ipHash }
                    .forEach { it.kick(screen) }
            }

            PunishmentType.KICK -> target?.kick(messages.render("kick-screen", punishment))
            PunishmentType.MUTE -> target?.sendMessage(messages.render("muted", punishment))
            PunishmentType.WARN -> target?.sendMessage(messages.render("warned", punishment))
        }
    }

}
