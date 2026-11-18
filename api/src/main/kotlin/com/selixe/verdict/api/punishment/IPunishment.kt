package com.selixe.verdict.api.punishment

import java.time.Instant
import java.util.UUID

interface IPunishment {

    val id: UUID
    val type: PunishmentType

    val target: UUID?
    val targetName: String?
    val ipHash: String?
    val ipMask: String?

    val reason: String
    val issuer: UUID?
    val issuerName: String

    val createdAt: Instant
    val expiresAt: Instant?

    val revokedAt: Instant?
    val revokedBy: String?

    fun isActive(now: Instant = Instant.now()): Boolean {
        return type.lasting && revokedAt == null && (expiresAt == null || now.isBefore(expiresAt))
    }

}
