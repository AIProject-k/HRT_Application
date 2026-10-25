package com.hormonelog.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hormonelog.app.chat.ChatSession
import com.hormonelog.app.chat.ChatShortcut
import com.hormonelog.app.ui.kit.*
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(chat: ChatSession, back: () -> Unit, setup: com.hormonelog.app.chat.LocalChatController? = null) {
    val ui = chat.ui
    val c = Hl.colors
    val scroll = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        snapshotFlow { Triple(scroll.isScrollInProgress, scroll.canScrollForward, autoScrolling) }
            .collect { (scrolling, canScrollForward, automatic) ->
                if (scrolling && !automatic) followLatest = !canScrollForward
            }
    }
    LaunchedEffect(ui.lines.lastOrNull()?.id) {
        if (ui.lines.isEmpty()) {
            followLatest = true
            scroll.scrollToItem(0)
        } else if (followLatest && !scroll.isScrollInProgress) {
            autoScrolling = true
            try { scroll.animateScrollToItem(ui.lines.size) } finally { autoScrolling = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        HlTopBar("채팅으로 기록", back, action = "새 대화", onAction = chat::reset)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), state = scroll,
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HlText("오프라인 기록 도우미 · ${setup?.spec?.label ?: "기본 내장 모델"}", size = HlSize.t14)
                    HlText("기본 조회·명확한 단일 투약은 AI 없이 처리해요. 복잡한 문장만 설치한 모델이 기기에서 해석해요. 대화·투약·검사 정보는 외부 AI 서버로 보내지 않아요. 최근 대화 200개와 작성 중인 입력은 기기에 보관해요.", size = HlSize.t12, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HlTextAction("단축어 목록 ${chat.memoryCount + ChatShortcut.entries.size}개", chat::listMemories)
                        ChatShortcut.entries.forEach { shortcut -> HlTextAction(shortcut.label, { chat.runShortcut(shortcut) }) }
                    }
                    if (setup != null) {
                        HlTextAction("오프라인 AI 모델 관리", setup::open)
                        HlText(if (ui.connected) "모델 설치됨 · 처음 사용할 때 불러와요" else "모델 미설치 · 기본 조회와 명확한 기록은 바로 사용해요", size = HlSize.t12, color = c.muted)
                    }
                }
            }
            if (ui.lines.isEmpty()) item(key = "help") {
                HlText("기본 단축어 ‘현재 상태’, ‘최근 검사’, ‘다음 주사’를 누르거나 그대로 입력해 보세요. 연결 없이 바로 조회해요. 조회 결과는 다음 모델 요청에 포함하지 않아요.\n\n실제로 투약한 내용과 검사 결과도 말씀해 주세요.\n예: 유트로게스탄 100mg 방금 복용\n\n‘내프로는 유트로게스탄이라고 기억해줘’처럼 약칭을 알려줄 수도 있어요. 투약 조합은 이름·약·실제 용량·경로와 함께 ‘기억해줘’라고 말씀해 주세요.", size = HlSize.t14, color = c.muted)
            }
            items(ui.lines, key = { "line:${it.id}" }) { line ->
                Column(Modifier.fillMaxWidth().background(if (line.user) c.input else c.card, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    HlText(if (line.user) "나" else "기록 도우미", size = HlSize.t12, color = c.muted)
                    SelectionContainer { HlText(line.text, size = HlSize.t14) }
                }
            }
            if (ui.busy) item(key = "busy") { HlText(if (ui.saving) "기기에 저장하고 있어요…" else "요청을 확인하고 있어요…", size = HlSize.t14, color = c.muted) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.conversationWarning?.let { HlText(it, size = HlSize.t12, color = c.danger) }
            if (ui.canUndo) HlButton("직전 채팅 등록 되돌리기", chat::undo, kind = HlButtonKind.Secondary, enabled = !ui.busy)
            if (ui.saveFailed) HlButton("저장 재시도", chat::retrySave, enabled = !ui.busy)
            if (ui.busy && !ui.saving) HlButton("응답 취소", chat::cancel, kind = HlButtonKind.Secondary)
            if (ui.pending && !ui.busy) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("오전", "오후", "오늘", "어제").forEach { choice -> HlButton(choice, { chat.send(choice) }, kind = HlButtonKind.Secondary) }
                    HlButton("이 요청 취소", chat::cancel, kind = HlButtonKind.Secondary)
                }
            }
            HlButton("메모로 저장", chat::saveMemo, kind = HlButtonKind.Secondary, enabled = !ui.busy && ui.input.isNotBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ChatComposer(ui.input, chat::editInput, Modifier.weight(1f))
                HlButton("보내기", chat::submit, enabled = chat.canSend(ui.input), minHeight = 52.dp, size = HlSize.t14)
            }
        }
    }
}
