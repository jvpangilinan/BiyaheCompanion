package com.biyahe.companion.data

import com.biyahe.companion.data.model.RouteCodec
import com.biyahe.companion.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteCodecTest {

    @Test fun oldFormatStillDecodes() {
        val pts = RouteCodec.decode("14.5,121.0;14.6,121.1")
        assertEquals(2, pts.size)
        assertNull(pts[0].roughness)
        assertEquals(0, pts[1].bumps)
    }

    @Test fun roughnessAndBumpsRoundTrip() {
        val pts = listOf(RoutePoint(14.5, 121.0), RoutePoint(14.6, 121.1, roughness = 1.75, bumps = 2))
        val back = RouteCodec.decode(RouteCodec.encode(pts))
        assertNull(back[0].roughness)
        assertEquals(1.75, back[1].roughness!!, 0.001)
        assertEquals(2, back[1].bumps)
    }
}
