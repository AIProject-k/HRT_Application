package com.hormonelog.app.ai

/** Which step of the offline model failed. Each one has its own recovery text; none of them saves a record. */
enum class LocalAiStage(val message: String) {
    LOAD("AI 모델을 불러오지 못했어요. 기록은 저장하지 않았어요. 모델을 다시 설치하거나 메모로 저장해 주세요."),
    INFERENCE("AI가 답을 만들지 못했어요. 기록은 저장하지 않았어요. 잠시 뒤 다시 보내거나 메모로 저장해 주세요."),
    RESPONSE("AI 응답을 기록 형식으로 읽지 못했어요. 기록은 저장하지 않았어요. 문장을 나누어 다시 보내 주세요."),
}

class LocalAiException(val stage: LocalAiStage, cause: Throwable) : IllegalStateException(stage.message, cause)
