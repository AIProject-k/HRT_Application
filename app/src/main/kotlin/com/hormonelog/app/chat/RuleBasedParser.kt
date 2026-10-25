package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.ChatMemoryNames
import org.json.JSONObject

/**
 * Decides the cases a local rule can decide without the model: reads, refusals, one clear lab, one dose, or one line per dose.
 * Anything it cannot decide returns null and the model path takes over. Every extracted value still passes ChatCommands.
 */
object RuleBasedParser {
    private val reads = mapOf("현재호르몬수치보여줘" to "status", "현재상태알려줘" to "status", "지금내수치얼마야" to "status",
        "최근검사결과보여줘" to "lab_results", "최근기록보여줘" to "recent", "다음주사언제야" to "next_injection", "단축어목록" to "memory_list")
    private val refusals = listOf(
        Regex("몸무게|체중|혈압") to "체중과 혈압은 이 채팅에서 저장하지 않아요. 기록 화면의 몸 탭에서 입력해 주세요.",
        Regex("메모해줘|메모 해줘|메모 남|메모해 ") to "투약·검사 기록이 아니어서 저장하지 않았어요. 원문을 메모로 남기려면 ‘메모로 저장’을 눌러 주세요.",
        Regex("만약|어떻게|예시|먹어도|복용해도|맞아도|예를 들") to "실제로 복용한 내용만 기록할 수 있어요. 가정이나 예시는 저장하지 않았어요.",
        Regex("\\d+\\s*시\\s*(에서|부터|~)") to "시각 범위는 한 시각으로 기록할 수 없어요. 실제 투약 시각 하나를 알려 주세요.",
    )
    private val correction = Regex("아니[,\\s]*([+-]?[0-9]+(?:\\.[0-9]+)?\\s*mg)", RegexOption.IGNORE_CASE)
    private val labFound = Regex("(E2|Total T|TT|P4|에스트라디올|총테스토스테론|프로게스테론)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(pg/mL|pmol/L|ng/dL|ng/mL|nmol/L)", RegexOption.IGNORE_CASE)

    fun parse(payload: String, state: AppState): ChatCommand? {
        val p = JSONObject(payload)
        val messages = p.getJSONArray("messages")
        val inputs = KoreanTime.normalizeInputs((0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.optString("role") == "user" }.map { it.getString("text") })
        val last = inputs.lastOrNull() ?: return null
        val key = last.replace(Regex("[\\s.!?]"), "")
        if (state.chatMemories.none { ChatMemoryNames.mentioned(last, it.name) }) reads[key]?.let { return ChatCommand(it) }
        val all = inputs.joinToString("\n")
        val pending = p.optJSONObject("pendingRequest")
        val correctedAmount = inputs.lastOrNull { correction.matches(it.trim()) }?.let { line -> correction.find(line.trim())!!.groupValues[1].filterNot(Char::isWhitespace) }
        if (correction.matches(last.trim())) return correctedDose(pending, correctedAmount!!)
        if (Regex("기억|별명|단축어|바꿔|수정|지워|채혈|피검").containsMatchIn(all)) return null
        refusals.firstOrNull { (pattern, _) -> pattern.containsMatchIn(all) }?.let { return ChatCommand("refuse", reply = it.second) }
        if (ChatIntents.blockedDose(state, inputs)) return ChatCommand("refuse", reply = "실제로 복용한 내용만 기록할 수 있어요. 부정·질문·계획 문장은 저장하지 않았어요. 완료한 투약을 한 문장씩 다시 보내 주세요.")
        // A lab and a dose in one sentence would save only one of them and drop the other silently.
        if (labFound.containsMatchIn(all) && ChatCommands.drugNames(state).any { all.contains(it) } && Regex("[먹맞]|복용|투약").containsMatchIn(all))
            return ChatCommand("refuse", reply = "검사 기록과 투약 기록을 한 문장에 섞으면 둘 다 저장하지 않았어요. 한 줄에 하나씩 나누어 보내 주세요.")
        lab(all)?.let { return it }
        if (Regex("검사").containsMatchIn(all)) return null
        if (pending == null) multiLine(inputs, state)?.let { return it }
        return dose(all, state, pending, correctedAmount)
    }

