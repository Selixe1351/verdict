package com.selixe.verdict.bukkit

import com.selixe.conduit.Conduit
import com.selixe.directive.CommandFailure
import com.selixe.verdict.api.profile.IProfile
import com.selixe.verdict.api.punishment.IPunishment
import com.selixe.verdict.api.punishment.PunishmentType
import com.selixe.verdict.bukkit.model.Profile
import com.selixe.verdict.bukkit.model.Punishment
import com.selixe.verdict.bukkit.model.Staff
import com.selixe.verdict.bukkit.sync.PunishmentEvent
import com.selixe.verdict.bukkit.util.IpHasher
import org.bson.Document
import kotlin.test.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@Testcontainers(disabledWithoutDocker = true)
class VerdictServiceTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7")
    }

    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
        override fun instant() = now
    }

    private val events = mutableListOf<PunishmentEvent>()
    private val staff = Staff(null, "Console")
    private val hasher = IpHasher("test-secret-0123456789")
    private lateinit var conduit: Conduit
    private lateinit var service: VerdictService

    @BeforeEach
    fun connect() {
        conduit = Conduit(mongo.connectionString, "verdict-${UUID.randomUUID()}")
        service = VerdictService(conduit.store<IPunishment, Punishment>("punishments"), conduit.store<IProfile, Profile>("profiles"), hasher, events::add, clock)
    }

    @AfterEach
    fun close() {
        conduit.close()
    }

    private fun join(name: String, ip: String = "1.1.1.1", uuid: UUID = UUID.randomUUID()): IProfile {
        service.login(uuid, name, ip)
        return service.getProfile(uuid)!!
    }

    @Test
    fun `login records the player and finds them by name in any case`() {
        val steve = join("Steve")

        assertEquals(steve.uuid, service.getProfile("sTeVe")?.uuid)
        assertEquals(listOf(hasher.hash("1.1.1.1")), steve.ipHashes)
    }

    @Test
    fun `login keeps every ip and the newest name`() {
        val uuid = UUID.randomUUID()
        service.login(uuid, "Steve", "1.1.1.1")
        service.login(uuid, "Steve2", "2.2.2.2")
        service.login(uuid, "Steve2", "1.1.1.1")

        val record = service.getProfile(uuid)!!
        assertEquals("Steve2", record.name)
        assertEquals(listOf(hasher.hash("1.1.1.1"), hasher.hash("2.2.2.2")), record.ipHashes)
        assertNull(service.getProfile("Steve"))
    }

    @Test
    fun `ban blocks login until unbanned`() {
        val steve = join("Steve")

        service.punish(PunishmentType.BAN, steve, "Cheating", staff)
        assertNotNull(service.login(steve.uuid, "Steve", "1.1.1.1"))

        service.unban(steve, staff)
        assertNull(service.login(steve.uuid, "Steve", "1.1.1.1"))
        assertEquals(listOf(false, true), events.map { it.revoked })
    }

    @Test
    fun `temporary ban expires`() {
        val steve = join("Steve")
        service.punish(PunishmentType.BAN, steve, "Cheating", staff, 7.days)

        now = now.plusSeconds(6 * 86_400)
        assertNotNull(service.getActiveBan(steve.uuid))

        now = now.plusSeconds(2 * 86_400)
        assertNull(service.getActiveBan(steve.uuid))
    }

    @Test
    fun `banning twice is refused`() {
        val steve = join("Steve")
        service.punish(PunishmentType.BAN, steve, "Cheating", staff)

        val failure = assertThrows<CommandFailure> { service.punish(PunishmentType.BAN, steve, "Again", staff) }

        assertEquals("Steve is already banned.", failure.message)
    }

    @Test
    fun `unbanning a player who is not banned is refused`() {
        val steve = join("Steve")

        assertThrows<CommandFailure> { service.unban(steve, staff) }
    }

    @Test
    fun `ip ban blocks every account on that ip`() {
        val steve = join("Steve", ip = "5.5.5.5")
        val alt = join("Alt", ip = "5.5.5.5")
        val other = join("Other", ip = "6.6.6.6")

        service.banIp(steve, "Ban evasion", staff)

        assertNotNull(service.login(alt.uuid, "Alt", "5.5.5.5"))
        assertNull(service.login(other.uuid, "Other", "6.6.6.6"))

        service.unbanIp(hasher.hash("5.5.5.5"), staff)
        assertNull(service.login(alt.uuid, "Alt", "5.5.5.5"))
    }

    @Test
    fun `ip addresses are never stored in plain text`() {
        val steve = join("Steve", ip = "203.0.113.7")
        service.banIp("198.51.100.9", "Proxy", staff)
        service.banIp(steve, "Evasion", staff)

        val database = conduit.mongo.database
        val stored = listOf("profiles", "punishments").flatMap { name ->
            database.getCollection(name).find().map(Document::toJson).toList()
        }.joinToString()

        assertFalse("203.0.113.7" in stored)
        assertFalse("198.51.100.9" in stored)
        assertTrue("198.51.*.*" in stored)
    }

    @Test
    fun `banning a typed ip blocks a player who logs in from it`() {
        service.banIp("7.7.7.7", "Proxy", staff)

        val ban = service.login(UUID.randomUUID(), "Fresh", "7.7.7.7")

        assertEquals("7.7.*.*", ban?.ipMask)
    }

    @Test
    fun `mute is active until it is revoked`() {
        val steve = join("Steve")
        service.punish(PunishmentType.MUTE, steve, "Spam", staff, 1.days)

        assertNotNull(service.getActiveMute(steve.uuid))
        assertNull(service.getActiveBan(steve.uuid))

        service.unmute(steve, staff)
        assertNull(service.getActiveMute(steve.uuid))
    }

    @Test
    fun `kicks and warnings are recorded but never active`() {
        val steve = join("Steve")
        service.punish(PunishmentType.WARN, steve, "Language", staff)
        service.punish(PunishmentType.KICK, steve, "AFK", staff, 1.days)

        val history = service.getHistory(steve.uuid)
        assertEquals(2, history.size)
        assertTrue(history.none { it.isActive(now) })
        assertTrue(history.all { it.expiresAt == null })
    }

    @Test
    fun `history is newest first and includes revoked punishments`() {
        val steve = join("Steve")
        service.punish(PunishmentType.WARN, steve, "First", staff)
        now = now.plusSeconds(60)
        service.punish(PunishmentType.BAN, steve, "Second", staff)
        now = now.plusSeconds(60)
        service.unban(steve, staff)

        val history = service.getHistory(steve.uuid)
        assertEquals(listOf("Second", "First"), history.map { it.reason })
        assertEquals("Console", history.first().revokedBy)
    }

    @Test
    fun `alts are players that shared an ip`() {
        val steve = join("Steve", ip = "1.1.1.1")
        service.login(steve.uuid, "Steve", "2.2.2.2")
        join("Alex", ip = "2.2.2.2")
        join("Bob", ip = "1.1.1.1")
        join("Stranger", ip = "9.9.9.9")

        assertEquals(listOf("Alex", "Bob"), service.getAlts(steve.uuid).map { it.name })
    }

    @Test
    fun `banned alts are only the alts with an active ban`() {
        val steve = join("Steve", ip = "1.1.1.1")
        val alex = join("Alex", ip = "1.1.1.1")
        join("Bob", ip = "1.1.1.1")

        service.punish(PunishmentType.BAN, alex, "Cheating", staff)

        assertEquals(listOf("Alex"), service.getBannedAlts(steve.uuid).map { it.name })
    }

}
