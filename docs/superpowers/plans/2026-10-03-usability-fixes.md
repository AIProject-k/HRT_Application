# 사용자 사용성 개선 구현 계획

**Goal:** UXReview_2026-10-03.md에 보고한 여섯 가지 오류와 혼동을 해결한다.

**Architecture:** 기존 DashboardReducer와 표시 헬퍼를 유지한다. 저장 형식과 모델 엔진을 바꾸지 않고 입력·표시·화면 연결을 수정한다.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, Android Compose UI tests.

사용자가 보고서를 확인하고 구현을 요청한 범위에 한정한다. 기존 미커밋 날짜·시간 선택 수정은 그대로 유지한다.

## 1. 검사값과 단위 표시

- [x] DashboardUsabilityTest: pmol/L 원래 값 표시와 그래프 환산값 표시, 단위 미상 결과 제외를 검증한다.
- [x] DashboardView.kt: labSubtitle는 LabAnalyteValue.reportedValue와 reportedUnit을 함께 사용한다. canonical은 canonicalValue 또는 알려진 단위 변환 결과를 반환한다.
- [x] nearestLabWithin은 canonical이 존재하는 검사만 선택한다.

## 2. 검사값 검증

- [x] DashboardUsabilityTest: 소수점만 입력, 중복 소수점, 음수·비유한값, 유효한 값과 오류값의 혼합 입력을 검증한다.
- [x] LabDraft: e2Error와 ttError를 추가한다. 입력된 모든 값이 유한한 0 이상의 숫자이고 하나 이상 입력되면 canSave=true다.
- [x] pressKey: 두 번째 소수점을 무시하고 첫 소수점은 0.으로 시작한다.
- [x] LabSheet: 오류를 필드 아래 표시하고 저장 버튼에 오류 수정을 안내한다.

## 3. 날짜별 묶음

- [x] DashboardUsabilityTest: 연도가 다른 같은 월·일의 투약·검사 기록이 분리되는지 검증한다.
- [x] DashboardView.kt: dateKey에 연도·월·일을 모두 포함한다. 시간 미상 그룹은 유지한다.

## 4. 다음 투약

- [x] DashboardUsabilityTest: 일정 미설정, 약물·경로 일치, 복수 일정, 과거·당일·미래 일정, 시작 전·종료된 일정을 검증한다.
- [x] homeSummary: 활성 에스트로겐 일정마다 같은 약물·경로의 실제 투약 기록을 찾고 해당 시각 + 간격으로 계산한다. 기록이 없으면 일정 시작 시각을 사용한다.
- [x] 일정 시작 이전 기록·미래 기록·누락 기록은 계산에서 제외한다. 종료 이후의 후보는 제외하고 여러 후보 중 가장 이른 시각을 표시한다.
- [x] 현지 날짜 차이를 이용해 N일 뒤, 오늘, 예정 시각 지남, N일 지남을 구분한다. 일정 없으면 일정 미설정, 남은 후보 없으면 남은 일정 없음이다.

## 5. 채혈 이전 마지막 투약

- [x] DashboardFlowTest: 과거 채혈 앞뒤의 투약 기록을 넣고 채혈 이전의 기록으로 안내하는지 검증한다.
- [x] DashboardScreen.kt: LabSheet에 넘기는 lastDose는 채혈 시각 이전의 마지막 실제 에스트로겐 투약으로 찾는다. 시각 미상이거나 이전 기록이 없으면 null이다.

## 6. 내보내기 진입점

- [x] MeScreen.kt: 클릭되지 않는 상단 데이터 내보내기 행을 삭제하고 기존 CSV 내보내기 버튼을 유지한다.
- [x] DashboardFlowTest: 내 정보 화면에 중복 내보내기 행이 없고 CSV 버튼은 있는지 검증한다.

## 검증 명령

PowerShell에서 JAVA_HOME=C:\Program Files\Zulu\zulu-17, ANDROID_HOME=C:\Users\user\AppData\Local\Android\Sdk로 지정한다.

1. `./gradlew.bat :app:testDebugUnitTest --tests com.hormonelog.app.feature.dashboard.DashboardUsabilityTest --console=plain`으로 수정 전 실패를 확인한다.
2. `./gradlew.bat testDebugUnitTest :app:assembleDebug --console=plain`으로 전체 단위 테스트와 APK 빌드를 확인한다.
3. 전용 에뮬레이터를 시작하고 ANDROID_SERIAL을 지정한 뒤 `./gradlew.bat :app:connectedDebugAndroidTest --console=plain`으로 화면 테스트를 검증한다. 개인 기기에서는 테스트 실행하지 않는다.
4. `git diff --check`와 최종 diff 검토 후 보고서에 결과를 추가한다. 연결 기기에는 최종 APK만 `adb install -r`로 갱신한다.
