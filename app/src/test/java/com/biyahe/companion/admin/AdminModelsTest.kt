package com.biyahe.companion.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminModelsTest {

    private fun hazard(type: String, lat: Double = 14.56, lon: Double = 121.0) =
        AdminHazard("rider42/abc123", "rider42", type, "Deep hole", lat, lon, 1_700_000_000_000L)

    @Test fun potholesGoToDpwhFirst() =
        assertEquals(Agency.DPWH, Agencies.forHazard(hazard("POTHOLE")).first())

    @Test fun metroManilaFloodsGoToMmda() =
        assertEquals(Agency.MMDA, Agencies.forHazard(hazard("FLOODING")).first())

    @Test fun provincialFloodsSkipMmda() {
        val outside = Agencies.forHazard(hazard("FLOODING", lat = 10.3, lon = 123.9)) // Cebu
        assertFalse(Agency.MMDA in outside)
        assertEquals(Agency.LGU_DRRMO, outside.first())
    }

    @Test fun reportNamesTheAgencyButNeverTheRider() {
        val snap = AdminSnapshot(users = listOf(AdminUser("rider42", "Juan Example", "juan@example.com")))
        val text = ReportText.hazard(hazard("POTHOLE"), Agency.DPWH, 1_700_000_100_000L)
        assertTrue(text.contains("Department of Public Works and Highways"))
        assertTrue(text.contains("maps.google.com"))
        assertFalse(text.contains(snap.userName("rider42")))
        assertFalse(text.contains("juan@example.com"))
    }

    @Test fun safetySummaryAddsUpTrips() {
        val trips = listOf(
            AdminTrip("a", 0, 10.0, 20, 80, "Smooth", 1, 2, 60, 1),
            AdminTrip("b", 0, 10.0, 20, 50, "Rough", 5, 3, 120, 4)
        )
        val text = ReportText.safetySummary(AdminSnapshot(trips = trips), Agency.LTO, 0)
        assertTrue(text.contains("Speed limit warnings: 5"))
        assertTrue(text.contains("Lane changes / swerves at driving speed: 5"))
        assertTrue(text.contains("Warnings per 100 km: 25.0"))
    }

    @Test fun pendingMeansNotYetReported() {
        val h1 = hazard("POTHOLE"); val h2 = h1.copy(key = "rider42/zzz")
        val snap = AdminSnapshot(hazards = listOf(h1, h2), reports = listOf(ReportRecord(h1.key, "DPWH", 1, "Admin")))
        assertEquals(listOf(h2), snap.pendingHazards)
    }
}
