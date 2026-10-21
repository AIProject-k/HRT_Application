package com.hormonelog.app.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hormonelog.app.chat.LocalChatController
import com.hormonelog.app.chat.ChatSetupStage
import com.hormonelog.app.ui.kit.*
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

@Composable
fun ChatSetupScreen(setup: LocalChatController) {
    val ui=setup.ui
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { setup.close() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HlTopBar("오프라인 AI 모델", setup::close)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            HlText("Qwen3-0.6B", size=HlSize.t22)
            HlText("기기에서 실행하는 기록 도우미예요. Termux·Claude 설치나 외부 AI 계정이 필요하지 않아요. 모델 설치 없이도 기본 조회와 명확한 단일 투약 기록을 사용할 수 있어요.", size=HlSize.t14)
            HlText("모델 파일 약 329 MiB · INT4 · CPU 실행\n설치할 때만 Hugging Face에서 파일을 받아요. 모바일 데이터가 사용될 수 있어요. 설치 후에는 인터넷 없이 동작해요. 파일 크기와 SHA-256을 검증한 뒤 사용해요.", size=HlSize.t14)
            HlText("첫 AI 응답은 모델을 불러오느라 시간이 걸릴 수 있어요. 실행 중에는 모델 파일 외에도 RAM·배터리를 사용해요. AI는 문장에서 기록 내용을 추출하며, 농도 계산과 저장 검증은 기존 앱이 처리해요.", size=HlSize.t12, color=Hl.colors.muted)
            if(ui.message.isNotBlank()) HlText(ui.message, size=HlSize.t14,
                color=if(ui.stage==ChatSetupStage.ERROR) Hl.colors.danger else Hl.colors.teal)
            if(ui.busy) {
                HlButton("설치 취소",setup::cancelInstall,kind=HlButtonKind.Secondary,enabled=ui.stage==ChatSetupStage.DOWNLOADING)
            } else {
                if(ui.stage==ChatSetupStage.READY) {
                    HlButton("채팅 사용하기",setup::done)
                    HlButton(if(confirmDelete) "모델 삭제 확인" else "AI 모델 삭제", {
                        if(confirmDelete) { setup.deleteModel(); confirmDelete=false } else confirmDelete=true
                    },kind=HlButtonKind.Secondary)
                    if(confirmDelete) HlTextAction("삭제 취소",{confirmDelete=false})
                } else HlButton(if(ui.stage==ChatSetupStage.ERROR) "모델 설치 재시도" else "오프라인 AI 다운로드",setup::start)
            }
            HlTextAction("나중에 · 기본 기록 사용",setup::skip)
            HlText("의약품의 용량·복용 계획을 상담하는 AI가 아니에요. 입력에 없는 시간·용량은 추측해서 저장하지 않아요.",size=HlSize.t12,color=Hl.colors.muted)
        }
    }
}
