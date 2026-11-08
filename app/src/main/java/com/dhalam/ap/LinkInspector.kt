package com.dhalam.ap

import java.net.URI
import java.net.IDN
import java.util.Locale

data class LinkInspection(val host: String, val notes: List<String>, val scheme: String = "", val path: String = "")

/** Local structural inspection only. Never resolves, fetches or launches the URL. */
fun inspectSecurityLink(input: String): LinkInspection {
    val text = input.trim()
    if (text.isEmpty() || text.length > 4096 || text.any { it.isWhitespace() || it.code < 32 || it.code == 127 || it == '\\' })
        return LinkInspection("", listOf("Enter one URL up to 4096 characters without spaces, control characters or backslashes."))
    try {
        val uri = URI(if (text.contains("://")) text else "https://$text")
        require(uri.scheme.lowercase(Locale.ROOT) in listOf("http", "https"))
        val authority = requireNotNull(uri.rawAuthority)
        // URI.host may be null for Unicode DNS names. Parse only an unambiguous authority.
        val rawHost = authority.substringAfterLast('@')
        val host = if (rawHost.startsWith("[")) {
            require(rawHost.contains(']')); rawHost.substringBefore(']') + "]"
        } else rawHost.substringBefore(':')
        val portSuffix = if (rawHost.startsWith("[")) rawHost.substringAfter(']') else rawHost.removePrefix(host)
        if (portSuffix.isNotEmpty()) {
            require(portSuffix.startsWith(':'))
            val port = portSuffix.drop(1).toIntOrNull()
            require(port != null && port in 1..65535)
        }
        require(host.isNotBlank() && '%' !in host)
        if (!host.startsWith("[")) IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES or IDN.ALLOW_UNASSIGNED)
        val destination = host.lowercase(Locale.ROOT)
        val notes = mutableListOf<String>()
        if (!text.contains("://")) notes.add("No scheme supplied; interpreted as HTTPS for display only.")
        if (uri.scheme.equals("http", true)) notes.add("HTTP does not provide HTTPS transport encryption.")
        if ('@' in authority) notes.add("Contains user information before @. The destination is $destination, not the text before @.")
        if (destination.any { it.code > 127 } || "xn--" in destination) notes.add("Internationalized hostname: check for look-alike letters or encoded domain labels.")
        if (destination.split('.').size > 3) notes.add("Many domain labels: brand names on the left can be misleading. Verify the entire hostname independently.")
        if (destination.matches(Regex("[0-9.]+")) || ':' in destination) notes.add("Numeric/IP destination: verify who operates it.")
        if (uri.port != -1 && uri.port !in listOf(80, 443)) notes.add("Uses an unusual port: ${uri.port}.")
        if (notes.isEmpty()) notes.add("No listed structural indicators found. This does not establish that the link is safe.")
        notes.add("No website was opened or fetched. Redirects, reputation and page content were not checked. HTTPS alone does not mean trustworthy.")
        return LinkInspection(destination, notes, uri.scheme.lowercase(Locale.ROOT), uri.rawPath.orEmpty().ifEmpty { "/" })
    } catch (_: Exception) { return LinkInspection("", listOf("Only a valid HTTP or HTTPS URL can be inspected.")) }
}
