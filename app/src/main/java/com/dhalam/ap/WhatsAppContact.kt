package com.dhalam.ap

import java.net.URI

/** Public WhatsApp contact only. Never accepts a Meta token, phone ID URL or arbitrary host. */
internal fun whatsappContactNumber(raw: String): String? {
    val value = raw.trim().removeSurrounding("\"").removeSurrounding("'")
    val number = if (value.startsWith("https://", ignoreCase = true)) {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.host.equals("wa.me", true) || uri.rawUserInfo != null || uri.port != -1 ||
            uri.rawFragment != null || uri.rawQuery != null) return null
        uri.rawPath.removePrefix("/")
    } else value.replace(Regex("[\\s()+-]"), "")
    return number.takeIf { it.matches(Regex("[1-9][0-9]{6,14}")) }
}

internal fun whatsappBotChatUrl(number: String): String =
    "https://wa.me/" + requireNotNull(whatsappContactNumber(number)) + "?text=CHECK%3A%20"
