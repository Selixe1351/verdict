package com.selixe.verdict.bukkit.model

import java.util.UUID

data class Staff(val uuid: UUID?, val name: String) {

    companion object {

        val CONSOLE = Staff(null, "Console")

    }

}
