package com.selixe.verdict.api

import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import java.util.UUID
import kotlin.time.Duration

interface VerdictAPI {

    fun getProfile(uuid: UUID): IProfile?
    fun getProfile(name: String): IProfile?

    fun getActiveBan(uuid: UUID): IPunishment?
    fun getActiveMute(uuid: UUID): IPunishment?

    fun getHistory(uuid: UUID): List<IPunishment>
    fun getAlts(uuid: UUID): List<IProfile>

    fun punish(type: PunishmentType, target: UUID, reason: String, duration: Duration? = null): IPunishment
    fun revoke(type: PunishmentType, target: UUID): IPunishment

}
