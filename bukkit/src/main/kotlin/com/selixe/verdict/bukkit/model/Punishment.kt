package com.selixe.verdict.bukkit.model

import com.selixe.conduit.Identifier
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import java.time.Instant
import java.util.UUID

data class Punishment(
    @Identifier override val id: UUID,
    override val type: PunishmentType,
    override val target: UUID?,
    override val targetName: String?,
    override val ipHash: String? = null,
    override val ipMask: String? = null,
    override val reason: String,
    override val issuer: UUID?,
    override val issuerName: String,
    override val createdAt: Instant,
    override val expiresAt: Instant? = null,
    override var revokedAt: Instant? = null,
    override var revokedBy: String? = null
) : IPunishment
