package com.hormonelog.app.chat

import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ChatLabInput(val analyte: String, val value: String, val unit: String)
data class ChatCommand(
    val action: String, val reply: String = "", val drug: String = "", val route: String = "",
    val amount: String = "", val date: String = "", val time: String = "", val usual: Boolean = false,
    val labs: List<ChatLabInput> = emptyList(),
    val amountUnit: String = "", val doses: List<ChatCommand> = emptyList(),
    val memoryName: String = "", val memoryTarget: String = "",
)
data class ChatDecision(val message: String, val next: AppState? = null, val closes: Boolean = false)
enum class ChatInputMode { RECORD, TEMPLATE, SHORTCUT }

/** Parsers extract literal evidence; only this local validator may turn it into records. */
object ChatCommands {
    const val MAX_BATCH_DOSES = 50
    val readActions = setOf("recent", "status", "lab_results", "next_injection")
    private fun compact(s: String) = s.lowercase().replace(Regex("\\s+"), "")
    private val drugs = mapOf(
        "ev" to Drug.ESTRADIOL_VALERATE, "에스트라디올발레레이트" to Drug.ESTRADIOL_VALERATE,
        "ec" to Drug.ESTRADIOL_CYPIONATE, "에스트라디올사이피오네이트" to Drug.ESTRADIOL_CYPIONATE,
        "에스트라디올" to Drug.ESTRADIOL_TABLET, "프로기노바" to Drug.ESTRADIOL_TABLET,
        "스피로노락톤" to Drug.SPIRONOLACTONE, "사이프로테론" to Drug.CYPROTERONE,
        "안쿨" to Drug.CYPROTERONE, "안드로쿨" to Drug.CYPROTERONE, "안드로쿨정" to Drug.CYPROTERONE,
        "androcur" to Drug.CYPROTERONE, "시프로테론" to Drug.CYPROTERONE,
        "시프로테론아세테이트" to Drug.CYPROTERONE, "cyproterone" to Drug.CYPROTERONE,
        "유트로게스탄" to Drug.PROGESTERONE_MICRONIZED, "미분화프로게스테론" to Drug.PROGESTERONE_MICRONIZED,
        "유트로게스탄연질캡슐" to Drug.PROGESTERONE_MICRONIZED,
    )
    private val routes = mapOf("근육주사" to Route.IM_INJECTION, "im" to Route.IM_INJECTION,
        "피하주사" to Route.SC_INJECTION, "sc" to Route.SC_INJECTION, "경구" to Route.ORAL,
        "먹었어" to Route.ORAL, "복용" to Route.ORAL, "설하" to Route.SUBLINGUAL)

    fun knownDrug(s: AppState, token: String): ChatMemory.Alias? {
        val name = compact(token)
        val builtin = drugs[name]
        if (builtin != null) return ChatMemory.Alias(token.trim(), builtin,
            if (name.startsWith("유트로게스탄")) P4Product.UTROGESTAN_SOFT_CAPSULE else P4Product.UNKNOWN)
        return s.chatMemories.filterIsInstance<ChatMemory.Alias>().firstOrNull { compact(it.name) == name }
    }

    internal fun drugNames(s: AppState): List<String> = drugs.keys.toList() + s.chatMemories.filterIsInstance<ChatMemory.Alias>().map { it.name }

    fun reservedName(name: String) = compact(name) in drugs || compact(name) in routes || compact(name) in
        setOf("오늘", "어제", "방금", "지금", "최근", "기록", "등록", "취소", "기억", "단축어", "e2", "p4", "tt") || ChatShortcut.find(name) != null

    fun switchesDrug(s: AppState, pending: ChatCommand, text: String): Boolean {
        val before = knownDrug(s, pending.drug)?.drug ?: return false
        val mentioned = drugNames(s).filter { compact(text).contains(compact(it)) }.mapNotNull { knownDrug(s, it)?.drug }
        return mentioned.isNotEmpty() && before !in mentioned
    }

