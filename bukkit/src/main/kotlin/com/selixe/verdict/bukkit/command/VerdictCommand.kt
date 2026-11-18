package com.selixe.verdict.bukkit.command

import com.selixe.directive.CommandContext
import com.selixe.directive.CommandFailure
import com.selixe.directive.command
import com.selixe.directive.intOrNull
import com.selixe.directive.paper.sender
import com.selixe.directive.parseDuration
import com.selixe.directive.string
import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.VerdictService
import com.selixe.verdict.bukkit.message.Messages
import com.selixe.verdict.bukkit.model.Staff
import com.selixe.verdict.bukkit.util.formatDuration
import com.selixe.verdict.bukkit.util.isIp
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration as KotlinDuration

class VerdictCommand(
    private val service: VerdictService,
    private val messages: Messages
) {

    private val miniMessage = MiniMessage.miniMessage()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    fun create() = command("verdict", aliases = listOf("vd"), description = "Punish players") {
        sub(
            "user",
            usage = "<player> <ban|ipban|mute|kick|warn|unban|unbanip|unmute|history|alts> [duration] [reason]",
            description = "Punish or look up a player",
            async = true
        ) {
            completes { context ->
                when (context.index) {
                    0 -> context.players()
                    1 -> ACTIONS.keys.toList()
                    2 -> if (context.arg(1)?.lowercase() in listOf("ban", "ipban", "mute")) DURATIONS else emptyList()
                    else -> emptyList()
                }
            }

            executes { context ->
                val action = context.string(1, "action").lowercase()
                val permission = ACTIONS[action] ?: throw CommandFailure("Unknown action '$action'.")

                if (!context.actor.isConsole && !context.actor.hasPermission(permission)) {
                    throw CommandFailure("You don't have permission.")
                }

                user(context, action, 2)
            }
        }

        sub(
            "ip",
            usage = "<address|player> <ban|unban> [duration] [reason]",
            permission = "verdict.ipban",
            description = "Ban or unban an IP address",
            async = true
        ) {
            completes { context ->
                when (context.index) {
                    0 -> context.players()
                    1 -> listOf("ban", "unban")
                    2 -> if (context.arg(1).equals("ban", true)) DURATIONS else emptyList()
                    else -> emptyList()
                }
            }

            executes { context ->
                when (val action = context.string(1, "action").lowercase()) {
                    "ban" -> ip(context, true, 2)
                    "unban" -> ip(context, false, 2)
                    else -> throw CommandFailure("Unknown action '$action'.")
                }
            }
        }
    }

    fun user(context: CommandContext, action: String, from: Int) {
        val target = findProfile(context)
        val staff = Staff(context.actor.uuid, context.actor.name)

        when (action) {
            "ban", "mute" -> {
                val type = if (action == "ban") PunishmentType.BAN else PunishmentType.MUTE
                val (duration, reason) = durationAndReason(context, from)

                notify(context, service.punish(type, target, reason, staff, duration))
            }

            "kick", "warn" -> {
                if (action == "kick" && Bukkit.getPlayer(target.uuid) == null) {
                    throw CommandFailure("${target.name} is not online.")
                }

                val type = if (action == "kick") PunishmentType.KICK else PunishmentType.WARN

                notify(context, service.punish(type, target, reason(context, from), staff))
            }

            "ipban" -> ip(context, true, from)
            "unban" -> notify(context, service.unban(target, staff), true)
            "unbanip" -> ip(context, false, from)
            "unmute" -> notify(context, service.unmute(target, staff), true)
            "history" -> history(context, target, context.intOrNull(from) ?: 1)
            "alts" -> alts(context, target)
        }
    }

    fun ip(context: CommandContext, ban: Boolean, from: Int) {
        val input = context.string(0, "address or player")
        val staff = Staff(context.actor.uuid, context.actor.name)

        val profile = if (isIp(input)) {
            null
        } else {
            service.getProfile(input) ?: throw CommandFailure("$input has never joined.")
        }

        if (!ban) {
            val ipHash = profile?.lastIpHash ?: service.hashIp(input)

            notify(context, service.unbanIp(ipHash, staff), true)
            return
        }

        val (duration, reason) = durationAndReason(context, from)

        val punishment = if (profile != null) {
            service.banIp(profile, reason, staff, duration)
        } else {
            service.banIp(input, reason, staff, duration)
        }

        notify(context, punishment)
    }

    private fun history(context: CommandContext, target: IProfile, page: Int) {
        val history = service.getHistory(target.uuid)

        if (history.isEmpty()) {
            context.reply("${messages.prefix}<white>${escape(target.name)} <gray>has no punishments.")
            return
        }

        val pages = (history.size + PAGE_SIZE - 1) / PAGE_SIZE
        val current = page.coerceIn(1, pages)
        val now = Instant.now()

        context.reply("${messages.prefix}<gray>History of <white>${escape(target.name)} <dark_gray>($current/$pages)")

        history.drop((current - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { punishment ->
            val length = punishment.expiresAt?.let { formatDuration(Duration.between(punishment.createdAt, it)) }
                ?: if (punishment.type.lasting) "permanent" else ""

            val state = when {
                punishment.revokedAt != null -> "<gray>revoked by ${escape(punishment.revokedBy ?: "?")}"
                punishment.isActive(now) -> "<green>active"
                punishment.type.lasting -> "<gray>expired"
                else -> ""
            }

            context.reply(
                "<dark_gray>${dateFormat.format(punishment.createdAt)} <white>${punishment.type.name} <gray>$length " +
                    "by ${escape(punishment.issuerName)}: <white>${escape(punishment.reason)} $state"
            )
        }
    }

    private fun alts(context: CommandContext, target: IProfile) {
        val alts = service.getAlts(target.uuid)

        if (alts.isEmpty()) {
            context.reply("${messages.prefix}<white>${escape(target.name)} <gray>has no known alts.")
            return
        }

        val names = alts.joinToString("<gray>, ") {
            val color = if (service.getActiveBan(it.uuid) != null) "<red>" else "<white>"
            color + escape(it.name)
        }

        context.reply("${messages.prefix}<gray>Alts of <white>${escape(target.name)}<gray>: $names")
    }

    private fun notify(context: CommandContext, punishment: IPunishment, revoked: Boolean = false) {
        if (context.actor.isConsole || context.actor.hasPermission("verdict.notify")) {
            return
        }

        val key = if (revoked) "notify-revoked" else "notify-issued"

        context.sender.sendMessage(messages.render(key, punishment, revoked))
    }

    private fun findProfile(context: CommandContext): IProfile {
        val name = context.string(0, "player")

        return service.getProfile(name) ?: throw CommandFailure("$name has never joined this server.")
    }

    private fun durationAndReason(context: CommandContext, from: Int): Pair<KotlinDuration?, String> {
        val first = context.arg(from)?.lowercase()

        if (first == "perm" || first == "permanent") {
            return null to reason(context, from + 1)
        }

        val duration = first?.let(::parseDuration) ?: return null to reason(context, from)

        return duration to reason(context, from + 1)
    }

    private fun reason(context: CommandContext, from: Int): String {
        return context.rest(from).ifBlank { messages.defaultReason }
    }

    private fun escape(text: String): String {
        return miniMessage.escapeTags(text)
    }

    companion object {

        private const val PAGE_SIZE = 10

        private val DURATIONS = listOf("30m", "1h", "1d", "7d", "30d", "permanent")

        private val ACTIONS = linkedMapOf(
            "ban" to "verdict.ban",
            "ipban" to "verdict.ipban",
            "mute" to "verdict.mute",
            "kick" to "verdict.kick",
            "warn" to "verdict.warn",
            "unban" to "verdict.unban",
            "unbanip" to "verdict.ipban",
            "unmute" to "verdict.unmute",
            "history" to "verdict.history",
            "alts" to "verdict.alts"
        )

    }

}
