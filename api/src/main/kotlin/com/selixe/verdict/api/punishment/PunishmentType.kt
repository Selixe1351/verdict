package com.selixe.verdict.api.punishment

enum class PunishmentType(val verb: String, val undoVerb: String) {

    BAN("banned", "unbanned"),
    MUTE("muted", "unmuted"),
    KICK("kicked", "kicked"),
    WARN("warned", "warned");

    val lasting: Boolean
        get() = this == BAN || this == MUTE

}
