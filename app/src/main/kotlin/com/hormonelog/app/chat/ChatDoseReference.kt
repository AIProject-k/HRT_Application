package com.hormonelog.app.chat

import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.*

/** Only an explicit follow-up may reuse the one dose saved in this conversation. */
object ChatDoseReference {
    fun requested(text: String): Boolean {
        val s = text.replace(Regex("\\s+"), "")
        return Regex("같은약|그약|그거|동일한약|어제도|그제도|오늘도").containsMatchIn(s) &&
            Regex("먹었|먹엇|복용했|복용|맞았|기록|등록|추가").containsMatchIn(s) &&
            !Regex("안먹|안맞|먹지|맞지|하지마|하지말|취소|먹어도|맞아도|예정|내일").containsMatchIn(s)
    }

    fun find(state: AppState, id: String?, revision: Int): DoseEvent? =
        state.doses.singleOrNull { it.id.toString() == id && it.revision == revision && it.enteredUnit == DoseUnit.MG && it.status.wasTaken }

    private fun drug(d: DoseEvent) = if (d.drug == Drug.PROGESTERONE_MICRONIZED && d.p4Context?.product == P4Product.UTROGESTAN_SOFT_CAPSULE)
        "유트로게스탄" else d.drug.label

    fun apply(c: ChatCommand, d: DoseEvent): ChatCommand = c.copy(
        drug = c.drug.ifBlank { drug(d) }, route = c.route.ifBlank { d.route.label },
        amount = c.amount.ifBlank { "${plainNumber(d.amountEntered)}mg" })

    fun evidence(d: DoseEvent) = "${drug(d)} ${plainNumber(d.amountEntered)}mg ${d.route.label}"
}
