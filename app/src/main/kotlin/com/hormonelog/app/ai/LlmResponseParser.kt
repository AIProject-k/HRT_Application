package com.hormonelog.app.ai

import com.hormonelog.app.chat.*
import org.json.JSONObject
import org.json.JSONTokener

/** Fail closed: a single JSON command, never executable tools or model-authored medical advice. */
object LlmResponseParser {
    fun parse(raw: String): ChatCommand = try {
        require(raw.length <= 60_000)
        var text=raw.trim()
        if (text.startsWith("```json\n") && text.endsWith("\n```")) text=text.removePrefix("```json\n").removeSuffix("\n```")
        val tokener=JSONTokener(text)
        val o=tokener.nextValue() as? JSONObject ?: error("JSON object required")
        require(tokener.nextClean() == '\u0000')
        val strings=setOf("action", "reply", "drug", "route", "amount", "amountUnit", "date", "time", "memoryName", "memoryTarget")
        fun validate(obj: JSONObject, child: Boolean=false) {
            require(obj.keys().asSequence().toSet().all { it in strings || it in setOf("usual", "labs", "doses") })
            strings.forEach { key -> if (obj.has(key)) require(obj.get(key) is String && obj.getString(key).length <= 600) }
            if (obj.has("usual")) require(obj.get("usual") is Boolean)
            if (child) require(!obj.has("doses") && !obj.has("labs") && obj.optString("action", "dose") == "dose")
        }
        validate(o)
        if (o.has("doses")) {
            val doses=o.getJSONArray("doses")
            require(doses.length() <= ChatCommands.MAX_BATCH_DOSES)
            for (i in 0 until doses.length()) validate(doses.getJSONObject(i), true)
        }
        if (o.has("labs")) {
            val labs=o.getJSONArray("labs")
            require(labs.length() <= 3)
            for (i in 0 until labs.length()) {
                val lab=labs.getJSONObject(i)
                require(lab.keys().asSequence().toSet() == setOf("analyte", "value", "unit"))
                lab.keys().asSequence().toSet().forEach { require(lab.get(it) is String && lab.getString(it).length <= 600) }
            }
        }
        val command=ChatProtocol.decode(JSONObject().put("structured_output",o).toString()).copy(reply="")
        // A literal relative-time token belongs in time, never an invented calendar date.
        if(command.date in setOf("방금","지금") && (command.time.isBlank() || command.time==command.date))
            command.copy(date="",time=command.date) else command
    } catch (e: Exception) {
        throw IllegalArgumentException("AI 응답을 해석하지 못했어요. 기록은 저장하지 않았어요. 문장을 나누어 다시 보내주세요.",e)
    }
}
