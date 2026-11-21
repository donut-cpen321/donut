package com.donut.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentUsageTextTest {
    // Input: a new item, one used once, one used often, and one with half hours
    // Expected output: singular for one, plural otherwise, and whole hours without a decimal
    @Test
    fun showsGamesAndHours() {
        assertEquals("0 games used · 0 hrs used", equipmentUsageText(0, 0.0))
        assertEquals("1 game used · 1 hr used", equipmentUsageText(1, 1.0))
        assertEquals("12 games used · 12 hrs used", equipmentUsageText(12, 12.0))
        assertEquals("3 games used · 4.5 hrs used", equipmentUsageText(3, 4.5))
    }
}
