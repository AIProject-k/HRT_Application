# 휴대폰 Claude 설치·연결 절차

호르몬로그의 설치기 소스와 재현 절차다. **같은 휴대폰의 다른 앱은 Ubuntu·Claude 설치와 공식 CLI 로그인만 재사용**한다. 각 앱의 프롬프트·스키마·기록·작업 폴더·응답 수신기는 별도로 둔다. 다른 앱 연결을 실제로 배포한 상태는 아니다.

전체 사용자 설치 순서는 [설치 가이드와 검증](../../docs/ChatInstaller_2026-10-19.md), 다른 앱의 연결 계약은 [동일 기기 재사용](../../docs/ClaudeSamePhoneIntegration.md)을 따른다.

## 1. 앱 설치 후 준비

1. 첫 실행 **채팅 설치 가이드 → 채팅 설치 시작**.
2. 공식 Termux 파일 다운로드·SHA-256 확인 후 Android 설치 승인을 진행한다. 설치 출처 허용·Play Protect 안내는 시스템이 제공하며 앱이 대신 승인하지 않는다.
3. 앱의 Termux 명령 실행 권한을 승인한다.
4. **준비 명령 복사하고 Termux 열기**. Termux 초기화 후 입력창을 길게 눌러 Paste → Enter. 준비 명령은 외부 앱 실행 허용을 켜고 호르몬로그로 돌아온다.
5. 최초 한 번의 준비 이후 아래 설치/검사는 앱이 이어서 수행한다. 원치 않으면 건너뛰어 기록 기능으로 진행한다.

## 2. 자동 설치 순서

| 단계 | 실행·검사 |
| --- | --- |
| Termux 의존성 | Python·PRoot-Distro·curl 확인. 필요한 경우 pkg update/upgrade 후 설치하여 기본 OpenSSL과 최신 curl의 호환성을 맞춤 |
| Ubuntu | 기존 ubuntu:24.04 + hrt/Claude 환경이면 재사용. 없으면 전용 hormonelog-ubuntu24에 공식 Ubuntu Base 24.04.5 파일을 해시 확인 후 설치 |
| Linux 준비 | OS ID/버전 확인, ca-certificates·curl·python3·passwd 설치, hrt 사용자·작업 폴더 생성 |
| Claude | 기존 버전 검사. 다르면 공식 설치 스크립트 해시 확인 후 2.1.292 명시 설치. 바이너리 버전 재확인 |
| 앱 연결 파일 | 고정 실행기·프롬프트·JSON 스키마·로그인 보조 배치. 실제 파일의 SHA-256과 배치 ID 검사 |
| 공통 환경 등록 | Termux의 ~/.local/share/claude-phone/runtime.json에 설치 경로·버전만 저장. 인증/앱 데이터 미포함 |
| 로그인 | 공식 claude auth login --claudeai. CLI가 생성한 공식 URL만 Chrome/기본 브라우저로 열기 |
| 코드 대체 경로 | 공식 localhost 콜백을 우선. 복사 후 앱 복귀 시 현재 OAuth state와 일치하는 code#state만 전달. 직접 입력도 지원 |
| 완료 | loggedIn=true, authMethod=claude.ai, 버전·배치 정상, 실제 최근 기록 의도 요청 통과 후 Claude 채팅 활성화 |

자동 로그인은 **로그인 절차 시작·공식 화면 열기·완료 감지·일치하는 코드 전달**을 뜻한다. 계정 비밀번호 입력·MFA·연결 승인은 사용자가 공식 화면에서 수행한다. 인증 파일을 다른 앱/PC로 복사하지 않는다.

## 3. 고정 버전·공급처