    fun apply(s: AppState, c: ChatCommand, inputs: List<String>, id: UUID, now: Instant, zone: ZoneId, previousDoseAt: Instant? = null, mode: ChatInputMode = ChatInputMode.RECORD): ChatDecision {
        fun ask(message: String) = ChatDecision(message)
        fun refuse(message: String) = ChatDecision(message, closes = true)
        if (mode == ChatInputMode.RECORD && c.action in setOf("dose", "batch", "lab") && inputs.any { Regex("기억해|기억하|외워|단축어로|별명으로").containsMatchIn(it) })
            return ask("기억 요청은 단축어로 저장해야 해요. 실제 투약으로 등록하지 않았어요.")
        if (c.action == "batch") {
            if (c.doses.size !in 2..MAX_BATCH_DOSES) return ask("한 번에 투약 2~${MAX_BATCH_DOSES}건을 함께 기록할 수 있어요.")
            var next = s
            val messages = mutableListOf<String>()
            for ((index, item) in c.doses.withIndex()) {
                val child = item.copy(action = "dose", date = item.date.ifBlank { c.date }, time = item.time.ifBlank { c.time }, doses = emptyList())
                val childId = if (index == 0) id else UUID.nameUUIDFromBytes("$id:$index".toByteArray(Charsets.UTF_8))
                val result = apply(next, child, inputs, childId, now, zone, previousDoseAt, mode)
                next = result.next ?: return ask("${item.drug.ifBlank { "투약 ${index + 1}" }}: ${result.message}\n아직 이 묶음은 저장하지 않았어요.")
                messages += result.message
            }
            return ChatDecision(messages.joinToString("\n"), next)
        }
        if (c.action == "recent") return ask(recent(s, zone))
        if (c.action == "status") return ask(ChatStatus.summary(s, now, zone))
        if (c.action == "lab_results") return ask(ChatStatus.labResults(s, now, zone))
        if (c.action == "next_injection") return ask(ChatStatus.nextInjection(s, now, zone))
        if (c.action == "ask") return ask(c.reply.ifBlank { "투약 등록, 검사 결과 등록, 최근 기록 조회를 도와드릴게요." }.take(600))
        if (c.action !in setOf("dose", "lab")) return ask("투약·검사 등록과 현재 상태·검사·최근 기록 조회를 지원해요.")
        val text = compact(inputs.joinToString("\n"))
        val additional = inputs.any { Regex("도(?:\\s*(?:기록해줘|등록해줘|추가해줘))?[.!?]?$|같은\\s*시간").containsMatchIn(it.trim()) }
        if ((mode == ChatInputMode.RECORD && !Regex("기록|등록|저장|추가|맞았|맞고|맞음|맞은|먹음|먹었|먹엇|먹은|먹고|복용").containsMatchIn(text) && !(additional && previousDoseAt != null)) ||
            (mode != ChatInputMode.TEMPLATE &&
            Regex("하지마|하지말|취소|안맞|안먹|먹지않|복용안|복용하지않|복용전|투약전|먹기전|맞기전|먹어도|복용해도|맞아도|예정|어떻게|예시|만약|못했|못먹|못맞|못함|않았|않음|안했|\\?").containsMatchIn(text))) {
            return refuse("실제로 투약하거나 검사한 내용을 등록할 때 ‘기록해줘’라고 말씀해 주세요.")
        }
        fun grounded(token: String): Boolean {
            val t = compact(token)
            if (t.isBlank()) return false
            if (token == c.drug && s.chatMemories.filterIsInstance<ChatMemory.Alias>().any { compact(it.name) == t })
                return inputs.any { ChatMemoryNames.mentioned(it, token) }
            return inputs.any { mentions(it, token, token == c.time) }
        }
        val implicitToday = compact(c.time) in setOf("방금", "지금") && compact(c.date) == "오늘" &&
            !Regex("어제|그제|내일|\\d{4}-\\d{2}-\\d{2}").containsMatchIn(text)
        val tokens = listOf(c.drug, c.route, c.amount, c.amountUnit, c.date.takeUnless { implicitToday }.orEmpty(), c.time) + c.labs.flatMap { listOf(it.analyte, it.value, it.unit) }
        if (tokens.filter { it.isNotBlank() }.any { !grounded(it) }) return ask("입력에서 확인하지 못한 정보가 있어요. 제품·용량·단위와 시각을 직접 알려주세요.")
        if (s.doses.any { it.id == id } || s.labs.any { it.id == id }) return ask("이 요청은 이미 기록되어 있어요.")
        val unknownTime = c.action == "lab" && compact(c.time) in setOf("시간모름", "시각모름")
        val at = when {
            mode == ChatInputMode.TEMPLATE -> now
            unknownTime -> null
            c.action == "dose" && c.date.isBlank() && c.time.isBlank() && additional -> previousDoseAt
            else -> resolveTime(c.date, c.time, now, zone)
        }
        fun timeQuestion() = when {
            c.time.isBlank() -> "언제 투약하거나 채혈했나요? 방금이라면 ‘방금’이라고만 해주세요."
            c.date.isBlank() && compact(c.time) !in setOf("방금", "지금") -> "날짜만 알려주세요. 예: 오늘, 어제, 2026-10-18."
            else -> "시각을 확인해 주세요. 예: 어제 오후 3시, 오늘 15:30. ‘방금’은 지금 시각으로 기록해요."
        }
        if (c.action == "dose") {
            if (c.date.isBlank() && Regex("내일|모레|글피|\\d+월|\\d+일|요일|\\d{4}년").containsMatchIn(text)) return ask("날짜를 확인하지 못했어요. 날짜를 ‘2026-10-24’처럼 알려주세요.")
            if (needsOwnClause(s, c, inputs) && !boundInOwnClause(c, inputs)) return ask("용량과 시각이 어느 약의 것인지 원문에서 확인하지 못했어요. 약별로 나누어 다시 알려주세요.")
            val day = c.date.takeIf { it.isNotBlank() }?.let { KoreanTime.day(it, now, zone) ?: return ask("날짜를 확인하지 못했어요. 날짜를 ‘2026-10-24’처럼 알려주세요.") }
            if (day != null && day.yearless && inputs.drop(inputs.indexOfLast { compact(it).contains(compact(c.date)) } + 1).none { KoreanTime.isAffirmation(it) })
                return ask("${day.date.year}년 ${day.date.monthValue}월 ${day.date.dayOfMonth}일로 기록할까요? 맞으면 ‘네’라고 답해 주세요.")
            val drugAlias = knownDrug(s, c.drug)
            val parsedDrug = drugAlias?.drug
            val parsedRoute = routes[compact(c.route)]
            if (c.drug.isNotBlank() && parsedDrug == null && !(c.usual && compact(c.drug) == "데포")) return ask("이 제품명은 확인할 수 없어요. 앱에서 지원하는 정확한 성분·제품명을 알려주세요.")
            if (c.route.isNotBlank() && parsedRoute == null) return ask("투여 경로를 확인할 수 없어요. 근육주사·피하주사·경구·설하 중 실제 경로를 알려주세요.")
            val plan = if (c.usual && Regex("평소|일정대로").containsMatchIn(text)) {
                s.regimens.filter { it.active && !it.startAt.isAfter(now) && (it.endAt == null || !it.endAt!!.isBefore(now)) }
                    .filter { parsedDrug == null || it.drug == parsedDrug }
                    .filter { parsedRoute == null || it.route == parsedRoute }
                    .filter { compact(c.drug) != "데포" || it.route in setOf(Route.IM_INJECTION, Route.SC_INJECTION) }
                    .singleOrNull()
            } else null
            if (c.usual && plan == null) return ask("일치하는 기존 일정이 하나로 정해지지 않아요. 제품명·용량·투여 경로를 알려주세요.")
            val drug = parsedDrug ?: plan?.drug ?: return ask("정확한 제품이나 성분을 알려주세요. ‘데포’만으로 약물을 정하지 않아요. 예: EV(발레레이트), EC(사이피오네이트).")
            val oral = if (Regex("복용|먹었|먹엇|먹은|먹음|먹고").containsMatchIn(text) || drug in setOf(Drug.CYPROTERONE, Drug.SPIRONOLACTONE)) Route.ORAL else null
            val route = parsedRoute ?: plan?.route ?: oral ?: return ask("투여 경로만 알려주세요. 예: 근육주사, 피하주사, 경구, 설하.")
            if (c.amount.isBlank() && plan == null) return ask("${c.drug.ifBlank { drug.label }} 용량은 몇 mg인가요? 실제 복용량을 ‘숫자+mg’ 형태로 알려주세요.")
            val amountMatch = Regex("([0-9]+(?:\\.[0-9]+)?)(mg)?").matchEntire(compact(c.amount))
            if (amountMatch != null && amountMatch.groupValues[2].isBlank() && compact(c.amountUnit) != "mg") return ask("${c.drug} ${c.amount}의 단위만 알려주세요. 예: ‘mg’.")
            if (c.amountUnit.isNotBlank() && compact(c.amountUnit) != "mg") return ask("이 기록은 mg 단위 용량이 필요해요. 정·알·mL를 mg으로 추측하지 않아요.")
            if (amountMatch != null && amountMatch.groupValues[2].isBlank() && !grounded("${c.amount} mg")) {
                val clarified = inputs.any { input ->
                    val t = compact(input)
                    Regex("^(?:단위는)?mg(?:야|이야|입니다|단위야|단위|임)?[.!]*$").matches(t) ||
                        (drugs.any { (alias, value) -> value == drug && t.contains(alias) } && Regex("[은는]mg|mg단위|단위[는가]?mg").containsMatchIn(t))
                }
                if (!clarified) return ask("${c.drug} ${c.amount}의 단위만 확인해 주세요. 다른 약의 단위를 가져오지 않아요.")
            }
            val amount = if (c.amount.isNotBlank()) amountMatch?.groupValues?.get(1)?.toDoubleOrNull()
                else plan?.amountEntered?.takeIf { plan.enteredUnit == DoseUnit.MG }
            if (amount == null || !amount.isFinite() || amount <= 0 || amount > DOSE_MAX) return ask("0보다 크고 500 이하인 mg 용량을 알려주세요. mL를 mg으로 추측 변환하지 않아요.")
            if (route !in drug.allowedRoutes) return ask("이 약물과 투여 경로가 맞지 않아요. 제품과 경로를 확인해 주세요.")
            if (at == null) return ask(if (day != null && KoreanTime.moment(c.time, now) == KoreanTime.Moment.Ambiguous) "오전인지 오후인지 알려주세요. 예: ‘오전 10시’." else timeQuestion())
            if (at.isAfter(now.plusSeconds(60))) return ask("아직 오지 않은 시각이에요. 실제 투약한 시각을 확인해 주세요.")
            val p4 = P4Context(
                drugAlias?.product?.takeIf { it != P4Product.UNKNOWN } ?: if (text.contains("유트로게스탄")) P4Product.UTROGESTAN_SOFT_CAPSULE else plan?.p4Context?.product ?: P4Product.UNKNOWN,
                when { text.contains("고지방") -> P4Meal.HIGH_FAT_MEAL; text.contains("식후") -> P4Meal.AFTER_MEAL; text.contains("공복") -> P4Meal.FASTING; else -> P4Meal.UNKNOWN },
            )
            val draft = DoseDraft(drug = drug, route = route, amountText = plainNumber(amount), time = DoseTime.PICK, pickedMillis = at.toEpochMilli(), p4Context = p4)
            val applied = RecordsReducer.saveDose(s.copy(doseDraft = draft, confirm = null), now, zone)
            if (applied.doses.size != s.doses.size + 1) return ask("같은 약물의 가까운 시각 기록이 있거나 입력이 올바르지 않아요. 기존 기록을 확인해 주세요.")
            val new = applied.doses.first { e -> s.doses.none { it.id == e.id } }
            val next = applied.copy(doses = applied.doses.map { if (it.id == new.id) it.copy(id = id) else it },
                doseDraft = s.doseDraft, sheet = s.sheet, confirm = s.confirm, undo = null, toast = null)
            val brand = if (compact(c.drug) in setOf("안쿨", "안드로쿨", "안드로쿨정", "androcur")) "안드로쿨 · " else ""
            return ChatDecision("${format(at, zone)} · $brand${doseSummary(drug, route, amount, DoseUnit.MG)}", next)
        }
        if (at == null && !unknownTime) return ask(timeQuestion())
        if (at != null && at.isAfter(now.plusSeconds(60))) return ask("아직 오지 않은 채혈 시각이에요. 실제 검사 시각을 확인해 주세요.")
        if (c.labs.isEmpty() || c.labs.size > 3) return ask("검사 항목(E2, Total T, P4), 수치, 단위를 알려주세요.")
        var draft = LabDraft(draw = if (unknownTime) DrawChoice.UNKNOWN else DrawChoice.PICK, pickedMillis = at?.toEpochMilli())
        val seen = mutableSetOf<Analyte>()
        for (v in c.labs) {
            val n = v.value.toDoubleOrNull()
            if (n == null || !n.isFinite() || n < 0 || n > 1_000_000) return ask("검사 수치를 확인해 주세요.")
            val unit = compact(v.unit)
            val a = when (compact(v.analyte)) {
                "e2", "에스트라디올" -> Analyte.ESTRADIOL
                "totalt", "tt", "총테스토스테론" -> Analyte.TOTAL_TESTOSTERONE
                "p4", "프로게스테론" -> Analyte.PROGESTERONE
                else -> return ask("검사 항목은 E2, Total T, P4를 지원해요.")
            }
            if (!seen.add(a)) return ask("같은 검사 항목을 두 번 지정했어요. 값을 확인해 주세요.")
            draft = when (a) {
                Analyte.ESTRADIOL -> draft.copy(e2 = v.value, e2Unit = E2Unit.entries.firstOrNull { compact(it.label) == unit } ?: return ask("E2 단위를 pg/mL 또는 pmol/L로 알려주세요."))
                Analyte.TOTAL_TESTOSTERONE -> draft.copy(tt = v.value, ttUnit = TUnit.entries.firstOrNull { compact(it.label) == unit } ?: return ask("Total T 단위를 ng/dL, ng/mL, nmol/L 중에서 알려주세요."))
                Analyte.PROGESTERONE -> draft.copy(p4 = v.value, p4Unit = P4Unit.entries.firstOrNull { compact(it.label) == unit } ?: return ask("P4 단위를 ng/mL 또는 nmol/L로 알려주세요."))
            }
            if (n == 0.0 && a != Analyte.PROGESTERONE) return ask("이 검사 항목의 0 값은 현재 기록기가 지원하지 않아요. 결과지를 확인해 주세요.")
        }
        val applied = RecordsReducer.saveLab(s.copy(labDraft = draft), now, zone)
        if (applied.labs.size != s.labs.size + 1) return ask("검사 값을 저장할 수 없어요. 수치와 시각을 확인해 주세요.")
        val new = applied.labs.first { l -> s.labs.none { it.id == l.id } }
        if (s.labs.any { it.collectedAt == new.collectedAt && it.analytes.toSet() == new.analytes.toSet() }) return ask("같은 시각과 값의 검사 기록이 이미 있어요.")
        val next = s.copy(labs = applied.labs.map { if (it.id == new.id) it.copy(id = id) else it }, undo = null, toast = null)
        return ChatDecision("${at?.let { format(it, zone) } ?: "채혈 시각 모름"} · " + c.labs.joinToString(" · ") { "${it.analyte} ${it.value} ${it.unit}" }, next)
    }

