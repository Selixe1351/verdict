package com.selixe.verdict.bukkit.command

import com.selixe.directive.Command
import com.selixe.directive.CommandFailure
import com.selixe.directive.command
import com.selixe.directive.parseDuration

class ShortcutCommand(private val verdict: VerdictCommand) {

    fun create(): List<Command> = listOf(
        user("ban", "verdict.ban", "<player> [duration] [reason]"),
        user("tempban", "verdict.ban", "<player> <duration> [reason]", "ban", true),
        user("mute", "verdict.mute", "<player> [duration] [reason]"),
        user("tempmute", "verdict.mute", "<player> <duration> [reason]", "mute", true),
        user("kick", "verdict.kick", "<player> [reason]"),
        user("warn", "verdict.warn", "<player> [reason]"),
        user("unban", "verdict.unban", "<player>"),
        user("unmute", "verdict.unmute", "<player>"),
        user("history", "verdict.history", "<player> [page]"),
        user("alts", "verdict.alts", "<player>"),
        ip("banip", "<address|player> [duration] [reason]", true),
        ip("unbanip", "<address|player>", false)
    )

    private fun user(
        name: String,
        permission: String,
        usage: String,
        action: String = name,
        durationRequired: Boolean = false
    ) = command(name, permission = permission, usage = usage, async = true) {
        executes { context ->
            if (durationRequired && context.arg(1)?.let(::parseDuration) == null) {
                throw CommandFailure("Give a duration such as 30m, 7d or 1d12h.")
            }

            verdict.user(context, action, 1)
        }
    }

    private fun ip(name: String, usage: String, ban: Boolean) =
        command(name, permission = "verdict.ipban", usage = usage, async = true) {
            executes { context -> verdict.ip(context, ban, 1) }
        }

}