| 항목 | 버전·URL | 고정 SHA-256 |
| --- | --- | --- |
| Termux ARM64 | [0.118.3 공식 배포](https://github.com/termux/termux-app/releases/tag/v0.118.3), termux-app_v0.118.3+github-debug_arm64-v8a.apk | 72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e |
| Termux x86_64 | 같은 배포의 termux-app_v0.118.3+github-debug_x86_64.apk | 3550e61f4d9eb49b712fd1bd9519dc37085a4d8eb597c57a340f0a64859b7144 |
| Ubuntu ARM64 | [ubuntu-base-24.04.5-base-arm64.tar.gz](https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz) | a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2 |
| Ubuntu AMD64 | [ubuntu-base-24.04.5-base-amd64.tar.gz](https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz) | e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86 |
| Claude 공식 설치기 | https://claude.ai/install.sh, 인자 2.1.292 | 3a68d3406cf674e17bed1733a4dcf37805e2e47d87417700007d7e1aa766a944 |

해시가 바뀌면 실패로 처리한다. 공식 배포 변경을 확인한 뒤 소스의 버전/해시를 함께 갱신한다. 자동으로 검증을 끄거나 다른 버전으로 바꾸지 않는다. PRoot-Distro는 Termux 저장소 패키지를 사용하며 이번 기기 검증 버전은 5.10.0이다.

## 4. 파일과 기록 위치

Termux `$HOME`은 `/data/data/com.termux/files/home`, `$PREFIX`는 `/data/data/com.termux/files/usr`다.

| 위치 | 의미 |
| --- | --- |
| ~/.local/share/hormonelog-chat/events.jsonl | 누적 설치 단계·UTC 시각·허용된 오류 코드. 인증·입력 대화 미포함 |
| ~/.local/share/hormonelog-chat/progress.json | 마지막 단계·오류 코드 |
| ~/.local/share/hormonelog-chat/container | 재사용 또는 신규 설치한 배포판 이름 |
| ~/.local/share/hormonelog-chat/install.log | 패키지/설치기의 상세 출력. 로그인 transcript는 기록하지 않음. 외부 공유 전 내용 검토 필요 |
| ~/hrt-setup-packages.log | 최초 Termux 패키지 준비 출력 |
| ~/.local/share/claude-phone/runtime.json | 동일 기기 앱이 재사용할 환경 경로·버전의 발견 정보. 현재 연결 성공 여부를 보장하는 파일은 아님 |
| $PREFIX/bin/hrt-chat | 호르몬로그 전용 실행기 |
| /home/hrt/.local/bin/claude | Ubuntu 내부 공통 Claude 실행 파일 |
| /home/hrt/chat | 호르몬로그 전용 프롬프트·스키마·로그인 보조 |
| /home/hrt/work | 호르몬로그 기존 작업 폴더 |

설치기 관련 파일은 기기 Termux 내부에 둔다. 배포 가능한 소스·절차·검증 요약만 저장소에 남기며 OAuth 토큰/인증 코드/원본 치료 기록을 저장소나 문서에 넣지 않는다. events.jsonl의 과거 단계는 구현 이후의 실행부터 저장되며 이전 실행을 역으로 만들어 넣지 않는다.

## 5. 상태 확인·재시도

앱의 **채팅 설치·로그인 가이드 → 설치·연결 다시 시도**가 기본 경로다. 이미 배치된 환경은 Termux에서 다음처럼 확인할 수도 있다.

```sh
python "$HOME/.local/share/hormonelog-chat/setup.py" probe
cat "$HOME/.local/share/hormonelog-chat/events.jsonl"
cat "$HOME/.local/share/claude-phone/runtime.json"
```

probe 결과에는 진행 중 로그인 URL이 들어갈 수 있어 통째로 외부 전송하지 않는다. 문서/공유에는 URL을 제외한 버전·authMethod·단계·오류 코드만 남긴다. 상세 로그를 공유할 때도 계정·기기·개인정보를 검토한다.

| 증상 | 해결과 확인 |
| --- | --- |
| 최초 명령 응답 없음 | Termux 초기화, RUN_COMMAND 권한, allow-external-apps=true를 확인하고 준비 명령 실행 |
| curl OpenSSL 심볼 오류 | 최초 설치기가 기본 패키지 upgrade 후 재시도. 기존 사용자 환경의 다른 패키지가 바뀔 수 있으므로 초기 설치 안내에 포함 |
| Docker 레이어 TLS 오류 | 검증된 Ubuntu 공식 Base 파일 설치 경로 사용. TLS 확인은 유지 |
| 설치기 해시 불일치 | 중단. 공식 파일 변경을 확인하고 핀을 갱신하기 전까지 실행하지 않음 |
| 로그인 페이지 미표시 | Chrome 최초 안내 완료 후 공식 화면 다시 열기. claude.com/cai/oauth/authorize 및 터미널 OSC 링크 지원 확인 |
| 로그인 코드 붙여넣기 어려움 | 복사 후 앱 복귀 또는 앱의 인증 코드 입력. 이번 로그인 state와 다른 클립보드는 자동 전달하지 않음 |
| USAGE_LIMIT | 설치·로그인은 보존. 한도 초기화 후 앱에서 다시 연결 검사 |
| 버전/배치 변경 | 실제 설치와 배치 파일을 다시 검사. 다른 앱의 준비 완료 플래그만 믿고 켜지 않음 |

## 6. 검증 실행

```powershell
$env:JAVA_HOME='C:/Program Files/Zulu/zulu-17'
$env:ANDROID_HOME='C:/Users/user/AppData/Local/Android/Sdk'
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

`test_setup_history.py`는 **Termux Python**에서 설치기 파일 경로를 인자로 받아 실행하며 자체 임시 폴더만 사용한다. `ChatSetupDeviceTest`는 전용 에뮬레이터의 스킵/거절 검사와 명시적으로 허용된 실제 기기의 기존 환경 검사로 분리한다. `AppFlowTest`의 wipeAppData 검사는 개인 폰에서 실행하지 않는다. 최신 결과는 [설치 검증 문서](../../docs/ChatInstaller_2026-10-19.md)에 기록한다.
