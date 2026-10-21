package com.hormonelog.app.chat

import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** User-approved definitions are data; they never become system prompts or shell commands. */
object ChatMemoryActions {
    val actions = setOf("remember_alias", "remember_doses", "update_alias", "update_doses", "memory_forget", "memory_list", "shortcut")
    private fun key(value: String) = value.lowercase().filterNot { it.isWhitespace() }
    private fun mentioned(inputs: List<String>, value: String): Boolean {
        if (value.isBlank()) return false
        val k = key(value)
        return inputs.any { input ->
            if (k.any { it in 'a'..'z' }) Regex("(?<![a-z0-9])${Regex.escape(k)}(?![a-z0-9])").containsMatchIn(key(input))
            else key(input).contains(k)
        }
    }
    private fun find(s: AppState, name: String) = s.chatMemories.firstOrNull { key(it.name) == key(name) }

    fun list(s: AppState, name: String = ""): String {
        if (name.isBlank()) {
            val defaults = "기본 단축어 · 연결 없이 바로 조회\n" + ChatShortcut.entries.joinToString("\n") { "${it.label} — ${it.description}" }
            val personal = if (s.chatMemories.isEmpty()) "내 단축어는 아직 없어요. ‘내프로는 유트로게스탄이라고 기억해줘’처럼 알려주세요."
                else s.chatMemories.joinToString("\n\n") { describe(it) } + "\n\n바꾸려면 ‘이름을 …로 바꿔줘’, 지우려면 ‘이름 기억 지워줘’라고 해주세요."
            return "$defaults\n\n내 단축어 ${s.chatMemories.size}개\n$personal"
        }
        if (find(s, name) == null) ChatShortcut.find(name)?.let { return "기본 단축어\n${it.label} — ${it.description}\n이름을 입력하거나 버튼을 누르면 바로 조회해요." }
        val selected = listOfNotNull(find(s, name))
        if (selected.isEmpty()) return "기억한 단축어가 없어요. ‘내프로는 유트로게스탄이라고 기억해줘’처럼 알려주세요."
        return selected.joinToString("\n\n") { describe(it) } + "\n\n바꾸려면 ‘이름을 …로 바꿔줘’, 지우려면 ‘이름 기억 지워줘’라고 해주세요."
    }

