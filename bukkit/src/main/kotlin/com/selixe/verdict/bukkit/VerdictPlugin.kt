package com.selixe.verdict.bukkit

import com.selixe.conduit.Conduit
import com.selixe.directive.paper.PaperCommands
import com.selixe.verdict.api.VerdictAPI
import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.bukkit.command.ShortcutCommand
import com.selixe.verdict.bukkit.command.VerdictCommand
import com.selixe.verdict.bukkit.listener.ChatListener
import com.selixe.verdict.bukkit.listener.LoginListener
import com.selixe.verdict.bukkit.message.Messages
import com.selixe.verdict.bukkit.model.Profile
import com.selixe.verdict.bukkit.model.Punishment
import com.selixe.verdict.bukkit.sync.PunishmentEvent
import com.selixe.verdict.bukkit.sync.PunishmentHandler
import com.selixe.verdict.bukkit.util.IpHasher
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.security.SecureRandom
import java.util.Base64

class VerdictPlugin : JavaPlugin() {

    private lateinit var conduit: Conduit
    private lateinit var service: VerdictService
    private lateinit var commands: PaperCommands

    override fun onEnable() {
        instance = this

        saveDefaultConfig()

        val mongoUri = config.getString("mongo.uri") ?: error("Mongo URI is not set")
        val mongoDatabase = config.getString("mongo.database") ?: error("Mongo database is not set")
        val redisUri = config.getString("redis.uri")?.ifBlank { null }

        conduit = Conduit(mongoUri, mongoDatabase, redisUri)

        if (redisUri == null) {
            logger.info("Redis is not configured. Punishments will not sync across servers.")
        }

        val hasher = IpHasher(ipSecret())
        val messages = Messages(config.getConfigurationSection("messages") ?: error("Messages are not set"))
        val handler = PunishmentHandler(messages, hasher)

        val handle: (PunishmentEvent) -> Unit = { event ->
            server.scheduler.runTask(this, Runnable { handler.handle(event) })
        }

        service = VerdictService(
            conduit.store<IPunishment, Punishment>("punishments"),
            conduit.store<IProfile, Profile>("profiles"),
            hasher,
            { event -> if (conduit.redis != null) conduit.publish(CHANNEL, event) else handle(event) }
        )

        if (conduit.redis != null) {
            conduit.subscribe<PunishmentEvent>(CHANNEL, handle)
        }

        server.servicesManager.register(VerdictAPI::class.java, service, this, ServicePriority.Normal)

        val mutedCommands = config.getStringList("muted-commands").map { it.lowercase() }.toSet()

        server.pluginManager.registerEvents(LoginListener(service, messages, logger), this)
        server.pluginManager.registerEvents(ChatListener(service, messages, mutedCommands), this)

        val verdictCommand = VerdictCommand(service, messages)

        commands = PaperCommands(this)
        commands.register(verdictCommand.create())

        if (config.getBoolean("shortcuts", true)) {
            ShortcutCommand(verdictCommand).create().forEach(commands::register)
        }
    }

    private fun ipSecret(): String {
        val secret = config.getString("ip-secret")

        if (!secret.isNullOrBlank()) {
            return secret
        }

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)

        val generated = Base64.getEncoder().encodeToString(bytes)

        config.set("ip-secret", generated)
        saveConfig()

        logger.warning("Generated a new ip-secret in config.yml. Copy it to every server on your network.")

        return generated
    }

    override fun onDisable() {
        instance = null

        if (::commands.isInitialized) {
            commands.close()
        }

        if (::conduit.isInitialized) {
            conduit.close()
        }
    }

    companion object {

        private const val CHANNEL = "verdict:punishments"

        private var instance: VerdictPlugin? = null

        fun api(): VerdictAPI = instance?.service ?: error("VerdictPlugin is not initialized")

    }

}
