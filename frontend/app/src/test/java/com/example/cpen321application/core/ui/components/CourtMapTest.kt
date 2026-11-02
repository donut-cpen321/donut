package com.example.cpen321application.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CourtMapTest {
    // Input: a court's name and its Google place ID
    // Expected output: a Google Maps link that opens that place's page, with the name encoded
    @Test
    fun linksToThePlaceInGoogleMaps() {
        val url = googleMapsUrl("UBC Tennis Centre", 49.2595521, -123.2431939, "ChIJ5bF9VMlyhlQRXEqAlInJ3c8")

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=UBC+Tennis+Centre&query_place_id=ChIJ5bF9VMlyhlQRXEqAlInJ3c8",
            url
        )
    }

    // Input: a court name with characters that would break the query string
    // Expected output: they are encoded, so the place ID stays its own parameter
    @Test
    fun encodesCharactersThatBreakTheQuery() {
        val url = googleMapsUrl("Queen Elizabeth Park Tennis & Pickleball #2", 49.2380281, -123.1127041, "ChIJ123")

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Queen+Elizabeth+Park+Tennis+%26+Pickleball+%232&query_place_id=ChIJ123",
            url
        )
    }

    // Input: a court with no Google place ID
    // Expected output: a Google Maps link to the court's coordinates, since the name alone could match another place
    @Test
    fun linksToTheCoordinatesWithoutAPlaceId() {
        val url = googleMapsUrl("Backyard court", 49.2595521, -123.2431939, null)

        assertEquals("https://www.google.com/maps/search/?api=1&query=49.2595521,-123.2431939", url)
    }
}
