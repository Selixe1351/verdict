package com.selixe.verdict.bukkit.command

import com.selixe.directive.Command
import com.selixe.directive.CommandContext
import com.selixe.directive.CompletionContext
import com.selixe.directive.CommandFailure
import com.selixe.directive.command
import com.selixe.directive.paper.sender
import com.selixe.directive.parseDuration
import com.selixe.directive.string
import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.VerdictService
import com.selixe.verdict.bukkit.menu.HistoryMenu
import com.selixe.verdict.bukkit.message.Messages
import com.selixe.verdict.bukkit.model.Staff
import com.selixe.verdict.bukkit.util.isIp
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.time.Duration

class PunishmentCommands(
    private val service: VerdictService,
    private val messages: Messages,
    private val menu: HistoryMenu
) {

    fun create(): List<Command> = listOf(
        punish("ban", "verdict.ban", PunishmentType.BAN),
        punish("tempban", "verdict.ban", PunishmentType.BAN, temporary = true),
        punish("mute", "verdict.mute", PunishmentType.MUTE),
        punish("tempmute", "verdict.mute", PunishmentType.MUTE, temporary = true),
        punish("kick", "verdict.kick", PunishmentType.KICK),
        punish("warn", "verdict.warn", PunishmentType.WARN),
        revoke("unban", "verdict.unban", service::unban),
        revoke("unmute", "verdict.unmute", service::unmute),
        banIp("ipban", listOf("banip")),
        banIp("tempipban", temporary = true),
        unbanIp(),
        history(),
        alts()
    )

    private fun punish(
        name: String,
        permission: String,
        type: PunishmentType,
        temporary: Boolean = false
    ) = command(name, permission = permission, usage = usage("<player>", type.lasting, temporary), async = true) {
        completes { context -> complete(context, type.lasting) }

        executes { context ->
            val target = findProfile(context)

            if (type == PunishmentType.KICK && Bukkit.getPlayer(target.uuid) == null) {
                throw CommandFailure("${target.name} is not online.")
            }

            val (duration, reason) = if (type.lasting) {
                durationAndReason(context, temporary)
            } else {
                null to reason(context, 1)
            }

            notify(context, service.punish(type, target, reason, staff(context), duration))
        }
    }

    private fun revoke(
        name: String,
        permission: String,
        action: (IProfile, Staff) -> IPunishment
    ) = command(name, permission = permission, usage = "<player>", async = true) {
        completes { context -> complete(context, false) }

        executes { context ->
            notify(context, action(findProfile(context), staff(context)), revoked = true)
        }
    }

    private fun banIp(
        name: String,
        aliases: List<String> = emptyList(),
        temporary: Boolean = false
    ) = command(
        name,
        aliases = aliases,
        permission = "verdict.ipban",
        usage = usage("<address|player>", true, temporary),
        async = true
    ) {
        completes { context -> complete(context, true) }

        executes { context ->
            val input = context.string(0, "address or player")
            val (duration, reason) = durationAndReason(context, temporary)
            val staff = staff(context)

            val punishment = if (isIp(input)) {
                service.banIp(input, reason, staff, duration)
            } else {
                service.banIp(findProfile(context), reason, staff, duration)
            }

            notify(context, punishment)
        }
    }

    private fun unbanIp() = command(
        "unbanip",
        aliases = listOf("unipban"),
        permission = "verdict.ipban",
        usage = "<address|player>",
        async = true
    ) {
        completes { context -> complete(context, false) }

        executes { context ->
            val input = context.string(0, "address or player")
            val ipHash = if (isIp(input)) service.hashIp(input) else findProfile(context).lastIpHash

            notify(context, service.unbanIp(ipHash, staff(context)), revoked = true)
        }
    }

    private fun history() = command("history", permission = "verdict.history", usage = "<player>", async = true) {
        completes { context -> complete(context, false) }

        executes { context ->
            val viewer = context.sender as? Player ?: throw CommandFailure("Only players can open the history menu.")
            val target = findProfile(context)
            val history = service.getHistory(target.uuid)

            if (history.isEmpty()) {
                throw CommandFailure("${target.name} has no punishments.")
            }

            menu.open(viewer, target.name, history)
        }
    }

    private fun alts() = command("alts", permission = "verdict.alts", usage = "<player>", async = true) {
        completes { context -> complete(context, false) }

        executes { context ->
            val target = findProfile(context)
            val alts = service.getAlts(target.uuid)

            if (alts.isEmpty()) {
                throw CommandFailure("${target.name} has no known alts.")
            }

            val names = alts.joinToString("<gray>, ") {
                val color = if (service.getActiveBan(it.uuid) != null) "<red>" else "<white>"

                color + MiniMessage.miniMessage().escapeTags(it.name)
            }

            context.reply("${messages.prefix}<gray>Alts of <white>${MiniMessage.miniMessage().escapeTags(target.name)}<gray>: $names")
        }
    }

    private fun notify(context: CommandContext, punishment: IPunishment, revoked: Boolean = false) {
        if (context.actor.isConsole || context.actor.hasPermission("verdict.notify")) {
            return
        }

        val key = if (revoked) "notify-revoked" else "notify-issued"

        context.sender.sendMessage(messages.render(key, punishment, revoked))
    }

    private fun staff(context: CommandContext) = Staff(context.actor.uuid, context.actor.name)

    private fun findProfile(context: CommandContext): IProfile {
        val name = context.string(0, "player")

        return service.getProfile(name) ?: throw CommandFailure("$name has never joined this server.")
    }

    private fun durationAndReason(context: CommandContext, required: Boolean): Pair<Duration?, String> {
        val first = context.arg(1)?.lowercase()

        if (first == "perm" || first == "permanent") {
            return null to reason(context, 2)
        }

        val duration = first?.let(::parseDuration)

        if (duration == null) {
            if (required) {
                throw CommandFailure("Give a duration such as 30m, 7d or 1d12h.")
            }

            return null to reason(context, 1)
        }

        return duration to reason(context, 2)
    }

    private fun reason(context: CommandContext, from: Int): String {
        return context.rest(from).ifBlank { messages.defaultReason }
    }

    private fun complete(context: CompletionContext, withDuration: Boolean): List<String> {
        return when {
            context.index == 0 -> context.players()
            context.index == 1 && withDuration -> DURATIONS
            else -> emptyList()
        }
    }

    private fun usage(target: String, lasting: Boolean, temporary: Boolean): String = when {
        temporary -> "$target <duration> [reason]"
        lasting -> "$target [duration] [reason]"
        else -> "$target [reason]"
    }

    companion object {

        private val DURATIONS = listOf("30m", "1h", "1d", "7d", "30d", "permanent")

    }

}
