package com.selixe.verdict.bukkit.sync

import com.selixe.verdict.bukkit.model.Punishment

data class PunishmentEvent(val punishment: Punishment, val revoked: Boolean)
