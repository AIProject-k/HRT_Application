package com.hormonelog.app.chat

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

class ChatSetupAssets(context: Context) {
    private val files = listOf("setup.py", "hrt-login.py", "hrt-chat", "hrt-chat-guest", "schema.json", "prompt.txt")
        .associateWith { context.assets.open("chat/$it").bufferedReader().use { r -> r.readText().replace("\r\n", "\n") } }
    val id: String = MessageDigest.getInstance("SHA-256").digest(files.entries.joinToString("\n") { "${it.key}\n${it.value}" }.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val bundle = encode(JSONObject().put("id", id).put("files", JSONObject().apply {
        files.forEach { (name, data) -> put(name, encode(data)) }
    }).toString())
    private val installer = context.assets.open("chat/setup.sh").bufferedReader().use { it.readText().replace("\r\n", "\n") }

    fun install() = "export HRT_SETUP_BUNDLE='$bundle'\n$installer"
    fun command(mode: String, code: String = ""): String {
        require(mode in setOf("probe", "login", "code", "verify", "cancel_login"))
        val payload = if (mode == "code") "export HRT_SETUP_CODE='${encode(code)}'\n" else ""
        return "set -eu\nif ! command -v python >/dev/null || ! command -v proot-distro >/dev/null; then " +
            "if [ -f \"\$HOME/.local/share/hormonelog-chat/job-pid\" ] && kill -0 \"\$(cat \"\$HOME/.local/share/hormonelog-chat/job-pid\")\" 2>/dev/null; then printf '{\"running\":true,\"phase\":\"termux\"}'; else printf '{}'; fi; exit 0; fi\n" +
            payload + "python - $mode <<'HRT_SETUP_PY'\n${files.getValue("setup.py")}\nHRT_SETUP_PY\n"
    }
    companion object {
        private fun encode(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val bootstrap = "mkdir -p \"\$HOME/.termux\"; if [ -f \"\$HOME/.termux/termux.properties\" ] && [ ! -f \"\$HOME/.termux/termux.properties.before-hormonelog\" ]; then cp \"\$HOME/.termux/termux.properties\" \"\$HOME/.termux/termux.properties.before-hormonelog\"; fi; printf '\\nallow-external-apps=true\\n' >> \"\$HOME/.termux/termux.properties\"; termux-reload-settings; am start -n com.hormonelog.app/.LauncherDefault"
    }
}
