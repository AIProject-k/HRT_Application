package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.ChatMemoryNames
import org.json.JSONObject

/** Recognize only unambiguous local forms. All extracted tokens still pass ChatCommands. */
object RuleBasedParser {
    fun parse(payload: String, state: AppState): ChatCommand? {
        val p=JSONObject(payload)
        val messages=p.getJSONArray("messages")
        val inputs=(0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.optString("role")=="user" }.map { it.getString("text") }
        val last=inputs.lastOrNull() ?: return null
        val key=last.replace(Regex("[\\s.!?]"),"")
        val reads=mapOf("현재호르몬수치보여줘" to "status", "현재상태알려줘" to "status", "지금내수치얼마야" to "status",
            "최근검사결과보여줘" to "lab_results", "최근기록보여줘" to "recent", "다음주사언제야" to "next_injection", "단축어목록" to "memory_list")
        if (state.chatMemories.none { ChatMemoryNames.mentioned(last,it.name) }) reads[key]?.let { return ChatCommand(it) }
        val all=inputs.joinToString("\n")
        if (Regex("기억|별명|단축어|바꿔|수정|지워|검사|채혈|피검|예시|만약|어떻게|먹어도").containsMatchIn(all)) return null
        val pending=p.optJSONObject("pendingRequest")
        if (!Regex("먹었|먹엇|복용|먹음|먹은|먹고|맞았|맞고|기록|등록|저장").containsMatchIn(all) && pending?.optString("action")!="dose") return null
        val names=ChatCommands.drugNames(state).sortedByDescending { it.length }
        val matches=Regex(names.joinToString("|") { Regex.escape(it) },RegexOption.IGNORE_CASE).findAll(all).toList()
        val drugs=matches.map { it.value }.distinctBy { it.lowercase() }
        if (drugs.size != 1) return null
        val amounts=Regex("[+-]?[0-9]+(?:\\.[0-9]+)?\\s*(?:mg|mL|정|알)",RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()
        if (amounts.size>1) return null
        fun literals(pattern: String) = Regex(pattern,RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()
        val routes=literals("근육주사|피하주사|경구|설하|(?<![a-z])im(?![a-z])|(?<![a-z])sc(?![a-z])")
        val dates=literals("\\d{4}-\\d{2}-\\d{2}|오늘|어제|그제")
        val times=literals("방금|지금|(?:오전|오후)\\s*\\d{1,2}(?::\\d{2}|시(?:\\s*\\d{1,2}분|\\s*반)?)|(?<![0-9])\\d{1,2}:\\d{2}(?![0-9])")
        if(routes.size>1 || dates.size>1 || times.size>1) return null
        return ChatCommand("dose",drug=drugs.single(),route=routes.singleOrNull().orEmpty(),amount=amounts.singleOrNull().orEmpty(),
            date=dates.singleOrNull().orEmpty(),time=times.singleOrNull().orEmpty())
    }
}
