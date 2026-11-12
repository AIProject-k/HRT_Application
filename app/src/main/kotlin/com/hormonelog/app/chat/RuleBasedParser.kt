package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.ChatMemoryNames
import org.json.JSONObject

/**
 * Decides the cases a local rule can decide without the model: reads, refusals, one clear lab, one dose, or one line per dose.
 * Anything it cannot decide returns null and the model path takes over. Every extracted value still passes ChatCommands.
 */
object RuleBasedParser {
    private const val SHOW = "(?:좀)?(?:알려줘|알려주세요|보여줘|보여주세요|어때|어때요|얼마야|얼마예요|몇이야|뭐야|궁금해)?"

    /** Read requests, matched against the whole message with spaces and end marks removed, so a sentence with anything else in it is not a read. */
    private val reads = listOf(
        Regex("(?:현재|지금|요즘)?(?:내|제)?(?:호르몬)?(?:상태|수치)(?:는|가)?$SHOW") to "status",
        Regex("(?:현재|지금|요즘)?(?:내|제)?(?:e2|tt|p4|에스트라디올|테스토스테론|프로게스테론)(?:수치)?(?:는|가)?$SHOW", RegexOption.IGNORE_CASE) to "status",
        Regex("(?:최근|마지막|지난)(?:피)?검사(?:결과|수치)?(?:는|가)?$SHOW|(?:피)?검사(?:결과|수치)(?:는|가)?$SHOW") to "lab_results",
        Regex("(?:최근|마지막|지난)기록(?:은|이)?$SHOW") to "recent",
        Regex("다음주사(?:는|가)?(?:일정)?(?:언제|언제야|언제예요|언제지|언제맞아)?$SHOW") to "next_injection",
        Regex("단축어목록$SHOW") to "memory_list",
    )
    private val help = Regex("도움말|사용법|뭐할수있어|뭘할수있어|어떻게써|어떻게쓰는거야|뭐라고말해야돼")
    private const val HELP = "이렇게 말해 보세요.\n· 방금 에스트라디올 2mg 먹었어\n· 어제 오후 9시 EV 5mg 근육주사 맞았어\n" +
        "· 방금 에스트라디올 2mg, 스피로노락톤 50mg 먹었어\n· 오늘 오전 9시 검사 E2 150 pg/mL, TT 20 ng/dL 기록\n" +
        "· 현재 상태 / 최근 기록 / 최근 검사 / 다음 주사"
    private val refusals = listOf(
        Regex("몸무게|체중|혈압") to "체중과 혈압은 이 채팅에서 저장하지 않아요. 기록 화면의 몸 탭에서 입력해 주세요.",
        Regex("메모해줘|메모 해줘|메모 남|메모해 ") to "투약·검사 기록이 아니어서 저장하지 않았어요. 원문을 메모로 남기려면 ‘메모로 저장’을 눌러 주세요.",
        Regex("만약|어떻게|예시|먹어도|복용해도|맞아도|예를 들") to "실제로 복용한 내용만 기록할 수 있어요. 가정이나 예시는 저장하지 않았어요.",
        Regex("\\d+\\s*시\\s*(에서|부터|~)") to "시각 범위는 한 시각으로 기록할 수 없어요. 실제 투약 시각 하나를 알려 주세요.",
    )
    private val correction = Regex("아니[,\\s]*([+-]?[0-9]+(?:\\.[0-9]+)?\\s*mg)", RegexOption.IGNORE_CASE)
    private val labFound = Regex("(E2|Total T|TT|P4|에스트라디올|총테스토스테론|테스토스테론|프로게스테론)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(pg/mL|pmol/L|ng/dL|ng/mL|nmol/L)", RegexOption.IGNORE_CASE)