    private fun mentions(text: String, token: String, isTime: Boolean): Boolean {
        val t = compact(token)
        if (t.isBlank()) return false
        val before = if (t.first().isDigit()) "(?<![0-9.])" else if (t.first() in 'a'..'z') "(?<![a-z])" else ""
        val after = if (isTime) "(?!\\s*(?:[0-9]+분|반))" else if (t.last().isDigit()) "(?![0-9.])" else if (t.last() in 'a'..'z') "(?![a-z])" else ""
        val pattern = token.trim().lowercase().split(Regex("\\s+")).joinToString("\\s*") { Regex.escape(it) }
        return Regex(before + pattern + after).containsMatchIn(text.lowercase())
    }
    private fun clauses(inputs: List<String>) = inputs.flatMap { it.split(Regex("[,;\n]|그리고|및|와|과")) }.filter { it.isNotBlank() }
    private fun needsOwnClause(s: AppState, c: ChatCommand, inputs: List<String>): Boolean {
        val text = compact(inputs.joinToString("\n"))
        return drugNames(s).distinct().count { text.contains(compact(it)) } > 1 ||
            clauses(inputs).count { compact(it).contains(compact(c.drug)) } > 1
    }
    private fun boundInOwnClause(c: ChatCommand, inputs: List<String>): Boolean =
        clauses(inputs).filter { compact(it).contains(compact(c.drug)) }.any { clause ->
            listOf(c.amount to false, c.time to true, c.route to false).filter { it.first.isNotBlank() }
                .all { (token, isTime) -> mentions(clause, token, isTime) }
        }

