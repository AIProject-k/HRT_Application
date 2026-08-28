package com.hormonelog.app.feature.common

import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.Telehealth

/** Korean display labels for domain enums, matching the design canvas wording. */

val Drug.label: String
    get() = when (this) {
        Drug.ESTRADIOL_VALERATE -> "에스트라디올 발레레이트"
        Drug.ESTRADIOL_TABLET -> "에스트라디올 정제"
        Drug.ESTRADIOL_PATCH -> "에스트라디올 패치"
        Drug.SPIRONOLACTONE -> "스피로노락톤"
        Drug.CYPROTERONE -> "사이프로테론"
    }

val Drug.isAntiandrogen: Boolean
    get() = this == Drug.SPIRONOLACTONE || this == Drug.CYPROTERONE

val Route.label: String
    get() = when (this) {
        Route.ORAL -> "경구"
        Route.SUBLINGUAL -> "설하"
        Route.PATCH -> "패치"
        Route.GEL -> "젤/크림"
        Route.IM_INJECTION -> "근육주사(IM)"
        Route.SC_INJECTION -> "피하주사(SC)"
    }

val DoseUnit.label: String
    get() = when (this) {
        DoseUnit.MG -> "mg"
        DoseUnit.MG_PER_DAY -> "mg/일"
        DoseUnit.PATCH -> "매(패치)"
    }

val Assay.label: String
    get() = when (this) {
        Assay.LC_MS_MS -> "LC-MS/MS"
        Assay.IMMUNOASSAY -> "면역측정"
        Assay.UNKNOWN -> "모름"
    }

val Assay.hint: String
    get() = when (this) {
        Assay.LC_MS_MS -> "질량분석. 가장 정확한 편이에요"
        Assay.IMMUNOASSAY -> "ECLIA·CLIA 등. 병원에서 가장 흔해요"
        Assay.UNKNOWN -> "검사지에 안 적혀 있으면 이걸 선택"
    }

/** Chip order used by the 투약 기록 sheet (matches the design's routeChips list). */
val DOSE_SHEET_DRUGS = listOf(
    Drug.ESTRADIOL_VALERATE,
    Drug.ESTRADIOL_TABLET,
    Drug.ESTRADIOL_PATCH,
    Drug.SPIRONOLACTONE,
    Drug.CYPROTERONE,
)

val DOSE_SHEET_ROUTES = listOf(
    Route.ORAL,
    Route.SUBLINGUAL,
    Route.PATCH,
    Route.GEL,
    Route.IM_INJECTION,
)

val DOSE_SHEET_UNITS = listOf(DoseUnit.MG, DoseUnit.MG_PER_DAY, DoseUnit.PATCH)

val LAB_METHODS = listOf(Assay.LC_MS_MS, Assay.IMMUNOASSAY, Assay.UNKNOWN)

val PrescriptionBasis.label: String
    get() = when (this) {
        PrescriptionBasis.INFORMED_CONSENT -> "정보동의서"
        PrescriptionBasis.DIAGNOSIS -> "진단서·소견서"
        PrescriptionBasis.REFERRAL -> "자문의뢰"
        PrescriptionBasis.UNKNOWN -> "모름"
    }

val Telehealth.label: String
    get() = when (this) {
        Telehealth.YES -> "비대면 가능"
        Telehealth.NO -> "대면만"
        Telehealth.UNKNOWN -> "모름"
    }

val PRESCRIPTION_BASES = listOf(
    PrescriptionBasis.INFORMED_CONSENT,
    PrescriptionBasis.DIAGNOSIS,
    PrescriptionBasis.REFERRAL,
    PrescriptionBasis.UNKNOWN,
)

val TELEHEALTH_OPTIONS = listOf(Telehealth.YES, Telehealth.NO, Telehealth.UNKNOWN)
