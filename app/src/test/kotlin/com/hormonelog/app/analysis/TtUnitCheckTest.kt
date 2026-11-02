package com.hormonelog.app.analysis

import com.hormonelog.core.domain.TUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtUnitCheckTest {
    @Test fun aValueBelowWhatALabReportsAsksForNgPerMl() {
        assertEquals(TUnit.NG_ML, ttUnitWarning(0.1, TUnit.NG_DL, preHrtWithTestes = false)!!.swapTo)
    }

    @Test fun aPreHrtValueBelowTheModelsRangeAsksForTheUnitItFits() {
        assertEquals(TUnit.NG_ML, ttUnitWarning(7.74, TUnit.NG_DL, preHrtWithTestes = true)!!.swapTo)
        assertEquals(TUnit.NMOL_L, ttUnitWarning(18.0, TUnit.NG_DL, preHrtWithTestes = true)!!.swapTo)
        assertNull("on HRT a low value is expected", ttUnitWarning(7.74, TUnit.NG_DL, preHrtWithTestes = false))
    }

    @Test fun anImplausiblyHighValueAsksForNgPerDl() {
        assertEquals(TUnit.NG_DL, ttUnitWarning(520.0, TUnit.NG_ML, preHrtWithTestes = true)!!.swapTo)
        assertEquals(TUnit.NG_DL, ttUnitWarning(520.0, TUnit.NMOL_L, preHrtWithTestes = false)!!.swapTo)
    }

    @Test fun ordinaryValuesPassQuietly() {
        assertNull(ttUnitWarning(520.0, TUnit.NG_DL, preHrtWithTestes = true))
        assertNull(ttUnitWarning(5.2, TUnit.NG_ML, preHrtWithTestes = true))
        assertNull(ttUnitWarning(18.0, TUnit.NMOL_L, preHrtWithTestes = true))
        assertNull(ttUnitWarning(12.0, TUnit.NG_DL, preHrtWithTestes = false))
        assertNull(ttUnitWarning(null, TUnit.NG_DL, preHrtWithTestes = true))
    }
}
