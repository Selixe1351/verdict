package com.selixe.verdict.api.profile

import java.time.Instant
import java.util.UUID

interface IProfile {

    val uuid: UUID
    val name: String

    val lastIpHash: String
    val ipHashes: List<String>

    val lastSeen: Instant

}
