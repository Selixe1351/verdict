package com.selixe.verdict.bukkit.util

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class IpHasher(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    init {
        require(secret.length >= 16) { "ip-secret must be at least 16 characters" }
    }

    fun hash(ip: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)

        val digest = mac.doFinal(ip.trim().lowercase().toByteArray())

        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun mask(ip: String): String {
        if (':' in ip) {
            return ip.split(':').take(2).joinToString(":") + ":*"
        }

        return ip.split('.').take(2).joinToString(".") + ".*.*"
    }

}