    fun apply(s: AppState, c: ChatCommand, inputs: List<String>, id: UUID, now: Instant, zone: ZoneId): ChatDecision {
        fun ask(message: String) = ChatDecision(message)
        if (c.action == "memory_list") return ask(list(s, c.memoryName))
        val name = c.memoryName.trim()
        if (!mentioned(inputs, name)) return ask("기억할 이름을 직접 알려주세요. 예: ‘밤약’.")
        if (c.action == "shortcut") return invoke(s, c, inputs, id, now, zone)
        val text = inputs.joinToString("\n")
        if (c.action == "memory_forget") {
            if (Regex("삭제하지마|지우지마|잊지마|삭제하지말|지우지말|잊지말").containsMatchIn(key(text))) return ask("기억을 지우지 않았어요.")
            if (!Regex("지워|삭제|잊어|기억하지마").containsMatchIn(key(text))) return ask("기억을 지우려면 ‘${name} 기억 지워줘’라고 해주세요.")
            if (find(s, name) == null) return ask("‘$name’으로 기억한 내용이 없어요.")
            return ChatDecision("‘$name’ 기억을 지웠어요. 실제 투약 기록은 그대로예요.", s.copy(chatMemories = s.chatMemories.filterNot { key(it.name) == key(name) }, undo = null, toast = null))
        }
        if (c.action !in actions) return ask("기억 추가·목록·변경·삭제를 지원해요.")
        val updating = c.action.startsWith("update_")
        val explicitUpdate = Regex("바꿔|바꾸|변경|수정|대신").containsMatchIn(text)
        if (updating && !explicitUpdate) return ask("기억을 바꾸려면 ‘$name 을 …로 바꿔줘’라고 해주세요.")
        if (!updating && !Regex("기억|외워|단축|별명|약칭|줄여|부를|부르").containsMatchIn(text)) return ask("사용할 이름과 함께 ‘기억해줘’라고 알려주세요.")
        if (Regex("하지마|하지말|취소").containsMatchIn(key(text))) return ask("기억을 변경하지 않았어요.")
        if (ChatCommands.reservedName(name) && (ChatShortcut.find(name) == null || find(s, name) == null)) return ask("‘$name’은 이미 앱에서 쓰는 약명이나 명령이에요. 다른 기억 이름을 정해 주세요.")
        if (find(s, name) == null && s.chatMemories.size >= 100) return ask("기억은 100개까지 저장할 수 있어요. 사용하지 않는 기억을 먼저 지워주세요.")
        val memory = if (c.action.endsWith("_alias")) {
            if (!mentioned(inputs, c.memoryTarget)) return ask("‘$name’이 어떤 제품·성분을 뜻하는지 알려주세요.")
            val target = ChatCommands.knownDrug(s, c.memoryTarget) ?: return ask("앱에서 지원하는 제품·성분에 연결해 주세요.")
            runCatching { ChatMemory.Alias(name, target.drug, target.product) }.getOrElse { return ask("기억 이름은 32자 이하의 글자·숫자·공백으로 정해 주세요.") }
        } else {
            val items = c.doses.ifEmpty { if (c.drug.isNotBlank() || c.amount.isNotBlank() || c.route.isNotBlank()) listOf(c.copy(action = "dose", doses = emptyList())) else emptyList() }
            if (items.size !in 1..5) return ask("‘$name’으로 기록할 약·mg 용량·경로를 알려주세요. 조합은 1~5개까지 기억해요.")
            val existing = (find(s, name) as? ChatMemory.Shortcut).takeIf { updating || explicitUpdate }
            val replaceAll = Regex("전부|전체|통째|만으로").containsMatchIn(text)
            val doses = mutableListOf<ChatMemory.Dose>()
            for (item in items) {
                val prior = if (existing != null && !replaceAll) {
                    if (item.drug.isBlank()) existing.doses.singleOrNull()
                    else existing.doses.firstOrNull { it.drug == ChatCommands.knownDrug(s, item.drug)?.drug }
                } else null
                if (existing != null && !replaceAll && item.drug.isBlank() && prior == null) return ask("‘$name’에는 약이 여러 개예요. 어느 약의 용량을 바꿀지 알려주세요.")
                val oldDefinition = prior?.let { d -> listOfNotNull(
                    drugToken(d).takeIf { item.drug.isBlank() }, d.route.label.takeIf { item.route.isBlank() },
                    "${plainNumber(d.amountMg)}mg".takeIf { item.amount.isBlank() },
                ).joinToString(" ") }
                val merged = item.copy(action = "dose", date = "", time = "", usual = false,
                    drug = item.drug.ifBlank { prior?.let(::drugToken).orEmpty() },
                    route = item.route.ifBlank { prior?.route?.label.orEmpty() },
                    amount = item.amount.ifBlank { prior?.let { "${plainNumber(it.amountMg)}mg" }.orEmpty() })
                val result = ChatCommands.apply(AppState(chatMemories = s.chatMemories), merged,
                    inputs + listOfNotNull(oldDefinition), id, now, zone, mode = ChatInputMode.TEMPLATE)
                val dose = result.next?.doses?.singleOrNull() ?: return ask("‘$name’ 기억에 필요한 정보: ${result.message}")
                doses += ChatMemory.Dose(dose.drug, dose.route, dose.amountEntered, dose.p4Context?.product ?: P4Product.UNKNOWN)
            }
            if (doses.map { it.drug }.distinct().size != doses.size) return ask("같은 약이 두 번 들어 있어요. 실제 사용할 용량 한 항목으로 정리해 주세요.")
            val combined = if (existing != null && !replaceAll && !(existing.doses.size == 1 && doses.size == 1 && existing.doses.single().drug != doses.single().drug)) {
                val additions = doses.filter { d -> existing.doses.none { it.drug == d.drug } }
                if (additions.isNotEmpty() && !text.contains("추가")) return ask("새 약을 추가하려면 ‘추가해줘’, 구성을 바꾸려면 ‘전체를 …로 바꿔줘’라고 알려주세요.")
                existing.doses.map { d -> doses.firstOrNull { it.drug == d.drug } ?: d } + additions
            } else doses
            if (combined.size > 5) return ask("한 단축어에는 약을 5개까지 기억할 수 있어요.")
            runCatching { ChatMemory.Shortcut(name, combined) }.getOrElse { return ask("기억 이름은 32자 이하의 글자·숫자·공백으로 정해 주세요.") }
        }
        val old = find(s, name)
        if (old == memory) return ask("이미 같은 내용으로 기억하고 있어요.\n${describe(old)}")
        if (old != null && !explicitUpdate) return ask("‘$name’은 이미 기억하고 있어요. 바꾸려면 ‘$name 을 …로 바꿔줘’라고 해주세요.\n${describe(old)}")
        if (updating && old == null) return ask("‘$name’으로 기억한 내용이 없어요. 먼저 ‘기억해줘’라고 등록해 주세요.")
        val entries = s.chatMemories.filterNot { key(it.name) == key(name) } + memory
        return ChatDecision("기억했어요.\n${describe(memory)}" + if (memory is ChatMemory.Shortcut) "\n이 단축어만 보내면 입력 시각에 등록해요. 다른 날짜·시각도 지정할 수 있어요." else "\n이 약칭과 실제 용량·시각을 말씀해 주세요.",
            s.copy(chatMemories = entries, undo = null, toast = null))
    }

