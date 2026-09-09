package com.hormonelog.app.feature.dashboard

/**
 * Model-status summary shown on 홈 and 내 정보.
 *
 * The design assumed a working population curve; this build has no evidence-backed
 * model yet, so when [canEstimate] is false the status stays in a "준비 중" state.
 *
 * What calibration actually does is scale the height of the literature curve to match
 * measured values. It does not fit a personal PK model, so nothing here is phrased as
 * personalisation being complete, and there is no progress percentage — three labs is
 * not "100% done", it is just more evidence than one.
 */
data class CalibrationStatus(
    val title: String,
    val subtitle: String,
    val detail: String,
    val chip: String,
    val progressLabel: String,
    val nextStep: String,
    val steps: List<Step>,
) {
    data class Step(val index: Int, val done: Boolean, val active: Boolean, val title: String, val subtitle: String)

    companion object {
        private val STEP_TITLES = listOf("문헌 평균 곡선", "높이 보정됨", "높이 보정 안정화")
        private val STEP_SUBTITLES = listOf(
            "투약 기록 + 문헌 평균으로 흐름 표시",
            "적격 검사값 1건으로 곡선 높이 보정",
            "적격 검사값 2건 이상으로 곡선 높이 보정",
        )

        /**
         * @param includedLabs eligible E2 labs that actually drove the calibration
         * @param exposureScale the applied multiplier (1.0 = none)
         */
        fun of(includedLabs: Int, exposureScale: Double, canEstimate: Boolean): CalibrationStatus {
            val stage = when {
                includedLabs >= 2 -> 2
                includedLabs == 1 -> 1
                else -> 0
            }
            val progressLabel = if (includedLabs == 0) "보정에 쓴 검사 없음" else "보정에 쓴 검사 ${includedLabs}건"
            val steps = (0..2).map { i ->
                Step(
                    index = i,
                    done = canEstimate && i < stage,
                    active = canEstimate && i == stage,
                    title = STEP_TITLES[i],
                    subtitle = STEP_SUBTITLES[i],
                )
            }

            if (!canEstimate) {
                return CalibrationStatus(
                    title = "예상 곡선 준비 중",
                    subtitle = "지원되지 않는 경로예요",
                    detail = "이 경로는 아직 근거 자료로 검증된 예측 모델이 없어요. 기록한 투약과 검사값은 그대로 저장돼요.",
                    chip = "준비 중",
                    progressLabel = progressLabel,
                    nextStep = "근거 모델이 지원되는 경로로 기록하면 곡선이 나와요.",
                    steps = steps,
                )
            }

            val pctDelta = ((exposureScale - 1.0) * 100).toInt()
            val deltaText = when {
                pctDelta > 0 -> "예상 곡선을 약 ${pctDelta}% 상향 보정했어요"
                pctDelta < 0 -> "예상 곡선을 약 ${-pctDelta}% 하향 보정했어요"
                else -> "보정 없이 문헌 평균 그대로예요"
            }
            val (title, subtitle, detail) = when (stage) {
                0 -> Triple(
                    "문헌 평균 곡선",
                    "검사값을 기록하면 내 몸에 맞춰 가요",
                    "지금 곡선은 문헌에 보고된 인구집단 평균으로 그린 참고용 추정이에요. 임상 검증값이 아니고, 수집 시각이 있는 E2 검사값을 기록하면 그만큼 위아래로 보정됩니다.",
                )
                1 -> Triple(
                    "검사값으로 높이 보정됨",
                    deltaText,
                    "적격 검사값 1건으로 예상 곡선의 전체 높이(노출량)를 맞췄어요. 곡선의 모양(피크·트로프 타이밍)은 문헌 평균 그대로이고, 검사값이 2건 이상이면 보정이 더 안정적이에요.",
                )
                else -> Triple(
                    "검사값으로 높이 보정됨",
                    deltaText,
                    "적격 검사값 여러 건의 기하평균으로 예상 곡선의 높이를 보정했어요. 곡선 모양 자체를 개인 PK로 피팅하지는 않으므로, 개인화가 끝난 상태는 아니에요.",
                )
            }
            return CalibrationStatus(
                title = title,
                subtitle = subtitle,
                detail = detail,
                chip = title,
                progressLabel = progressLabel,
                nextStep = when (stage) {
                    0 -> "채혈 시각이 있는 E2 검사값을 1건만 기록해도 보정이 시작돼요."
                    1 -> "검사값을 1건 더 기록하면 보정이 안정화돼요."
                    else -> "검사값이 많을수록, 트로프·피크 등 다양한 시점일수록 보정이 정확해져요."
                },
                steps = steps,
            )
        }
    }
}
