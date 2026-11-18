package com.selixe.verdict.bukkit.message

import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.bukkit.util.formatDuration
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration
import java.time.Instant

class Messages(private val section: ConfigurationSection) {

    private val miniMessage = MiniMessage.miniMessage()

    val prefix: String = section.getString("prefix") ?: ""

    val defaultReason: String = section.getString("default-reason") ?: "No reason given"

    fun render(key: String, punishment: IPunishment, revoked: Boolean = false): Component {
        val template = section.getString(key) ?: error("Message '$key' is missing from config.yml")

        return miniMessage.deserialize(
            template,
            Placeholder.parsed("prefix", prefix),
            Placeholder.unparsed("player", punishment.targetName ?: punishment.ipMask ?: "?"),
            Placeholder.unparsed("staff", if (revoked) punishment.revokedBy ?: "?" else punishment.issuerName),
            Placeholder.unparsed("reason", punishment.reason),
            Placeholder.unparsed("type", if (revoked) punishment.type.undoVerb else punishment.type.verb),
            Placeholder.unparsed("duration", duration(punishment)),
            Placeholder.unparsed("expires", expires(punishment))
        )
    }

    private fun duration(punishment: IPunishment): String {
        val expiresAt = punishment.expiresAt

        if (expiresAt == null) {
            return if (punishment.type.lasting) "permanently" else ""
        }

        return "for " + formatDuration(Duration.between(punishment.createdAt, expiresAt))
    }

    private fun expires(punishment: IPunishment): String {
        val expiresAt = punishment.expiresAt ?: return section.getString("permanent") ?: "never"

        return "in " + formatDuration(Duration.between(Instant.now(), expiresAt))
    }

}
