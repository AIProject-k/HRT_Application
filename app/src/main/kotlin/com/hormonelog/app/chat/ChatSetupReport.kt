package com.hormonelog.app.chat

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class ChatSetupReport(
    val ubuntu: String, val claude: String, val bridgeId: String,
    val loggedIn: Boolean, val authMethod: String,
    val phase: String, val error: String, val loginUrl: String?, val running: Boolean,
) {
    fun installed(bundleId: String) = ubuntu == "24.04" && claude == "2.1.292" && bridgeId == bundleId
    val subscription: Boolean get() = loggedIn && authMethod == "claude.ai"
    companion object {
        /** Only a code explicitly bound to this CLI OAuth request may be read automatically. */
        fun matchingCode(value: String, loginUrl: String?): String? = runCatching {
            val safe = loginUrl?.let(::officialLoginUrl) ?: return null
            val state = URI(safe).rawQuery.split('&').firstOrNull { it.startsWith("state=") }
                ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") } ?: return null
            val text = value.trim()
            text.takeIf { state.length >= 16 && it.length <= 4096 && it.substringAfter('#', "") == state &&
                Regex("[A-Za-z0-9_~.+/=-]{8,2048}").matches(it.substringBefore('#')) }
        }.getOrNull()
        fun parse(raw: String): ChatSetupReport {
            require(raw.length <= 60_000)
            val o = JSONObject(raw)
            return ChatSetupReport(o.optString("ubuntu"), o.optString("claude"), o.optString("bridgeId"),
                o.optBoolean("loggedIn"), o.optString("authMethod"), o.optString("phase"), o.optString("error"),
                officialLoginUrl(o.optString("loginUrl")), o.optBoolean("running"))
        }
        fun officialLoginUrl(value: String): String? = runCatching {
            val uri = URI(value)
            value.takeIf { it.length in 1..8192 && uri.scheme == "https" && uri.rawUserInfo == null &&
                uri.port == -1 && !uri.rawQuery.isNullOrBlank() &&
                ((uri.host == "claude.com" && uri.path == "/cai/oauth/authorize") ||
                    (uri.host in setOf("claude.ai", "platform.claude.com", "console.anthropic.com") && uri.path == "/oauth/authorize")) }
        }.getOrNull()
    }
}
