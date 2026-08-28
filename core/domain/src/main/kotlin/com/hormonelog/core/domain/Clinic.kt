package com.hormonelog.core.domain

import java.util.UUID

/**
 * A user-authored note about a clinic that provides HRT. This is the user's own
 * memo (e.g. copied from a community post), not app-provided or verified data.
 */
data class Clinic(
    val id: UUID,
    val name: String,
    val region: String = "",
    val prescriptionBasis: PrescriptionBasis = PrescriptionBasis.UNKNOWN,
    val telehealth: Telehealth = Telehealth.UNKNOWN,
    val priceNote: String = "",
    val memo: String = "",
    /** Where the info came from, e.g. an Arcalive post URL. */
    val sourceUrl: String = "",
)

/** What the clinic requires to prescribe. */
enum class PrescriptionBasis {
    INFORMED_CONSENT, // 정보동의서
    DIAGNOSIS, // 진단서 / 소견서
    REFERRAL, // 자문의뢰
    UNKNOWN,
}

enum class Telehealth {
    YES,
    NO,
    UNKNOWN,
}