    fun parse(payload: String, state: AppState): ChatCommand? {
        val p = JSONObject(payload)
        val messages = p.getJSONArray("messages")
        val inputs = KoreanTime.normalizeInputs((0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.optString("role") == "user" }.map { it.getString("text") })
        val last = inputs.lastOrNull() ?: return null
        val key = last.replace(Regex("[\\s.!?]"), "")
        if (state.chatMemories.none { ChatMemoryNames.mentioned(last, it.name) }) {
            if (help.matches(key)) return ChatCommand("ask", reply = HELP)
            reads.firstOrNull { it.first.matches(key) }?.let { return ChatCommand(it.second) }
        }
        val all = inputs.joinToString("\n")
        val pending = p.optJSONObject("pendingRequest")
        val correctedAmount = inputs.lastOrNull { correction.matches(it.trim()) }?.let { line -> correction.find(line.trim())!!.groupValues[1].filterNot(Char::isWhitespace) }
        if (correction.matches(last.trim())) return correctedDose(pending, correctedAmount!!)
        if (Regex("기억|별명|단축어|바꿔|수정|지워").containsMatchIn(all)) return null
        refusals.firstOrNull { (pattern, _) -> pattern.containsMatchIn(all) }?.let { return ChatCommand("refuse", reply = it.second) }
        if (ChatIntents.blockedDose(state, inputs)) return ChatCommand("refuse", reply = "실제로 복용한 내용만 기록할 수 있어요. 부정·질문·계획 문장은 저장하지 않았어요. 완료한 투약을 한 문장씩 다시 보내 주세요.")
        // A lab and a dose in one sentence would save only one of them and drop the other silently.
        if (labFound.containsMatchIn(all) && ChatCommands.drugNames(state).any { all.contains(it) } && Regex("[먹맞]|복용|투약").containsMatchIn(all))
            return ChatCommand("refuse", reply = "검사 기록과 투약 기록을 한 문장에 섞으면 둘 다 저장하지 않았어요. 한 줄에 하나씩 나누어 보내 주세요.")
        lab(all)?.let { return it }
        if (Regex("검사|채혈|피검").containsMatchIn(all)) return null
        if (pending == null) (multiLine(inputs, state) ?: oneLine(inputs, state))?.let { return it }
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

    /** One to three results in one message, each with its own unit, and one date and one time for all of them. */
    private fun lab(all: String): ChatCommand? {
        val found = labFound.findAll(all).toList()
        if (found.size !in 1..3 || !Regex("검사|기록|등록|저장|나왔|결과|채혈").containsMatchIn(all)) return null
        // 유리(free) testosterone is a different test; the bare word must not be stored as Total T.
        if (Regex("유리|프리|free", RegexOption.IGNORE_CASE).containsMatchIn(all)) return null
        val date = singleLiteral(all, DATES) ?: return null
        val time = singleLiteral(all, TIMES) ?: return null
        return ChatCommand("lab", date = date, time = time, labs = found.map { ChatLabInput(it.groupValues[1], it.groupValues[2], it.groupValues[3]) })
    }

    /**
     * Pills taken together in one sentence: "방금 에스트라디올 2mg, 스피로노락톤 50mg 먹었어". Each part names one drug and one mg amount,
     * and the sentence gives one time for all of them. Injections and other routes stay one per message.
     */
    private fun oneLine(inputs: List<String>, state: AppState): ChatCommand? {
        val line = inputs.singleOrNull()?.takeIf { '\n' !in it } ?: return null
        if (!Regex("먹었|먹엇|먹음|복용").containsMatchIn(line) || Regex("맞|주사|붙|발랐|$ROUTES", RegexOption.IGNORE_CASE).containsMatchIn(line)) return null
        val parts = line.split(Regex("(?<=mg)\\s*(?:,|이랑|랑|하고|와|과)\\s*|\\s*그리고\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE))
        if (parts.size < 2) return null
        val items = parts.map { part ->
            val drug = drugMentions(part, state).singleOrNull() ?: return null
            val amount = Regex("[0-9]+(?:\\.[0-9]+)?\\s*mg", RegexOption.IGNORE_CASE).findAll(part).map { it.value }.singleOrNull() ?: return null
            ChatCommand("dose", drug = drug, amount = amount)
        }
        if (items.mapNotNull { ChatCommands.knownDrug(state, it.drug)?.drug }.distinct().size != items.size) return null
        val dates = literals(line, DATES)
        val times = literals(line, TIMES)
        if (dates.size > 1 || times.size != 1) return null
        return ChatCommand("batch", date = dates.singleOrNull().orEmpty(), time = times.single(), doses = items)
    }

    private fun drugMentions(text: String, state: AppState): List<String> =
        Regex(ChatCommands.drugNames(state).sortedByDescending { it.length }.joinToString("|") { ChatCommands.namePattern(it) }, RegexOption.IGNORE_CASE)
            .findAll(text).map { it.value }.toList()

    private fun dose(all: String, state: AppState, pending: JSONObject?, correctedAmount: String?): ChatCommand? {
        if (!Regex("[먹맞]|${ChatCommands.RECORD_WORDS}").containsMatchIn(all) && pending?.optString("action") != "dose") return null
        val mentions = drugMentions(all, state)
        // Two mentions of one drug are two doses, or a typo: the rules must not merge them into one record.
        if (mentions.map { it.lowercase() }.distinct().size != 1 || mentions.size != 1) return null
        val drugs = mentions
        val amounts = if (correctedAmount != null) listOf(correctedAmount) else
            Regex("[+-]?[0-9]+(?:\\.[0-9]+)?\\s*(?:mg|mL|정|알)", RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()
        if (amounts.size > 1) return null
        val routes = literals(all, ROUTES)
        val dates = literals(all, DATES)
        val times = literals(all, TIMES)
        if (routes.size > 1 || dates.size > 1 || times.size > 1) return null
        return ChatCommand("dose", drug = drugs.single(), route = routes.singleOrNull().orEmpty(), amount = amounts.singleOrNull().orEmpty(),
            date = dates.singleOrNull().orEmpty(), time = times.singleOrNull().orEmpty())
    }

    private fun literals(all: String, pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).findAll(all).map { it.value }.distinct().toList()

    private fun singleLiteral(all: String, pattern: String): String? = literals(all, pattern).singleOrNull()

    private const val ROUTES = ChatCommands.ROUTE_WORDS
    private const val DATES = "\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}/\\d{1,2}|\\d{1,3}\\s*일\\s*전|오늘|어제|그제|그저께|엊그제|내일|모레"
    private const val TIMES = "방금|지금|정오|\\d{1,2}\\s*시간\\s*전|\\d{1,3}\\s*분\\s*전|(?:(?:오전|오후|아침|저녁|밤|새벽|낮)\\s*)?\\d{1,2}\\s*시(?:\\s*\\d{1,2}\\s*분|\\s*반)?|(?<![0-9])\\d{1,2}:\\d{2}(?![0-9])"
}
