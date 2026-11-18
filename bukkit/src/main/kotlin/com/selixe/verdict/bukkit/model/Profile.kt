package com.selixe.verdict.bukkit.model

import com.selixe.conduit.Identifier
import com.selixe.verdict.api.profile.IProfile
import java.time.Instant
import java.util.UUID

data class Profile(
    @Identifier override val uuid: UUID,
    override var name: String,
    override var lastIpHash: String,
    override val ipHashes: MutableList<String> = mutableListOf(),
    override var lastSeen: Instant = Instant.now()
) : IProfile
