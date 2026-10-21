package com.hormonelog.app.feature.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.chat.ChatSetupController
import com.hormonelog.app.chat.ChatSetupStage
import com.hormonelog.app.platform.TermuxChat
import com.hormonelog.app.ui.kit.*
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

@Composable
fun ChatSetupScreen(setup: ChatSetupController) {
    val ui = setup.ui
    var code by remember { mutableStateOf("") }
    var showTermuxGuide by remember { mutableStateOf(false) }
    val close = setup::close
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), setup::permissionResult)
    LaunchedEffect(ui.stage) { if (ui.stage == ChatSetupStage.PERMISSION) permission.launch(TermuxChat.PERMISSION) }
    BackHandler { close() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        HlTopBar("채팅 설치 가이드", close)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HlText("채팅을 선택해서 설치할 수 있어요", size = HlSize.t22)
            HlText("설치하지 않아도 투약·검사 기록과 기본 조회 단축어를 사용할 수 있어요. Claude 채팅은 실행·구독 연결 확인이 끝나야 켜져요.", size = HlSize.t14)
            HlText("① Termux 0.118.3 설치 승인\n② 최초 명령 권한·연결 준비\n③ Ubuntu 24.04 자동 설치\n④ Claude Code 2.1.292 자동 설치·실행 검사\n⑤ 공식 구독 로그인·승인\n⑥ 완료 감지·앱 연결", size = HlSize.t14)
            HlText("안정적인 인터넷과 충분한 저장 공간(약 3GB 이상)을 준비해 주세요. 처음에는 수 분 이상 걸릴 수 있어요. Android 설치 승인과 Termux 최초 준비 명령 붙여넣기는 직접 진행해야 해요. 기존 환경·로그인은 가능한 재사용해요.", size = HlSize.t12, color = Hl.colors.muted)
            if (ui.message.isNotBlank()) HlText(ui.message, size = HlSize.t14, color = if (ui.stage == ChatSetupStage.ERROR) Hl.colors.danger else Hl.colors.teal)
            when (ui.stage) {
                ChatSetupStage.INTRO, ChatSetupStage.ERROR -> {
                    HlButton(if (ui.stage == ChatSetupStage.ERROR) "설치·연결 다시 시도" else "채팅 설치 시작", setup::start)
                    if (ui.stage == ChatSetupStage.ERROR) HlButton("앱 권한 설정", setup::openAppSettings, kind = HlButtonKind.Secondary)
                }
                ChatSetupStage.APK -> HlButton("Termux 설치 창 열기", setup::installApk)
                ChatSetupStage.PERMISSION -> {
                    HlText("호르몬로그가 Termux에서 설치·연결 명령을 실행할 수 있도록 Android 권한 창에서 허용을 선택해 주세요. 권한 창이 보이지 않으면 아래 설정 방법을 펼쳐 보세요.", size = HlSize.t14)
                    HlButton("명령 실행 권한 허용", { permission.launch(TermuxChat.PERMISSION) })
                }
                ChatSetupStage.BOOTSTRAP -> {
                    HlText("① 아래 버튼으로 준비 명령을 복사하고 Termux를 여세요.\n② 첫 실행의 Installing… 안내가 사라지고 입력 커서($)가 나올 때까지 기다리세요.\n③ 검은 화면의 입력 부분을 길게 눌러 Paste(붙여넣기)를 선택하고 키보드 Enter를 누르세요.\n④ 앱으로 돌아오면 설치가 이어져요. 자동으로 돌아오지 않으면 최근 앱에서 호르몬로그를 열고 ‘준비 완료 · 이어서 설치’를 누르세요.", size = HlSize.t14)
                    HlButton("준비 명령 복사하고 Termux 열기", setup::prepareTermux)
                    HlButton("준비 완료 · 이어서 설치", setup::start, kind = HlButtonKind.Secondary)
                }
                ChatSetupStage.LOGIN -> {
                    HlText("공식 화면에서 구독 계정으로 로그인·연결 승인을 진행해 주세요. 브라우저가 자동으로 돌아오면 코드 입력 없이 연결돼요. 코드가 표시되면 복사 후 앱으로 돌아오세요. 이번 로그인에 일치하는 코드만 자동 전달해요. 자동 인식이 안 되면 아래에 붙여넣어 주세요.", size = HlSize.t14)
                    HlText("Chrome 첫 실행 안내가 나오면 안내를 마친 뒤 로그인해 주세요.", size = HlSize.t12, color = Hl.colors.muted)
                    if (ui.loginUrl != null) HlButton("공식 로그인 화면 다시 열기", setup::reopenLogin)
                    HlTextField(code, { code = it.take(4096) }, label = "인증 코드 (자동 연결이 안 될 때만)", keyboard = KeyboardType.Password, password = true)
                    HlButton("코드 전달", { setup.submitCode(code); code = "" }, enabled = code.isNotBlank())
                    HlText("코드·인증 파일은 앱에 저장하지 않아요. 로그인은 Claude 구독 방식만 확인해요. 완료 후 연결 확인 요청 1회가 구독 사용량을 사용해요.", size = HlSize.t12, color = Hl.colors.muted)
                }
                ChatSetupStage.READY -> HlButton("채팅 사용하기", setup::done)
                else -> HlText("설치·검사가 진행 중이에요. 앱으로 돌아오면 상태를 다시 확인해요.", size = HlSize.t14)
            }
            HlTextAction(if (showTermuxGuide) "Termux 설정 방법 접기" else "Termux 설치·초기화·권한 설정 방법", { showTermuxGuide = !showTermuxGuide })
            if (showTermuxGuide) {
                HlText("1. Termux 설치 승인", size = HlSize.t16)
                HlText("‘채팅 설치 시작’을 누르면 앱이 공식 Termux 파일을 내려받고 확인해요. Android가 출처 허용을 요청하면 호르몬로그의 ‘이 출처 허용’을 켠 뒤 뒤로 돌아와 설치를 승인하세요. 설치 창이 닫혔다면 ‘Termux 설치 창 열기’를 누르세요. 시스템에서 설치가 차단되면 보호 설정을 끄지 말고 건너뛰거나 안내 내용을 확인해 주세요.", size = HlSize.t14)
                HlText("2. Termux 첫 실행·초기화", size = HlSize.t16)
                HlText("설치가 끝나면 Termux를 한 번 여세요. 검은 화면의 Installing… 같은 초기화 안내가 끝나고 $ 표시 옆에서 입력할 수 있을 때까지 기다리세요. 이때는 직접 명령을 입력하지 않아도 돼요. 최근 앱으로 호르몬로그에 돌아와 설치를 계속하세요.", size = HlSize.t14)
                HlText("3. 호르몬로그의 명령 실행 권한", size = HlSize.t16)
                HlText("권한 창에서 허용을 선택하세요. 거절했거나 창이 보이지 않으면 ‘앱 권한 설정’ → 권한 → 추가 권한(또는 기타 권한) → ‘Termux 환경에서 명령 실행’을 허용한 뒤 돌아와 ‘설치·연결 다시 시도’를 누르세요. 영어로는 Run commands in Termux environment예요. 기기에 따라 메뉴 이름이 다를 수 있어요. 런처를 위장했다면 앱 이름이 ‘메모’로 보일 수 있어요.", size = HlSize.t14)
                HlButton("앱 권한 설정 열기", setup::openAppSettings, kind = HlButtonKind.Secondary)
                HlText("4. 최초 준비 명령 붙여넣기", size = HlSize.t16)
                HlText("앱이 최초 연결 준비를 안내하면 ‘준비 명령 복사하고 Termux 열기’를 누르세요. Termux 입력 부분을 길게 눌러 Paste(붙여넣기) → Enter 순서로 진행해요. 이 명령이 Termux 쪽의 외부 앱 실행 허용을 설정해요. 붙여넣기 메뉴가 안 나오면 Termux에서 키보드를 띄우고 다시 길게 누르세요. 복사가 풀렸다면 앱으로 돌아와 버튼을 다시 누르세요.", size = HlSize.t14)
                HlText("5. 돌아와서 자동 설치 계속", size = HlSize.t16)
                HlText("자동으로 돌아오지 않으면 최근 앱에서 호르몬로그를 열고 ‘준비 완료 · 이어서 설치’를 누르세요. Ubuntu·Claude 설치 중에는 기다려 주세요. 완료 후 열린 공식 화면에서 구독 로그인·승인을 하고, 앱에 ‘구독 연결 확인 완료’가 표시되면 끝이에요. 이미 준비된 기기는 필요한 단계만 진행해요.", size = HlSize.t14)
                HlText("설정이 어렵거나 원치 않으면 ‘건너뛰고 기록 사용’을 선택하세요. 나중에 채팅 화면의 ‘채팅 설치·로그인 가이드’에서 다시 시작할 수 있어요.", size = HlSize.t12, color = Hl.colors.muted)
            }
            HlTextAction(if (ui.busy) "나중에 사용 · 진행 중인 설치는 계속돼요" else if (ui.stage == ChatSetupStage.READY) "채팅 끄고 기록 사용" else "건너뛰고 기록 사용", setup::skip)
        }
    }
}
