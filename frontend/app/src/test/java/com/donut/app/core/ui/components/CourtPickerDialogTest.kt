package com.donut.app.core.ui.components

import com.donut.app.core.data.model.Court
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourtPickerDialogTest {
    private val ubc = Court(id = 1, name = "UBC Tennis Centre", latitude = 49.2595, longitude = -123.2432)
    private val kits = Court(id = 2, name = "Kitsilano Beach Park", latitude = 49.2733, longitude = -123.1550)

    // Input: several courts and one already picked
    // Expected output: the map opens on the picked court
    @Test
    fun opensOnThePickedCourt() {
        assertEquals(kits, courtToOpenOn(listOf(ubc, kits), selectedCourtId = 2))
    }

    // Input: several courts and none picked
    // Expected output: null, so the map zooms out to show every court
    @Test
    fun showsEveryCourtWhenNoneIsPicked() {
        assertNull(courtToOpenOn(listOf(ubc, kits), selectedCourtId = null))
    }

    // Input: a picked court that is no longer in the list
    // Expected output: null, so the map shows every court instead of a court that is gone
    @Test
    fun showsEveryCourtWhenThePickedOneIsGone() {
        assertNull(courtToOpenOn(listOf(ubc, kits), selectedCourtId = 99))
    }

    // Input: a single court and none picked
    // Expected output: that court, since fitting one point would zoom all the way in
    @Test
    fun opensOnTheOnlyCourt() {
        assertEquals(ubc, courtToOpenOn(listOf(ubc), selectedCourtId = null))
    }
}
