package com.selixe.verdict.bukkit.menu

import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.util.formatDuration
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryMenu(private val plugin: Plugin) : Listener {

    private val miniMessage = MiniMessage.miniMessage()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private class Holder(val name: String, val history: List<IPunishment>) : InventoryHolder {

        var page = 0

        lateinit var menu: Inventory

        override fun getInventory(): Inventory = menu

    }

    fun open(viewer: Player, name: String, history: List<IPunishment>) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val holder = Holder(name, history)
            val title = miniMessage.deserialize("<dark_gray>History <gray>- <white>${escape(name)}")

            holder.menu = Bukkit.createInventory(holder, SIZE, title)

            render(holder)
            viewer.openInventory(holder.menu)
        })
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? Holder ?: return

        event.isCancelled = true

        if (event.clickedInventory != event.inventory) {
            return
        }

        when (event.rawSlot) {
            PREVIOUS -> if (holder.page > 0) turn(holder, -1)
            NEXT -> if (holder.page < lastPage(holder)) turn(holder, 1)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is Holder) {
            event.isCancelled = true
        }
    }

    private fun turn(holder: Holder, step: Int) {
        holder.page += step

        render(holder)
    }

    private fun lastPage(holder: Holder): Int = (holder.history.size - 1) / PAGE_SIZE

    private fun render(holder: Holder) {
        val inventory = holder.menu
        val now = Instant.now()

        inventory.clear()

        holder.history.drop(holder.page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { slot, punishment ->
            inventory.setItem(slot, entry(punishment, now))
        }

        if (holder.page > 0) {
            inventory.setItem(PREVIOUS, button("<white>Previous page"))
        }

        if (holder.page < lastPage(holder)) {
            inventory.setItem(NEXT, button("<white>Next page"))
        }

        inventory.setItem(
            INFO,
            button("<gray>Page <white>${holder.page + 1}<gray>/<white>${lastPage(holder) + 1}", Material.PAPER)
        )
    }

    private fun entry(punishment: IPunishment, now: Instant): ItemStack {
        val (material, color) = when (punishment.type) {
            PunishmentType.BAN -> Material.BARRIER to "red"
            PunishmentType.MUTE -> Material.NAME_TAG to "gold"
            PunishmentType.KICK -> Material.LEATHER_BOOTS to "yellow"
            PunishmentType.WARN -> Material.OAK_SIGN to "aqua"
        }

        val title = punishment.type.name + if (punishment.ipHash != null) " (IP)" else ""

        val lines = mutableListOf(
            "<gray>Reason: <white>${escape(punishment.reason)}",
            "<gray>By: <white>${escape(punishment.issuerName)}",
            "<gray>Date: <white>${dateFormat.format(punishment.createdAt)}"
        )

        if (punishment.type.lasting) {
            val length = punishment.expiresAt?.let { formatDuration(Duration.between(punishment.createdAt, it)) }

            lines += "<gray>Length: <white>${length ?: "permanent"}"
        }

        when {
            punishment.revokedAt != null -> lines += "<gray>Revoked by <white>${escape(punishment.revokedBy ?: "?")}"
            punishment.isActive(now) -> lines += "<green>Active"
            punishment.type.lasting -> lines += "<gray>Expired"
        }

        return item(material, "<$color><bold>$title", lines)
    }

    private fun button(name: String, material: Material = Material.ARROW): ItemStack {
        return item(material, name, emptyList())
    }

    private fun item(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)

        item.editMeta { meta ->
            meta.displayName(line(name))
            meta.lore(lore.map(::line))
        }

        return item
    }

    private fun line(text: String): Component = miniMessage.deserialize("<!italic>$text")

    private fun escape(text: String): String = miniMessage.escapeTags(text)

    companion object {

        private const val SIZE = 54
        private const val PAGE_SIZE = 45
        private const val PREVIOUS = 48
        private const val INFO = 49
        private const val NEXT = 50

    }

}