    private fun invoke(s: AppState, c: ChatCommand, inputs: List<String>, id: UUID, now: Instant, zone: ZoneId): ChatDecision {
        val memory = find(s, c.memoryName) ?: return ChatDecision("‘${c.memoryName}’은 아직 기억하지 못했어요. 사용할 약·용량·경로를 알려주세요.")
        if (inputs.none { ChatMemoryNames.mentioned(it, c.memoryName) }) return ChatDecision("단축어 이름을 다른 단어와 구분해서 알려주세요.")
        val extra = inputs.map { key(it).replace(key(c.memoryName), "") }
        if (extra.any { Regex("[?？]|뭐|무엇|보여|알려|목록|내용|기억|바꿔|변경|수정|삭제|지워|잊어|하지마|취소|먹어도|먹을까|먹어야|복용해도|예정").containsMatchIn(it) }) return ChatDecision(describe(memory))
        if (memory !is ChatMemory.Shortcut) return ChatDecision("${describe(memory)}\n실제 용량과 시각을 함께 알려주세요.")
        if (c.date.isBlank() && c.time.isBlank() && extra.any { Regex("어제|그제|내일|아까|지난|[0-9]+[시분]|[0-9]+:[0-9]+|[0-9]{4}-").containsMatchIn(it) })
            return ChatDecision("날짜·시각 표현이 있어 입력 시각으로 대신 기록하지 않았어요. 실제 날짜와 시각을 알려주세요.")
        val time = c.time.ifBlank { if (c.date.isBlank()) "방금" else "" }
        val items = memory.doses.map { d -> ChatCommand("dose", drug = drugToken(d),
            route = d.route.label, amount = "${plainNumber(d.amountMg)}mg", date = c.date, time = time) }
        val definition = items.joinToString(" · ") { "${it.drug} ${it.amount} ${it.route}" }
        val evidence = inputs + definition + if (c.time.isBlank() && c.date.isBlank()) listOf("방금") else emptyList()
        val expanded = if (items.size == 1) items.single() else ChatCommand("batch", date = c.date, time = time, doses = items)
        val result = ChatCommands.apply(s, expanded, evidence, id, now, zone, mode = ChatInputMode.SHORTCUT)
        val next = result.next ?: return result
        val products = memory.doses.associate { it.drug to it.product }
        val oldIds = s.doses.map { it.id }.toSet()
        return result.copy(message = "‘${memory.name}’\n${result.message}", next = next.copy(doses = next.doses.map { d ->
            if (d.id !in oldIds && d.drug == Drug.PROGESTERONE_MICRONIZED) d.copy(p4Context = (d.p4Context ?: P4Context()).copy(product = products[d.drug] ?: P4Product.UNKNOWN)) else d
        }))
    }

    private fun describe(memory: ChatMemory): String = when (memory) {
        is ChatMemory.Alias -> "${memory.name} → ${if (memory.product == P4Product.UTROGESTAN_SOFT_CAPSULE) "유트로게스탄" else memory.drug.label} (약칭)"
        is ChatMemory.Shortcut -> "${memory.name} → " + memory.doses.joinToString(" + ") {
            "${if (it.product == P4Product.UTROGESTAN_SOFT_CAPSULE) "유트로게스탄" else it.drug.label} ${plainNumber(it.amountMg)}mg ${it.route.label}"
        }
    }
    private fun drugToken(dose: ChatMemory.Dose) = if (dose.product == P4Product.UTROGESTAN_SOFT_CAPSULE) "유트로게스탄" else dose.drug.label
}