    /** "아니 4mg" changes only the amount of the open dose. Date, time and route keep their own source. */
    private fun correctedDose(pending: JSONObject?, amount: String): ChatCommand =
        if (pending == null || pending.optString("action") != "dose") ChatCommand("refuse", reply = "고칠 대기 중인 투약이 없어요. 저장된 기록은 타임라인에서 고쳐 주세요.")
        else ChatCommand("dose", drug = pending.optString("drug"), route = pending.optString("route"), amount = amount,
            date = pending.optString("date"), time = pending.optString("time"))

    /** One clear dose sentence per line: every line must stand alone with its own drug, amount and time. */
    private fun multiLine(inputs: List<String>, state: AppState): ChatCommand? {
        val lines = inputs.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        val items = lines.map { dose(it, state, null, null) ?: return null }
        if (items.any { it.amount.isBlank() || it.time.isBlank() }) return null
        return ChatCommand("batch", doses = items)
    }

    private fun lab(all: String): ChatCommand? {
        val found = labFound.findAll(all).toList()
        if (found.size != 1 || !Regex("검사|기록|등록|저장").containsMatchIn(all)) return null
        val date = singleLiteral(all, DATES) ?: return null
        val time = singleLiteral(all, TIMES) ?: return null
        val m = found.single()
        return ChatCommand("lab", date = date, time = time, labs = listOf(ChatLabInput(m.groupValues[1], m.groupValues[2], m.groupValues[3])))
    }

    private fun dose(all: String, state: AppState, pending: JSONObject?, correctedAmount: String?): ChatCommand? {
        if (!Regex("[먹맞]|복용|기록|등록|저장").containsMatchIn(all) && pending?.optString("action") != "dose") return null
        val mentions = Regex(ChatCommands.drugNames(state).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
            .findAll(all).map { it.value }.toList()
        // Two mentions of one drug are two doses, or a typo: the rules must not merge them into one record.
        if (mentions.map { it.lowercase() }.distinct().size != 1 || mentions.size != 1) return null
        val drugs = mentions
        val amounts = if (correctedAmount != null) listOf(correctedAmount) else
            Regex("[+-]?[0-9]+(?:\\.[0-9]+)?\\s*(?:mg|mL|정|알)", RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()
        if (amounts.size > 1) return null
        val routes = literals(all, "근육주사|피하주사|경구|설하|(?<![a-z])im(?![a-z])|(?<![a-z])sc(?![a-z])")
        val dates = literals(all, DATES)
        val times = literals(all, TIMES)
        if (routes.size > 1 || dates.size > 1 || times.size > 1) return null
        return ChatCommand("dose", drug = drugs.single(), route = routes.singleOrNull().orEmpty(), amount = amounts.singleOrNull().orEmpty(),
            date = dates.singleOrNull().orEmpty(), time = times.singleOrNull().orEmpty())
    }

    private fun literals(all: String, pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()

    private fun singleLiteral(all: String, pattern: String): String? = literals(all, pattern).singleOrNull()

    private const val DATES = "\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}/\\d{1,2}|\\d{1,3}\\s*일\\s*전|오늘|어제|그제|그저께|엊그제|내일|모레"
    private const val TIMES = "방금|지금|정오|\\d{1,2}\\s*시간\\s*전|\\d{1,3}\\s*분\\s*전|(?:(?:오전|오후|아침|저녁|밤|새벽|낮)\\s*)?\\d{1,2}\\s*시(?:\\s*\\d{1,2}\\s*분|\\s*반)?|(?<![0-9])\\d{1,2}:\\d{2}(?![0-9])"
}
