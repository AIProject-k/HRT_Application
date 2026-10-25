package com.hormonelog.app.chat

import org.json.JSONArray
import org.json.JSONObject
import com.hormonelog.core.domain.ChatMemory

object ChatProtocol {
    fun commandJson(c: ChatCommand): JSONObject = JSONObject()
        .put("action", c.action).put("reply", c.reply).put("drug", c.drug).put("route", c.route)
        .put("amount", c.amount).put("amountUnit", c.amountUnit).put("date", c.date).put("time", c.time).put("usual", c.usual)
        .put("memoryName", c.memoryName).put("memoryTarget", c.memoryTarget)
        .put("doses", JSONArray().apply { c.doses.forEach { put(commandJson(it)) } })
        .put("labs", JSONArray().apply { c.labs.forEach { put(JSONObject().put("analyte", it.analyte).put("value", it.value).put("unit", it.unit)) } })
    fun decode(raw: String): ChatCommand {
        require(raw.length <= 60_000)
        val envelope = JSONObject(raw)
        require(!envelope.optBoolean("is_error")) { "AI 요청 실패" }
        val o = envelope.optJSONObject("structured_output") ?: throw IllegalArgumentException("구조화된 응답이 없어요")
        val action = o.getString("action")
        require(action in setOf("ask", "refuse", "dose", "batch", "lab") || action in ChatCommands.readActions || action in ChatMemoryActions.actions)
        fun text(key: String) = o.optString(key, "").take(600)
        val labs = o.optJSONArray("labs") ?: JSONArray()
        require(labs.length() <= 3)
        val doses = o.optJSONArray("doses") ?: JSONArray()
        require(doses.length() <= ChatCommands.MAX_BATCH_DOSES)
        return ChatCommand(action, text("reply"), text("drug"), text("route"), text("amount"), text("date"), text("time"), o.optBoolean("usual"),
            (0 until labs.length()).map { i -> labs.getJSONObject(i).let { ChatLabInput(it.getString("analyte"), it.getString("value"), it.getString("unit")) } },
            amountUnit = text("amountUnit"), doses = (0 until doses.length()).map { i -> doses.getJSONObject(i).let { d ->
                fun field(key: String) = d.optString(key, "").take(600)
                ChatCommand("dose", drug = field("drug"), route = field("route"), amount = field("amount"),
                    date = field("date"), time = field("time"), usual = d.optBoolean("usual"), amountUnit = field("amountUnit"))
            } }, memoryName = text("memoryName"), memoryTarget = text("memoryTarget"))
    }

    fun request(turns: List<Pair<Boolean, String>>, previousDoseTimeAvailable: Boolean = false, memories: List<ChatMemory> = emptyList(), context: List<Pair<Boolean, String>> = emptyList(), pending: ChatCommand? = null, referenceAvailable: Boolean = false): String {
        val payload = JSONObject().put("previousDoseTimeAvailable", previousDoseTimeAvailable)
            .put("sameDrugReferenceAvailable", referenceAvailable)
            .put("memoryNames", JSONArray().apply { memories.take(100).forEach { memory ->
                put(JSONObject().put("name", memory.name).put("kind", if (memory is ChatMemory.Alias) "alias" else "shortcut"))
            } }).put("messages", JSONArray().apply {
                turns.forEach { (user, text) -> put(JSONObject().put("role", if (user) "user" else "assistant").put("text", text)) }
            })
        pending?.let { payload.put("pendingRequest", commandJson(it)) }
        var recentContext = context.takeLast(20)
        while (true) {
            val result = payload.put("contextMessages", JSONArray().apply {
                recentContext.forEach { (user, text) -> put(JSONObject().put("role", if (user) "user" else "assistant").put("text", text)) }
            }).toString()
            if (result.toByteArray().size < 120_000 || recentContext.isEmpty()) return result
            recentContext = recentContext.drop(1)
        }
    }
}
