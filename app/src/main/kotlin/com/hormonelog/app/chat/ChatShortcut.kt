package com.hormonelog.app.chat

/** Preloaded read-only commands, separate from the user's learned dose recipes. */
enum class ChatShortcut(val label: String, val action: String, val description: String) {
    STATUS("현재 상태", "status", "현재 추정·실측 검사·최근 투약·다음 일정 조회"),
    LABS("최근 검사", "lab_results", "저장된 실측 검사값·단위·채혈 날짜 조회"),
    INJECTION("다음 주사", "next_injection", "등록된 다음 주사 일정 조회"),
    ;

    companion object {
        fun find(text: String): ChatShortcut? = entries.firstOrNull { compact(it.label) == compact(text) }
        private fun compact(text: String) = text.filterNot { it.isWhitespace() }
    }
}