    private fun resolveTime(date: String, time: String, now: Instant, zone: ZoneId): Instant? {
        val moment = KoreanTime.moment(time, now) ?: return null
        val day = if (date.isBlank()) null else KoreanTime.day(date, now, zone)?.date ?: return null
        val local = when (moment) {
            is KoreanTime.Moment.Elapsed -> return moment.instant.takeIf { day == null || it.atZone(zone).toLocalDate() == day }
            is KoreanTime.Moment.At -> day?.atTime(moment.time) ?: return null
            KoreanTime.Moment.Ambiguous -> return null
        }
        return local.takeIf { zone.rules.getValidOffsets(it).size == 1 }?.atZone(zone)?.toInstant()
    }

    private fun format(at: Instant, zone: ZoneId) = at.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    private fun recent(s: AppState, zone: ZoneId): String {
        val entries = s.doses.map { it.occurredAt to "${format(it.occurredAt, zone)} · ${doseSummary(it.drug, it.route, it.amountEntered, it.enteredUnit)}" } +
            s.labs.map { (it.collectedAt ?: Instant.MIN) to "${it.collectedAt?.let { at -> format(at, zone) } ?: "시각 모름"} · " + it.analytes.joinToString(" · ") { v -> "${v.analyte} ${v.reportedValue} ${v.reportedUnit}" } }
        return entries.sortedByDescending { it.first }.take(10).joinToString("\n") { it.second }.ifBlank { "아직 투약·검사 기록이 없어요." }
    }
}
