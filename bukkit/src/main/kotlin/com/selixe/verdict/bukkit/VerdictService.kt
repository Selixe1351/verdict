package com.selixe.verdict.bukkit

import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.selixe.conduit.Store
import com.selixe.directive.CommandFailure
import com.selixe.verdict.api.VerdictAPI
import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.model.Profile
import com.selixe.verdict.bukkit.model.Punishment
import com.selixe.verdict.bukkit.model.Staff
import com.selixe.verdict.bukkit.sync.PunishmentEvent
import com.selixe.verdict.bukkit.util.IpHasher
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class VerdictService(
    private val punishments: Store<IPunishment, Punishment>,
    private val profiles: Store<IProfile, Profile>,
    private val hasher: IpHasher,
    private val broadcast: (PunishmentEvent) -> Unit,
    private val clock: Clock = Clock.systemUTC()
) : VerdictAPI {

    private val ignoreCase = Collation.builder()
        .locale("en")
        .collationStrength(CollationStrength.SECONDARY)
        .build()

    init {
        profiles.collection.createIndex(Indexes.ascending("name"), IndexOptions().collation(ignoreCase))
    }

    override fun getProfile(uuid: UUID): IProfile? {
        return profiles.get(uuid)
    }

    override fun getProfile(name: String): IProfile? {
        return profiles.collection.find(Filters.eq("name", name)).collation(ignoreCase).first()
    }

    override fun getActiveBan(uuid: UUID): IPunishment? {
        return active(PunishmentType.BAN, punishments.find(IPunishment::target, uuid))
    }

    override fun getActiveMute(uuid: UUID): IPunishment? {
        return active(PunishmentType.MUTE, punishments.find(IPunishment::target, uuid))
    }

    fun getActiveIpBan(ipHash: String): IPunishment? {
        return active(PunishmentType.BAN, punishments.find(IPunishment::ipHash, ipHash))
    }

    override fun getHistory(uuid: UUID): List<IPunishment> {
        return punishments.find(IPunishment::target, uuid).sortedByDescending { it.createdAt }
    }

    override fun getAlts(uuid: UUID): List<IProfile> {
        val profile = profiles.get(uuid) ?: return emptyList()

        return profile.ipHashes
            .flatMap { profiles.find("ipHashes", it) }
            .filter { it.uuid != uuid }
            .distinctBy { it.uuid }
            .sortedBy { it.name.lowercase() }
    }

    override fun punish(type: PunishmentType, target: UUID, reason: String, duration: Duration?): IPunishment {
        val profile = getProfile(target) ?: error("Unknown player: $target")

        return punish(type, profile, reason, Staff.CONSOLE, duration)
    }

    override fun revoke(type: PunishmentType, target: UUID): IPunishment {
        val profile = getProfile(target) ?: error("Unknown player: $target")

        return when (type) {
            PunishmentType.BAN -> unban(profile, Staff.CONSOLE)
            PunishmentType.MUTE -> unmute(profile, Staff.CONSOLE)
            else -> error("Only bans and mutes can be revoked")
        }
    }

    fun login(uuid: UUID, name: String, ip: String): IPunishment? {
        val now = clock.instant()
        val ipHash = hasher.hash(ip)

        val updated = profiles.update(uuid) {
            it.name = name
            it.lastIpHash = ipHash
            it.lastSeen = now

            if (ipHash !in it.ipHashes) {
                it.ipHashes += ipHash
            }
        }

        if (updated == null) {
            profiles.save(Profile(uuid, name, ipHash, mutableListOf(ipHash), now))
        }

        return getActiveBan(uuid) ?: getActiveIpBan(ipHash)
    }

    fun punish(type: PunishmentType, target: IProfile, reason: String, staff: Staff, duration: Duration? = null): IPunishment {
        if (type == PunishmentType.BAN && getActiveBan(target.uuid) != null) {
            throw CommandFailure("${target.name} is already banned.")
        }

        if (type == PunishmentType.MUTE && getActiveMute(target.uuid) != null) {
            throw CommandFailure("${target.name} is already muted.")
        }

        return issue(type, target, null, null, reason, staff, duration)
    }

    fun banIp(ip: String, reason: String, staff: Staff, duration: Duration? = null): IPunishment {
        val ipHash = hasher.hash(ip)

        if (getActiveIpBan(ipHash) != null) {
            throw CommandFailure("That IP is already banned.")
        }

        return issue(PunishmentType.BAN, null, ipHash, hasher.mask(ip), reason, staff, duration)
    }

    fun banIp(target: IProfile, reason: String, staff: Staff, duration: Duration? = null): IPunishment {
        if (getActiveIpBan(target.lastIpHash) != null) {
            throw CommandFailure("${target.name}'s IP is already banned.")
        }

        return issue(PunishmentType.BAN, target, target.lastIpHash, null, reason, staff, duration)
    }

    fun unban(target: IProfile, staff: Staff): IPunishment {
        val ban = getActiveBan(target.uuid) ?: throw CommandFailure("${target.name} is not banned.")

        return revoke(ban, staff)
    }

    fun unbanIp(ipHash: String, staff: Staff): IPunishment {
        val ban = getActiveIpBan(ipHash) ?: throw CommandFailure("That IP is not banned.")

        return revoke(ban, staff)
    }

    fun hashIp(ip: String): String {
        return hasher.hash(ip)
    }

    fun unmute(target: IProfile, staff: Staff): IPunishment {
        val mute = getActiveMute(target.uuid) ?: throw CommandFailure("${target.name} is not muted.")

        return revoke(mute, staff)
    }

    private fun issue(
        type: PunishmentType,
        target: IProfile?,
        ipHash: String?,
        ipMask: String?,
        reason: String,
        staff: Staff,
        duration: Duration?
    ): IPunishment {
        val now = clock.instant()
        val expiresAt = if (type.lasting) duration?.let { now.plus(it.toJavaDuration()) } else null

        val punishment = Punishment(
            id = UUID.randomUUID(),
            type = type,
            target = target?.uuid,
            targetName = target?.name,
            ipHash = ipHash,
            ipMask = ipMask,
            reason = reason,
            issuer = staff.uuid,
            issuerName = staff.name,
            createdAt = now,
            expiresAt = expiresAt
        )

        punishments.insert(punishment)
        broadcast(PunishmentEvent(punishment, revoked = false))

        return punishment
    }

    private fun revoke(punishment: IPunishment, staff: Staff): IPunishment {
        val now = clock.instant()

        val revoked = punishments.update(punishment.id) {
            it.revokedAt = now
            it.revokedBy = staff.name
        } ?: throw CommandFailure("That punishment no longer exists.")

        broadcast(PunishmentEvent(revoked as Punishment, revoked = true))

        return revoked
    }

    private fun active(type: PunishmentType, punishments: List<IPunishment>): IPunishment? {
        val now = clock.instant()

        return punishments.firstOrNull { it.type == type && it.isActive(now) }
    }

}
